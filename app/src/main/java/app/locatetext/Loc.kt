package app.locatetext

import android.location.Location
import java.util.Locale
import kotlin.math.roundToInt

/** Message format, parsing, and the offline distance/direction readout. */
object Loc {
    const val ACTION_UPDATED = "app.locatetext.UPDATED"

    /** The text one phone sends to ask the other for its location. */
    const val REQUEST = "WHERE"

    fun isRequest(body: String) = body.trim().equals(REQUEST, ignoreCase = true)

    // Plain ASCII keeps the text in a single 160-character SMS.
    private val tagged =
        Regex("""LOC\s+(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)(?:\s+acc=(\d+)m)?(?:\s+t=(\d+))?""")
    private val loose = Regex("""(-?\d{1,2}\.\d+)\s*[,;\s]\s*(-?\d{1,3}\.\d+)""")

    private fun six(v: Double) = String.format(Locale.US, "%.6f", v)

    fun format(lat: Double, lon: Double, acc: Float, timeMs: Long): String {
        val a = six(lat)
        val b = six(lon)
        return "LOC $a,$b acc=${acc.roundToInt()}m t=${timeMs / 1000} https://maps.google.com/?q=$a,$b"
    }

    private fun valid(lat: Double, lon: Double) = Math.abs(lat) <= 90 && Math.abs(lon) <= 180

    fun parse(text: String): Fix? {
        tagged.find(text)?.let { m ->
            val lat = m.groupValues[1].toDouble()
            val lon = m.groupValues[2].toDouble()
            if (valid(lat, lon)) {
                val acc = m.groupValues[3].toFloatOrNull() ?: 0f
                val t = m.groupValues[4].toLongOrNull()?.times(1000) ?: System.currentTimeMillis()
                return Fix(lat, lon, acc, t)
            }
        }
        loose.find(text)?.let { m ->
            val lat = m.groupValues[1].toDouble()
            val lon = m.groupValues[2].toDouble()
            if (valid(lat, lon)) return Fix(lat, lon, 0f, System.currentTimeMillis())
        }
        return null
    }

    fun sameNumber(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }.takeLast(7)
        val y = b.filter { it.isDigit() }.takeLast(7)
        return x.isNotEmpty() && x == y
    }

    private fun compass(bearing: Float): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[((bearing + 360f) % 360f / 45f + 0.5f).toInt() % 8]
    }

    /** Works with no map and no internet: "1.2 km NE". */
    fun describe(from: Fix, to: Fix): String {
        val r = FloatArray(2)
        Location.distanceBetween(from.lat, from.lon, to.lat, to.lon, r)
        val d = r[0]
        val dist = if (d < 1000) "${d.roundToInt()} m" else String.format(Locale.US, "%.1f km", d / 1000)
        return "$dist ${compass(r[1])}"
    }
}
