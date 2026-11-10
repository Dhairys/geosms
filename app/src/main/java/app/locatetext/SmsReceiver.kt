package app.locatetext

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Picks up location texts from the paired number, even when the app is closed. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(i)
        val from = parts.firstOrNull()?.originatingAddress ?: return
        // Ignore texts from anyone except the number set in the app.
        if (!Loc.sameNumber(from, Store.phone(c))) return
        val body = parts.joinToString("") { it.messageBody ?: "" }

        if (Loc.isRequest(body)) {
            // Answer only while sharing is switched on in the app.
            if (Store.running(c)) {
                try {
                    c.startService(Intent(c, TrackerService::class.java).setAction(TrackerService.ACTION_REQUEST))
                } catch (e: Exception) {
                    // Android refused to start the service from the background; nothing more to do.
                }
            }
            return
        }
        if (body.trim().startsWith("NOFIX", ignoreCase = true)) {
            Notify.received(c, "$from could not get a GPS fix yet. Ask again in a few minutes.")
            return
        }
        val fix = Loc.parse(body) ?: return

        Store.saveFix(c, "rx", fix, from)
        c.sendBroadcast(Intent(Loc.ACTION_UPDATED).setPackage(c.packageName))

        val me = Store.fix(c, "self")
        val where = if (me != null) Loc.describe(me, fix) + " from you" else "Open the app to see it"
        Notify.received(c, "$from: $where")
    }
}
