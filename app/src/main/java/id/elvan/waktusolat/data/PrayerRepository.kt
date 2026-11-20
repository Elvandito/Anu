package id.elvan.waktusolat.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

 data class PrayerTimes(
    val date: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val location: String,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    fun entries() = listOf(
        "Subuh" to fajr, "Terbit" to sunrise, "Zuhur" to dhuhr,
        "Asar" to asr, "Magrib" to maghrib, "Isya" to isha
    )
}

class PrayerRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("prayer_data", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    fun cached(): PrayerTimes? {
        val raw = prefs.getString("times", null) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            PrayerTimes(j.getString("date"), j.getString("fajr"), j.getString("sunrise"),
                j.getString("dhuhr"), j.getString("asr"), j.getString("maghrib"),
                j.getString("isha"), j.optString("location", "Lokasi tersimpan"), j.optLong("fetchedAt"))
        }.getOrNull()
    }

    fun saveLocation(lat: Double, lon: Double, label: String) {
        prefs.edit().putString("lat", lat.toString()).putString("lon", lon.toString()).putString("label", label).apply()
    }

    fun coordinates(): Pair<Double, Double> =
        (prefs.getString("lat", "-7.05")!!.toDoubleOrNull() ?: -7.05) to
        (prefs.getString("lon", "112.35")!!.toDoubleOrNull() ?: 112.35)

    fun locationLabel(): String = prefs.getString("label", "Jabung, Jawa Timur") ?: "Jabung, Jawa Timur"

    @Synchronized fun refresh(lat: Double? = null, lon: Double? = null, label: String? = null): PrayerTimes {
        val old = coordinates()
        val useLat = lat ?: old.first
        val useLon = lon ?: old.second
        val useLabel = label ?: locationLabel()
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        val url = "https://api.aladhan.com/v1/timings/$today?latitude=$useLat&longitude=$useLon&method=20"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Server jadwal solat: HTTP ${response.code}" }
            val root = JSONObject(response.body?.string() ?: error("Respons kosong"))
            check(root.optInt("code") == 200) { "Data jadwal tidak valid" }
            val data = root.getJSONObject("data")
            val t = data.getJSONObject("timings")
            fun clean(key: String) = t.getString(key).substringBefore(" ").trim()
            val date = data.getJSONObject("date").getJSONObject("gregorian").getString("date")
            val result = PrayerTimes(date, clean("Fajr"), clean("Sunrise"), clean("Dhuhr"),
                clean("Asr"), clean("Maghrib"), clean("Isha"), useLabel)
            val json = JSONObject().apply {
                put("date", result.date); put("fajr", result.fajr); put("sunrise", result.sunrise)
                put("dhuhr", result.dhuhr); put("asr", result.asr); put("maghrib", result.maghrib)
                put("isha", result.isha); put("location", result.location); put("fetchedAt", result.fetchedAt)
            }
            prefs.edit().putString("times", json.toString()).putString("lat", useLat.toString())
                .putString("lon", useLon.toString()).putString("label", useLabel).apply()
            return result
        }
    }
}
