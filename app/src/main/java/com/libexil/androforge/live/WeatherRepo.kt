package com.libexil.androforge.live

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.libexil.androforge.BuildConfig
import com.libexil.androforge.data.TempUnits
import com.libexil.androforge.data.WeatherPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

data class DayForecast(val label: String, val sky: Sky, val max: Int, val min: Int)

data class Weather(
    val place: String,
    val temp: Int,
    val feels: Int,
    val sky: Sky,
    val condition: String,
    val isDay: Boolean,
    val humidity: Int,
    val wind: Int,
    val high: Int,
    val low: Int,
    val days: List<DayForecast>,
    val units: TempUnits,
    val fetchedAt: Long,
)

sealed interface WeatherState {
    data object Loading : WeatherState
    data object NeedsLocation : WeatherState
    data class Ready(val weather: Weather) : WeatherState
    data class Failed(val message: String) : WeatherState
}

/** Weather from Open-Meteo (free, no API key). Cached per place for 20 minutes. */
object WeatherRepo {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = HashMap<String, MutableStateFlow<WeatherState>>()
    private val locks = HashMap<String, Mutex>()
    private const val MAX_AGE = 20 * 60 * 1000L

    fun defaultUnits(): TempUnits =
        if (Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")) TempUnits.US else TempUnits.METRIC

    fun key(place: WeatherPlace?, units: TempUnits) = "${place?.lat ?: "here"},${place?.lon ?: ""},$units"

    @Synchronized
    fun flow(key: String): StateFlow<WeatherState> = cache.getOrPut(key) { MutableStateFlow(WeatherState.Loading) }.asStateFlow()

    @Synchronized
    private fun mutable(key: String) = cache.getOrPut(key) { MutableStateFlow(WeatherState.Loading) }

    @Synchronized
    private fun lock(key: String) = locks.getOrPut(key) { Mutex() }

    fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(context: Context, place: WeatherPlace?, units: TempUnits, force: Boolean = false) {
        val key = key(place, units)
        val flow = mutable(key)
        lock(key).withLock {
            val current = flow.value
            if (!force && current is WeatherState.Ready && System.currentTimeMillis() - current.weather.fetchedAt < MAX_AGE) return
            val target = place ?: deviceLocation(context) ?: run {
                if (current !is WeatherState.Ready) flow.value = WeatherState.NeedsLocation
                return
            }
            flow.value = try {
                WeatherState.Ready(fetch(target, units))
            } catch (e: Exception) {
                if (current is WeatherState.Ready) current else WeatherState.Failed(e.message ?: "No connection")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun deviceLocation(context: Context): WeatherPlace? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null
        val lm = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val best: Location? = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        val loc = best ?: return@withContext null
        val name = try {
            @Suppress("DEPRECATION")
            if (Geocoder.isPresent()) {
                Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                    ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
        WeatherPlace(name ?: "Here", loc.latitude, loc.longitude)
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "AndroForge/${BuildConfig.VERSION_NAME}")
        try {
            if (c.responseCode != 200) error("Weather service answered ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private suspend fun fetch(place: WeatherPlace, units: TempUnits): Weather = withContext(Dispatchers.IO) {
        val u = if (units == TempUnits.US) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else "&wind_speed_unit=kmh"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7$u"
        val root = json.parseToJsonElement(get(url)).jsonObject
        val cur = root["current"]!!.jsonObject
        val daily = root["daily"]!!.jsonObject
        fun arr(o: JsonObject, k: String) = o[k]!!.jsonArray
        val times = arr(daily, "time")
        val codes = arr(daily, "weather_code")
        val maxes = arr(daily, "temperature_2m_max")
        val mins = arr(daily, "temperature_2m_min")
        val days = times.indices.map { i ->
            val date = LocalDate.parse(times[i].jsonPrimitive.content)
            val label = if (i == 0) "Today" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            DayForecast(
                label,
                sky(codes[i].jsonPrimitive.int),
                maxes[i].jsonPrimitive.double.let(Math::round).toInt(),
                mins[i].jsonPrimitive.double.let(Math::round).toInt(),
            )
        }
        val code = cur["weather_code"]!!.jsonPrimitive.int
        Weather(
            place = place.name,
            temp = Math.round(cur["temperature_2m"]!!.jsonPrimitive.double).toInt(),
            feels = Math.round(cur["apparent_temperature"]!!.jsonPrimitive.double).toInt(),
            sky = sky(code),
            condition = describe(code),
            isDay = cur["is_day"]?.jsonPrimitive?.int != 0,
            humidity = cur["relative_humidity_2m"]?.jsonPrimitive?.double?.toInt() ?: 0,
            wind = cur["wind_speed_10m"]?.jsonPrimitive?.double?.let(Math::round)?.toInt() ?: 0,
            high = days.firstOrNull()?.max ?: 0,
            low = days.firstOrNull()?.min ?: 0,
            days = days,
            units = units,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** City search for the widget editor. */
    suspend fun search(query: String): List<WeatherPlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val lang = Locale.getDefault().language
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=$lang&name=" +
            URLEncoder.encode(query.trim(), "UTF-8")
        val root = json.parseToJsonElement(get(url)).jsonObject
        val results = root["results"]?.jsonArray ?: return@withContext emptyList()
        results.map { el ->
            val o = el.jsonObject
            val name = o["name"]!!.jsonPrimitive.content
            val admin = o["admin1"]?.jsonPrimitive?.content
            val country = o["country_code"]?.jsonPrimitive?.content
            WeatherPlace(
                listOfNotNull(name, admin, country).distinct().joinToString(", "),
                o["latitude"]!!.jsonPrimitive.double,
                o["longitude"]!!.jsonPrimitive.double,
            )
        }
    }

    fun sky(code: Int): Sky = when (code) {
        0, 1 -> Sky.CLEAR
        2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57 -> Sky.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81 -> "Showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Storm with hail"
        else -> "—"
    }
}
