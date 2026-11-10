package app.locatetext

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Fix(val lat: Double, val lon: Double, val acc: Float, val t: Long)

/** Small SharedPreferences wrapper shared by the service, receiver and screen. */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("lat", Context.MODE_PRIVATE)

    fun phone(c: Context): String = p(c).getString("phone", "") ?: ""

    /** Seconds between texts in timed mode. */
    fun interval(c: Context): Int = p(c).getInt("intervalSec", 60)

    fun saveSettings(c: Context, phone: String, seconds: Int) {
        p(c).edit().putString("phone", phone).putInt("intervalSec", seconds).apply()
    }

    /** "share" = this phone sends its location. "ask" = this phone requests the other's location. */
    fun role(c: Context): String = p(c).getString("role", "share") ?: "share"
    fun setRole(c: Context, r: String) { p(c).edit().putString("role", r).apply() }

    /** Daily text limit for the current role: 3000 when sharing (plan 3600), 50 when asking (plan 100). */
    fun limit(c: Context): Int =
        p(c).getInt("limit_" + role(c), if (role(c) == "share") 3000 else 50)

    fun periodDays(c: Context): Int = p(c).getInt("periodDays", 1)

    fun saveBudget(c: Context, limit: Int, days: Int) {
        p(c).edit().putInt("limit_" + role(c), limit).putInt("periodDays", days).apply()
    }

    /** Texts sent in the current period. Starts a fresh period once the old one has run out. */
    fun sentCount(c: Context): Int {
        val s = p(c)
        val now = System.currentTimeMillis()
        val start = s.getLong("periodStart", 0L)
        // Calendar days in the phone's time zone, so a 1 day period resets at midnight.
        val zone = ZoneId.systemDefault()
        val firstDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val expired = LocalDate.now(zone).isAfter(firstDay.plusDays(periodDays(c) - 1L))
        if (start == 0L || expired) {
            s.edit().putLong("periodStart", now).putInt("count", 0).apply()
            return 0
        }
        return s.getInt("count", 0)
    }

    fun addSent(c: Context) {
        val n = sentCount(c) + 1
        p(c).edit().putInt("count", n).apply()
    }

    fun resetCount(c: Context) {
        p(c).edit().putLong("periodStart", System.currentTimeMillis()).putInt("count", 0).apply()
    }

    /** On demand: GPS stays off and the phone replies only when asked. */
    fun onDemand(c: Context): Boolean = p(c).getBoolean("onDemand", true)
    fun setOnDemand(c: Context, v: Boolean) { p(c).edit().putBoolean("onDemand", v).apply() }

    /** Also treat a short unanswered call from the paired number as a request. */
    fun callTrigger(c: Context): Boolean = p(c).getBoolean("callTrigger", true)
    fun setCallTrigger(c: Context, v: Boolean) { p(c).edit().putBoolean("callTrigger", v).apply() }

    fun running(c: Context): Boolean = p(c).getBoolean("running", false)
    fun setRunning(c: Context, v: Boolean) { p(c).edit().putBoolean("running", v).apply() }

    fun status(c: Context): String = p(c).getString("status", "") ?: ""
    fun setStatus(c: Context, s: String) { p(c).edit().putString("status", s).apply() }

    fun rxFrom(c: Context): String = p(c).getString("rx.from", "") ?: ""

    /** key is "self" (own latest fix), "sent" (last fix texted) or "rx" (last received). */
    fun saveFix(c: Context, key: String, f: Fix, from: String? = null) {
        val e = p(c).edit()
            .putLong("$key.lat", f.lat.toRawBits())
            .putLong("$key.lon", f.lon.toRawBits())
            .putFloat("$key.acc", f.acc)
            .putLong("$key.t", f.t)
        if (from != null) e.putString("$key.from", from)
        e.apply()
    }

    fun fix(c: Context, key: String): Fix? {
        val s = p(c)
        if (!s.contains("$key.t")) return null
        return Fix(
            Double.fromBits(s.getLong("$key.lat", 0)),
            Double.fromBits(s.getLong("$key.lon", 0)),
            s.getFloat("$key.acc", 0f),
            s.getLong("$key.t", 0)
        )
    }
}
