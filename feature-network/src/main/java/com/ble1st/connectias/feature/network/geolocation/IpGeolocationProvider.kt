package com.ble1st.connectias.feature.network.geolocation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for IP geolocation lookup functionality.
 */
@Singleton
class IpGeolocationProvider @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Looks up geolocation data for an IP address.
     */
    suspend fun lookupIp(ip: String): GeolocationResult = withContext(Dispatchers.IO) {
        try {
            // Use ip-api.com (free tier, no API key required)
            val url = "http://ip-api.com/json/$ip?fields=status,message,continent,continentCode,country,countryCode,region,regionName,city,district,zip,lat,lon,timezone,offset,currency,isp,org,as,asname,mobile,proxy,hosting,query"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")

            val apiResponse = json.decodeFromString<IpApiResponse>(body)
            
            if (apiResponse.status == "fail") {
                throw Exception(apiResponse.message ?: "Lookup failed")
            }

            GeolocationResult(
                ip = apiResponse.query ?: ip,
                success = true,
                country = apiResponse.country,
                countryCode = apiResponse.countryCode,
                region = apiResponse.regionName,
                regionCode = apiResponse.region,
                city = apiResponse.city,
                zipCode = apiResponse.zip,
                latitude = apiResponse.lat,
                longitude = apiResponse.lon,
                timezone = apiResponse.timezone,
                isp = apiResponse.isp,
                organization = apiResponse.org,
                asn = apiResponse.asNumber,
                asnName = apiResponse.asname,
                isProxy = apiResponse.proxy,
                isHosting = apiResponse.hosting,
                isMobile = apiResponse.mobile,
                continent = apiResponse.continent,
                continentCode = apiResponse.continentCode,
                currency = apiResponse.currency
            )
        } catch (e: Exception) {
            Timber.e(e, "Geolocation lookup failed for $ip")
            GeolocationResult(
                ip = ip,
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Gets geolocation for current device.
     */
    suspend fun lookupCurrentIp(): GeolocationResult = withContext(Dispatchers.IO) {
        try {
            // First get public IP
            val ipRequest = Request.Builder()
                .url("https://api.ipify.org")
                .get()
                .build()

            val ipResponse = httpClient.newCall(ipRequest).execute()
            val publicIp = ipResponse.body?.string() ?: throw Exception("Failed to get public IP")

            lookupIp(publicIp)
        } catch (e: Exception) {
            Timber.e(e, "Failed to lookup current IP")
            GeolocationResult(
                ip = "unknown",
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Batch lookup multiple IPs.
     */
    suspend fun lookupBatch(ips: List<String>): List<GeolocationResult> = withContext(Dispatchers.IO) {
        ips.map { ip -> lookupIp(ip) }
    }

    /**
     * Calculates distance between two coordinates.
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }
}

/**
 * Response from ip-api.com.
 */
@Serializable
private data class IpApiResponse(
    val status: String? = null,
    val message: String? = null,
    val continent: String? = null,
    val continentCode: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val regionName: String? = null,
    val city: String? = null,
    val district: String? = null,
    val zip: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val timezone: String? = null,
    val offset: Int? = null,
    val currency: String? = null,
    val isp: String? = null,
    val org: String? = null,
    @SerialName("as")
    val asNumber: String? = null,
    val asname: String? = null,
    val mobile: Boolean? = null,
    val proxy: Boolean? = null,
    val hosting: Boolean? = null,
    val query: String? = null
)

/**
 * Geolocation lookup result.
 */
@Serializable
data class GeolocationResult(
    val ip: String,
    val success: Boolean,
    val error: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val regionCode: String? = null,
    val city: String? = null,
    val zipCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val isp: String? = null,
    val organization: String? = null,
    val asn: String? = null,
    val asnName: String? = null,
    val isProxy: Boolean? = null,
    val isHosting: Boolean? = null,
    val isMobile: Boolean? = null,
    val continent: String? = null,
    val continentCode: String? = null,
    val currency: String? = null
) {
    val formattedLocation: String
        get() = listOfNotNull(city, region, country).joinToString(", ")

    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null
}
