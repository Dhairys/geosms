package app.locatetext

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {

    // Palette
    private val cBg = Color.parseColor("#EEF3F0")
    private val cCard = Color.WHITE
    private val cInk = Color.parseColor("#14201A")
    private val cMuted = Color.parseColor("#5A6B61")
    private val cLine = Color.parseColor("#D5DED8")
    private val cAccent = Color.parseColor("#0B7A5A")
    private val cSoft = Color.parseColor("#DCE5DF")
    private val cWarn = Color.parseColor("#B42318")
    private val cRx = Color.parseColor("#C2410C")

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var segShare: TextView
    private lateinit var segAsk: TextView
    private lateinit var phone: EditText
    private lateinit var interval: EditText
    private lateinit var limitIn: EditText
    private lateinit var onDemandSw: Switch
    private lateinit var callSw: Switch
    private lateinit var intervalRow: View
    private lateinit var shareCard: LinearLayout
    private lateinit var askCard: LinearLayout
    private lateinit var toggle: Button
    private lateinit var dot: View
    private lateinit var headline: TextView
    private lateinit var details: TextView
    private lateinit var budgetLabel: TextView
    private lateinit var budgetBar: ProgressBar
    private lateinit var rxBig: TextView
    private lateinit var rxSmall: TextView
    private lateinit var rxOpen: Button
    private lateinit var map: MapView
    private var meMarker: Marker? = null
    private var rxMarker: Marker? = null
    private var lastCentered = -1L

    private val updated = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refresh()
    }

    // ---------- small view helpers ----------

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun shape(fill: Int, radius: Int, strokeDp: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun tv(label: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = label
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun section(label: String) = tv(label.uppercase(Locale.US), 11.5f, cMuted, true).apply {
        letterSpacing = 0.08f
        setPadding(0, 0, 0, dp(8))
    }

    private fun hint(label: String) = tv(label, 12.5f, cMuted).apply { setPadding(0, dp(6), 0, 0) }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(cCard, 14, 1, cLine)
        setPadding(dp(16), dp(14), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

    private fun input(hintText: String, type: Int) = EditText(this).apply {
        hint = hintText
        inputType = type
        textSize = 16f
        setTextColor(cInk)
        setHintTextColor(Color.parseColor("#8FA096"))
        background = shape(Color.parseColor("#F5F8F6"), 10, 1, cLine)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isSingleLine = true
    }

    private fun styleButton(b: Button, primary: Boolean, danger: Boolean = false) {
        val tone = if (danger) cWarn else cAccent
        val base = if (primary) shape(tone, 12) else shape(Color.WHITE, 12, 2, tone)
        b.background = RippleDrawable(ColorStateList.valueOf(Color.argb(50, 0, 0, 0)), base, null)
        b.setTextColor(if (primary) Color.WHITE else tone)
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        this.text = label
        isAllCaps = false
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        minHeight = dp(52)
        minimumHeight = dp(52)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }
        styleButton(this, primary)
        setOnClickListener { onClick() }
    }

    private fun toggleRow(title: String, sub: String, sw: Switch): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(12) }
        }
        col.addView(tv(title, 15f, cInk, true))
        col.addView(tv(sub, 13f, cMuted))
        row.addView(col)
        row.addView(sw)
        return row
    }

    private fun segItem(label: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = label
        textSize = 14.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(12), dp(8), dp(12))
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        setOnClickListener { onClick() }
    }

    // ---------- screen ----------

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        Configuration.getInstance().apply {
            load(this@MainActivity, getSharedPreferences("osm", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(filesDir, "osmdroid/tiles")
        }
        window.statusBarColor = cBg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(32))
        }

        root.addView(tv("Locate and Text", 27f, cInk, true))
        root.addView(tv("Share a GPS location by text or missed call. No internet needed.", 14f, cMuted).apply {
            setPadding(0, dp(2), 0, dp(16))
        })

        // Role switch
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape(cSoft, 12)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        segShare = segItem("Share my location") { setRole("share") }
        segAsk = segItem("Ask for location") { setRole("ask") }
        seg.addView(segShare)
        seg.addView(segAsk)
        root.addView(seg)

        // Status
        val statusCard = card()
        val statusTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        dot = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(10) } }
        headline = tv("", 18f, cInk, true)
        statusTop.addView(dot)
        statusTop.addView(headline)
        statusCard.addView(statusTop)
        details = tv("", 13.5f, cMuted).apply { setPadding(0, dp(8), 0, 0) }
        statusCard.addView(details)
        root.addView(statusCard)

        // Other phone
        val pair = card()
        pair.addView(section("Other phone"))
        phone = input("+15550100199", InputType.TYPE_CLASS_PHONE).apply { setText(Store.phone(this@MainActivity)) }
        pair.addView(phone)
        pair.addView(hint("Full number with country code, entered the same way on both phones. Only this number can ask for or send a location."))
        root.addView(pair)

        // Share mode
        shareCard = card()
        shareCard.addView(section("Sharing"))
        onDemandSw = Switch(this).apply {
            isChecked = Store.onDemand(this@MainActivity)
            setOnCheckedChangeListener { _, _ -> applyRole() }
        }
        callSw = Switch(this).apply { isChecked = Store.callTrigger(this@MainActivity) }
        shareCard.addView(toggleRow("Reply only when asked", "GPS stays off until the other phone texts WHERE. Saves battery.", onDemandSw))
        shareCard.addView(toggleRow("Answer a missed call", "A short ring from the saved number counts as a request.", callSw))
        val ir = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        ir.addView(tv("Send automatically every (seconds)", 13.5f, cMuted))
        interval = input("60", InputType.TYPE_CLASS_NUMBER).apply { setText(Store.interval(this@MainActivity).toString()) }
        ir.addView(interval)
        ir.addView(hint("Timed mode sends only after you have moved 50 m or more, plus a check-in at least once an hour."))
        intervalRow = ir
        shareCard.addView(ir)
        toggle = button("Start sharing", true) { onToggle() }
        shareCard.addView(toggle)
        root.addView(shareCard)

        // Ask mode
        askCard = card()
        askCard.addView(section("Ask the other phone"))
        askCard.addView(button("Ask by text  (1 text)", true) { askNow() })
        askCard.addView(button("Ask by missed call  (no text)", false) { askByCall() })
        askCard.addView(hint("Their phone replies with its location by text, usually within a minute. It must be running Start sharing."))
        root.addView(askCard)

        // Budget
        val budget = card()
        budget.addView(section("Daily text limit"))
        budgetLabel = tv("", 15f, cInk, true)
        budget.addView(budgetLabel)
        budgetBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(8)).apply { topMargin = dp(8); bottomMargin = dp(12) }
        }
        budget.addView(budgetBar)
        limitIn = input("3000", InputType.TYPE_CLASS_NUMBER).apply { setText(Store.limit(this@MainActivity).toString()) }
        budget.addView(tv("Stop sending at (texts per day)", 13.5f, cMuted))
        budget.addView(limitIn)
        budget.addView(hint("Set this a little below your plan's daily allowance so your own texts still go through. The count restarts at midnight."))
        budget.addView(button("Reset count", false) { Store.resetCount(this); refresh() })
        root.addView(budget)

        // Map
        val mapCard = card()
        mapCard.addView(section("Map"))
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(320))
            // Let the map pan instead of the page scrolling.
            setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false }
            controller.setZoom(3.0)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(10).toFloat()) }
            }
            clipToOutline = true
        }
        mapCard.addView(map)
        mapCard.addView(hint("Green is you, orange is the received location. Map pictures need internet. Positions and distance work without it."))
        root.addView(mapCard)

        // Received
        val rxCard = card()
        rxCard.addView(section("Last location received"))
        rxBig = tv("", 26f, cRx, true)
        rxSmall = tv("", 13.5f, cMuted).apply { setPadding(0, dp(4), 0, 0) }
        rxCard.addView(rxBig)
        rxCard.addView(rxSmall)
        rxOpen = button("Open in maps app", false) { openMaps() }
        rxCard.addView(rxOpen)
        root.addView(rxCard)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(cBg)
            clipToPadding = false
            addView(root)
        })
        applyRole()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        ContextCompat.registerReceiver(
            this, updated, IntentFilter(Loc.ACTION_UPDATED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refresh()
    }

    override fun onPause() {
        saveAll()
        unregisterReceiver(updated)
        map.onPause()
        super.onPause()
    }

    // ---------- settings ----------

    private fun saveAll() {
        Store.saveSettings(
            this,
            phone.text.toString().trim(),
            interval.text.toString().toIntOrNull()?.coerceIn(10, 3600) ?: 60
        )
        Store.saveBudget(this, limitIn.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: Store.limit(this), 1)
        Store.setOnDemand(this, onDemandSw.isChecked)
        Store.setCallTrigger(this, callSw.isChecked)
    }

    private fun setRole(r: String) {
        saveAll()  // keep the limit typed for the role being left
        if (r == Store.role(this)) return
        if (r == "ask" && Store.running(this)) {
            stopService(Intent(this, TrackerService::class.java))
            Store.setRunning(this, false)
        }
        Store.setRole(this, r)
        limitIn.setText(Store.limit(this).toString())
        applyRole()
        refresh()
    }

    private fun applyRole() {
        val share = Store.role(this) == "share"
        fun seg(v: TextView, on: Boolean) {
            v.background = if (on) shape(cAccent, 10) else shape(Color.TRANSPARENT, 10)
            v.setTextColor(if (on) Color.WHITE else cMuted)
        }
        seg(segShare, share)
        seg(segAsk, !share)
        shareCard.visibility = if (share) View.VISIBLE else View.GONE
        askCard.visibility = if (share) View.GONE else View.VISIBLE
        intervalRow.visibility = if (onDemandSw.isChecked) View.GONE else View.VISIBLE
    }

    // ---------- permissions ----------

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun notificationPerm(): List<String> =
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

    /** Asks for anything missing. Returns true when everything in `must` is granted and the action can go ahead. */
    private fun ensure(code: Int, wanted: List<String>, must: List<String>, afterPrompt: Boolean): Boolean {
        val missing = wanted.filter { !granted(it) }
        if (missing.isEmpty()) return true
        if (!afterPrompt) { requestPermissions(missing.toTypedArray(), code); return false }
        return must.all { granted(it) }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        when (code) {
            1 -> start(true)
            2 -> askNow(true)
            3 -> askByCall(true)
        }
    }

    // ---------- actions ----------

    private fun problem(msg: String) {
        Store.setStatus(this, msg)
        refresh()
    }

    private fun onToggle() {
        if (Store.running(this)) {
            stopService(Intent(this, TrackerService::class.java))
            Store.setRunning(this, false)
            refresh()
        } else {
            start(false)
        }
    }

    private fun start(afterPrompt: Boolean) {
        if (phone.text.toString().trim().isEmpty()) { problem("Enter the other phone's number first."); return }
        saveAll()
        val wanted = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (callSw.isChecked) {
            wanted.add(Manifest.permission.READ_PHONE_STATE)
            wanted.add(Manifest.permission.READ_CALL_LOG)
        }
        wanted.addAll(notificationPerm())
        val must = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.SEND_SMS)
        if (!ensure(1, wanted, must, afterPrompt)) {
            if (afterPrompt) problem("Location and SMS permissions are needed to share your position.")
            return
        }
        startForegroundService(Intent(this, TrackerService::class.java))
        refresh()
    }

    /** Rings the other phone for about 7 seconds, then hangs up. Uses no text. */
    private fun askByCall(afterPrompt: Boolean = false) {
        if (phone.text.toString().trim().isEmpty()) { problem("Enter the other phone's number first."); return }
        saveAll()  // saved so their reply text is accepted
        val call = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS)
        // RECEIVE_SMS is how the reply arrives.
        val wanted = call + Manifest.permission.RECEIVE_SMS + notificationPerm()
        if (!ensure(3, wanted, call, afterPrompt)) {
            if (afterPrompt) problem("Phone permissions are needed to place the call and hang up.")
            return
        }
        val number = phone.text.toString().trim()
        val tm = getSystemService(TelecomManager::class.java)
        try {
            tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
            Store.setStatus(this, "Ringing them once and hanging up in 7 seconds. Their phone should reply by text.")
            refresh()
            Handler(Looper.getMainLooper()).postDelayed({
                try { tm.endCall() } catch (e: SecurityException) { /* permission revoked meanwhile */ }
            }, 7_000L)
        } catch (e: SecurityException) {
            problem("Call permission is missing.")
        }
    }

    /** Texts WHERE to the other phone. Costs one text from this phone's limit. */
    private fun askNow(afterPrompt: Boolean = false) {
        if (phone.text.toString().trim().isEmpty()) { problem("Enter the other phone's number first."); return }
        saveAll()  // saved so their reply is accepted
        val wanted = listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS) + notificationPerm()
        if (!ensure(2, wanted, listOf(Manifest.permission.SEND_SMS), afterPrompt)) {
            if (afterPrompt) problem("SMS permission is needed to send the request.")
            return
        }
        if (Store.sentCount(this) >= Store.limit(this)) {
            problem("Text limit reached for today. Raise it below or wait for midnight.")
            return
        }
        try {
            @Suppress("DEPRECATION")
            val sm = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            sm.sendTextMessage(phone.text.toString().trim(), null, Loc.REQUEST, null, null)
            Store.addSent(this)
            problem("Asked for their location. Waiting for a reply, usually a minute or two.")
        } catch (e: Exception) {
            problem("Could not send the request: ${e.message}")
        }
    }

    private fun openMaps() {
        val rx = Store.fix(this, "rx") ?: return
        val uri = Uri.parse("geo:${rx.lat},${rx.lon}?q=${rx.lat},${rx.lon}")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (e: Exception) { problem("No maps app found on this phone.") }
    }

    // ---------- display ----------

    private fun fmt(v: Double) = String.format(Locale.US, "%.6f", v)

    private fun ago(t: Long): String {
        val s = ((System.currentTimeMillis() - t) / 1000).coerceAtLeast(0)
        return if (s < 90) "${s}s" else if (s < 5400) "${s / 60} min" else "${s / 3600} h"
    }

    private fun setDot(color: Int) {
        dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }

    private fun refresh() {
        val running = Store.running(this)
        val share = Store.role(this) == "share"
        val me = Store.fix(this, "self")
        val rx = Store.fix(this, "rx")

        toggle.text = if (running) "Stop sharing" else "Start sharing"
        styleButton(toggle, !running, running)

        // Status
        val (title, dotColor) = when {
            !share -> "Ready to ask" to cAccent
            !running -> "Not sharing" to cMuted
            Store.onDemand(this) -> "Standing by" to cAccent
            else -> "Sharing live" to cAccent
        }
        headline.text = title
        setDot(dotColor)
        val sb = StringBuilder()
        Store.status(this).takeIf { it.isNotEmpty() }?.let { sb.append(it) }
        if (share && me != null) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Your last GPS fix: ${ago(me.t)} ago, within ${me.acc.roundToInt()} m")
        }
        if (sb.isEmpty()) {
            sb.append(if (share) "Tap Start sharing when you are ready." else "Enter the number above, then ask.")
        }
        details.text = sb

        // Budget
        val used = Store.sentCount(this)
        val limit = Store.limit(this)
        budgetLabel.text = "$used of $limit texts used today"
        budgetBar.progress = (used * 100L / limit.coerceAtLeast(1)).toInt().coerceAtMost(100)
        budgetBar.progressTintList = ColorStateList.valueOf(if (used * 10L >= limit * 9L) cWarn else cAccent)

        // Received
        if (rx == null) {
            rxBig.text = "Nothing yet"
            rxSmall.text = "Location texts from the number above appear here and on the map."
            rxOpen.visibility = View.GONE
        } else {
            rxBig.text = if (me != null) Loc.describe(me, rx) + " away" else "Location received"
            rxSmall.text = "From ${Store.rxFrom(this)}\n${fmt(rx.lat)}, ${fmt(rx.lon)}  ·  fix ${ago(rx.t)} old"
            rxOpen.visibility = View.VISIBLE
        }

        updateMap(me, rx)
    }

    private fun pin(old: Marker?, p: GeoPoint, title: String, color: Int): Marker {
        val m = old ?: Marker(map).also { map.overlays.add(it) }
        m.position = p
        m.title = title
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        m.icon = m.icon.mutate().also { it.setTint(color) }
        return m
    }

    private fun updateMap(me: Fix?, rx: Fix?) {
        val pts = mutableListOf<GeoPoint>()
        if (me != null) {
            val p = GeoPoint(me.lat, me.lon)
            meMarker = pin(meMarker, p, "You", cAccent); pts.add(p)
        }
        if (rx != null) {
            val p = GeoPoint(rx.lat, rx.lon)
            rxMarker = pin(rxMarker, p, "Received", cRx); pts.add(p)
        }
        map.invalidate()

        // Re-center only when a new received location arrives, so panning is not undone.
        val key = rx?.t ?: 0L
        if (pts.isNotEmpty() && key != lastCentered) {
            lastCentered = key
            map.post {
                if (pts.size == 1) {
                    map.controller.setZoom(16.0); map.controller.setCenter(pts[0])
                } else {
                    map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), true, dp(60))
                }
            }
        }
    }
}
