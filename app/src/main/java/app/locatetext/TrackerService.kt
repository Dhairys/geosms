package app.locatetext

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

/**
 * Foreground service that texts your position to the paired number.
 *
 * Two modes (set in the app):
 *  - On demand: GPS stays off. When the paired number texts WHERE, the service gets a fix and replies.
 *  - Timed: GPS runs and a text goes out every N minutes, only if you moved.
 *
 * GPS needs no internet. Texts need cell signal only.
 */
class TrackerService : Service(), LocationListener {

    companion object {
        const val ACTION_REQUEST = "app.locatetext.REQUEST"
        private const val FIX_WAIT_MS = 60_000L
        private const val FRESH_MS = 120_000L
    }

    private lateinit var lm: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var best: Location? = null
    private var lastSentAt = 0L
    private var requestPending = false
    private val giveUp = Runnable { answer(null) }

    // ---- missed-call requests ----
    // A call from the paired number that rings for under 12 seconds and is never answered counts
    // as a request. A real call (answered, or ringing longer) is ignored.
    private var ringFrom: String? = null
    private var ringAt = 0L
    private var phoneRegistered = false
    private var callNote = ""

    private val phoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    @Suppress("DEPRECATION")
                    val num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    // Only the number saved in the app counts. Anyone else's call is ignored.
                    if (num != null && ringFrom == null && Loc.sameNumber(num, Store.phone(c))) {
                        ringFrom = num
                        ringAt = SystemClock.elapsedRealtime()
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> ringFrom = null
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    val from = ringFrom
                    ringFrom = null
                    if (from != null && SystemClock.elapsedRealtime() - ringAt < 12_000L) startRequest()
                }
            }
        }
    }

    private fun registerPhone() {
        if (phoneRegistered) return
        val ok = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        if (!ok) {
            callNote = " Missed-call requests are off: allow Phone and Call logs for this app."
            return
        }
        ContextCompat.registerReceiver(
            this, phoneReceiver,
            IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        phoneRegistered = true
    }

    private fun unregisterPhone() {
        if (!phoneRegistered) return
        try { unregisterReceiver(phoneReceiver) } catch (e: Exception) { /* already gone */ }
        phoneRegistered = false
        ringFrom = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val onDemand = Store.onDemand(this)
        val note = if (onDemand) "Standing by. Replies with your location when asked."
        else "Texting your location every ${Store.interval(this)} s"
        val n = Notify.ongoing(this, note)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, n)
        }
        lm = getSystemService(LocationManager::class.java)

        if (intent?.action == ACTION_REQUEST) {
            if (Store.running(this)) startRequest()
            return START_STICKY
        }

        // Normal start (or restart after the system killed the service).
        lm.removeUpdates(this)
        handler.removeCallbacks(giveUp)
        requestPending = false
        unregisterPhone()
        callNote = ""
        if (Store.callTrigger(this)) registerPhone()
        if (onDemand) {
            Store.setStatus(
                this,
                "Standing by. GPS is off until the other phone texts WHERE" +
                    (if (phoneRegistered) " or gives a missed call" else "") + "." + callNote
            )
        } else {
            val gps = register(10_000L, 30_000L) ?: run { stopSelf(); return START_NOT_STICKY }
            Store.setStatus(this, if (gps) "Waiting for a GPS fix…" else "Turn on location in your phone settings.")
        }
        Store.setRunning(this, true)
        lastSentAt = 0L
        broadcast()
        return START_STICKY
    }

    /** Starts GPS (and cell/Wi-Fi as a fallback). Returns whether GPS is on, or null if permission is missing. */
    private fun register(gpsEveryMs: Long, netEveryMs: Long): Boolean? {
        return try {
            val gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (gps) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, gpsEveryMs, 0f, this, Looper.getMainLooper())
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, netEveryMs, 0f, this, Looper.getMainLooper())
            }
            gps
        } catch (e: SecurityException) {
            Store.setStatus(this, "Location permission is missing.")
            null
        }
    }

    // ---- someone asked for our location ----

    private fun startRequest() {
        if (requestPending) return
        requestPending = true
        val fresh = best?.takeIf { System.currentTimeMillis() - it.time < FRESH_MS }
        if (fresh != null) { answer(fresh); return }

        Store.setStatus(this, "Your location was requested. Looking for a GPS fix…")
        broadcast()
        if (register(2_000L, 5_000L) == null) { requestPending = false; return }
        handler.postDelayed(giveUp, FIX_WAIT_MS)
    }

    private fun lastKnown(): Location? = try {
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (e: SecurityException) { null }

    private fun answer(l: Location?) {
        handler.removeCallbacks(giveUp)
        requestPending = false
        if (Store.onDemand(this)) lm.removeUpdates(this)  // back to GPS off

        val fix = l ?: best ?: lastKnown()
        if (fix != null) {
            sendFix(fix, asked = true)
        } else if (sendText("NOFIX")) {
            Store.setStatus(this, "No GPS fix yet. Told them so.")
        }
        broadcast()
    }

    // ---- GPS updates ----

    override fun onLocationChanged(l: Location) {
        val cur = best
        val take = cur == null ||
            l.provider == LocationManager.GPS_PROVIDER ||
            (cur.provider != LocationManager.GPS_PROVIDER && l.accuracy < cur.accuracy) ||
            l.time - cur.time > 120_000
        if (!take) return
        best = l
        Store.saveFix(this, "self", Fix(l.latitude, l.longitude, l.accuracy, l.time))

        if (requestPending) {
            answer(l)
            return
        }
        if (Store.onDemand(this)) { broadcast(); return }

        // Timed mode. After the interval, send only if you moved 50 m or more since the last text,
        // or at least once an hour (or every interval, if longer) as a "still here" ping.
        val elapsed = System.currentTimeMillis() - lastSentAt
        val intervalMs = Store.interval(this) * 1000L
        val last = Store.fix(this, "sent")
        var moved = true
        if (last != null) {
            val r = FloatArray(1)
            Location.distanceBetween(last.lat, last.lon, l.latitude, l.longitude, r)
            moved = r[0] >= 50f
        }
        val due = elapsed >= intervalMs - 5_000L && (moved || elapsed >= maxOf(intervalMs, 3_600_000L))
        if (due) sendFix(l, asked = false)
        broadcast()
    }

    // ---- sending ----

    /** Every text goes through here so the limit is always checked and counted. */
    private fun sendText(text: String): Boolean {
        val to = Store.phone(this)
        if (to.isBlank()) return false
        if (Store.sentCount(this) >= Store.limit(this)) {
            Store.setStatus(this, "Text limit reached (${Store.limit(this)}). Tap Reset count, or wait for the period to end.")
            return false
        }
        return try {
            @Suppress("DEPRECATION")
            val sm = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            sm.sendMultipartTextMessage(to, null, sm.divideMessage(text), null, null)
            Store.addSent(this)
            true
        } catch (e: Exception) {
            Store.setStatus(this, "Could not send the text: ${e.message}")
            false
        }
    }

    private fun sendFix(l: Location, asked: Boolean) {
        val ok = sendText(Loc.format(l.latitude, l.longitude, l.accuracy, l.time))
        if (ok) {
            lastSentAt = System.currentTimeMillis()
            Store.saveFix(this, "sent", Fix(l.latitude, l.longitude, l.accuracy, l.time))
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            Store.setStatus(this, "Last text sent at $time" + if (asked) " (they asked)" else "")
            // Always tell the person being located when their position was shared on request.
            if (asked) Notify.shared(this, "Sent your location to ${Store.phone(this)} at $time because they asked.")
        } else {
            // Try again in about a minute instead of on every GPS update.
            lastSentAt = System.currentTimeMillis() - Store.interval(this) * 1000L + 60_000L
        }
    }

    private fun broadcast() {
        sendBroadcast(Intent(Loc.ACTION_UPDATED).setPackage(packageName))
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        unregisterPhone()
        try { lm.removeUpdates(this) } catch (e: Exception) { /* nothing to remove */ }
        Store.setRunning(this, false)
        broadcast()
        super.onDestroy()
    }

    // Required explicitly: older Android versions call these without default implementations.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
