package com.memorylink.data.remote

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * API client for sunrise-sunset.org to get solar times.
 *
 * Uses IP-based geolocation (simplest approach, no permissions needed). The API automatically
 * detects location from the request IP.
 *
 * Per .clinerules/20-android.md:
 * - Source: Use sunrise-sunset.org API (free, no key)
 * - Fallback: If location unavailable, use static times from config
 * - Cache: Store sunrise/sunset times daily; recalculate at midnight
 *
 * API documentation: https://sunrise-sunset.org/api
 */
@Singleton
class SunriseSunsetApi @Inject constructor() {

    companion object {
        private const val TAG = "SunriseSunsetApi"

        // API endpoint - uses IP geolocation when lat/lng not provided
        // We use a fixed location (Toronto) as fallback since IP geolocation
        // can be unreliable. Users can override via [CONFIG] events.
        private const val API_BASE_URL = "https://api.sunrise-sunset.org/json"

        // Default coordinates (Toronto, Canada) - reasonable North American default
        private const val DEFAULT_LAT = 43.6532
        private const val DEFAULT_LNG = -79.3832

        // Timeout for HTTP requests
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000

        // Time format from API response (ISO 8601)
        private val API_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm:ss a")
    }

    /** Cached solar times for today. Invalidated at midnight or when date changes. */
    private var cachedData: SolarData? = null
    private var cachedDate: LocalDate? = null

    /** Result of fetching solar times. */
    sealed class SolarResult {
        data class Success(val data: SolarData) : SolarResult()
        data class Error(val message: String) : SolarResult()
    }

    /** Solar times for a given day. */
    data class SolarData(val sunrise: LocalTime, val sunset: LocalTime, val date: LocalDate)

    /**
     * Get sunrise time for today.
     *
     * @param fallback Time to return if API fails
     * @return Sunrise time or fallback
     */
    suspend fun getSunrise(fallback: LocalTime = LocalTime.of(6, 0)): LocalTime {
        return getSolarData()?.sunrise ?: fallback
    }

    /**
     * Get sunset time for today.
     *
     * @param fallback Time to return if API fails
     * @return Sunset time or fallback
     */
    suspend fun getSunset(fallback: LocalTime = LocalTime.of(21, 0)): LocalTime {
        return getSolarData()?.sunset ?: fallback
    }

    /**
     * Get solar data for today, using cache if available.
     *
     * @return SolarData or null if unavailable
     */
    suspend fun getSolarData(): SolarData? {
        val today = LocalDate.now()

        // Return cached data if valid for today
        cachedData?.let { cached ->
            if (cachedDate == today) {
                Log.d(TAG, "Using cached solar data for $today")
                return cached
            }
        }

        // Fetch fresh data
        return when (val result = fetchSolarTimes(today)) {
            is SolarResult.Success -> {
                cachedData = result.data
                cachedDate = today
                Log.d(
                        TAG,
                        "Fetched solar times: sunrise=${result.data.sunrise}, sunset=${result.data.sunset}"
                )
                result.data
            }
            is SolarResult.Error -> {
                Log.w(TAG, "Failed to fetch solar times: ${result.message}")
                null
            }
        }
    }

    /**
     * Force refresh of solar data (ignores cache).
     *
     * @return SolarResult indicating success or failure
     */
    suspend fun refresh(): SolarResult {
        val today = LocalDate.now()
        val result = fetchSolarTimes(today)

        if (result is SolarResult.Success) {
            cachedData = result.data
            cachedDate = today
        }

        return result
    }

    /** Clear cached data. Call this at midnight to force refresh. */
    fun clearCache() {
        cachedData = null
        cachedDate = null
        Log.d(TAG, "Solar cache cleared")
    }

    /**
     * Fetch solar times from the API.
     *
     * @param date Date to fetch solar times for
     * @param lat Latitude (optional, uses default if not provided)
     * @param lng Longitude (optional, uses default if not provided)
     * @return SolarResult with times or error
     */
    suspend fun fetchSolarTimes(
            date: LocalDate,
            lat: Double = DEFAULT_LAT,
            lng: Double = DEFAULT_LNG
    ): SolarResult =
            withContext(Dispatchers.IO) {
                try {
                    val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    // Include timezone ID so API returns local times (not UTC)
                    val tzid = java.util.TimeZone.getDefault().id
                    val urlString = "$API_BASE_URL?lat=$lat&lng=$lng&date=$dateStr&formatted=1&tzid=$tzid"

                    Log.d(TAG, "Fetching solar times from: $urlString")

                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection

                    connection.apply {
                        requestMethod = "GET"
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        setRequestProperty("Accept", "application/json")
                    }

                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        return@withContext SolarResult.Error("HTTP error: $responseCode")
                    }

                    val response =
                            BufferedReader(InputStreamReader(connection.inputStream)).use { reader
                                ->
                                reader.readText()
                            }

                    connection.disconnect()

                    // Parse JSON response
                    val json = JSONObject(response)
                    val status = json.getString("status")

                    if (status != "OK") {
                        return@withContext SolarResult.Error("API status: $status")
                    }

                    val results = json.getJSONObject("results")
                    val sunriseStr = results.getString("sunrise")
                    val sunsetStr = results.getString("sunset")

                    // Parse times (API returns in format "7:30:00 AM")
                    val sunrise = parseApiTime(sunriseStr)
                    val sunset = parseApiTime(sunsetStr)

                    if (sunrise == null || sunset == null) {
                        return@withContext SolarResult.Error(
                                "Failed to parse times: sunrise=$sunriseStr, sunset=$sunsetStr"
                        )
                    }

                    SolarResult.Success(SolarData(sunrise, sunset, date))
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching solar times", e)
                    SolarResult.Error("Network error: ${e.message}")
                }
            }

    /**
     * Parse time string from API response.
     *
     * @param timeStr Time string like "7:30:00 AM"
     * @return LocalTime or null if parsing fails
     */
    private fun parseApiTime(timeStr: String): LocalTime? {
        return try {
            // API returns times like "7:30:00 AM" - need to handle variable formats
            // Trim whitespace and normalize to uppercase for AM/PM parsing
            val normalized = timeStr.trim().uppercase()
            Log.d(TAG, "Parsing API time: '$timeStr' -> normalized: '$normalized'")
            LocalTime.parse(normalized, API_TIME_FORMATTER)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse time with formatter: $timeStr, trying manual parse", e)
            // Fallback: manual parsing for edge cases
            try {
                parseTimeManually(timeStr.trim())
            } catch (e2: Exception) {
                Log.e(TAG, "Manual parse also failed for: $timeStr", e2)
                null
            }
        }
    }

    /**
     * Manual time parsing fallback for edge cases. Handles formats like "7:30:00 AM", "12:00:00
     * PM", etc.
     */
    private fun parseTimeManually(timeStr: String): LocalTime? {
        val parts = timeStr.uppercase().split(" ")
        if (parts.size != 2) return null

        val timePart = parts[0]
        val amPm = parts[1]

        val timeComponents = timePart.split(":")
        if (timeComponents.size < 2) return null

        var hour = timeComponents[0].toInt()
        val minute = timeComponents[1].toInt()
        val second = if (timeComponents.size > 2) timeComponents[2].toInt() else 0

        // Convert to 24-hour format
        when {
            amPm == "AM" && hour == 12 -> hour = 0
            amPm == "PM" && hour != 12 -> hour += 12
        }

        return LocalTime.of(hour, minute, second)
    }
}
