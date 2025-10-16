package com.ble1st.connectias.api

data class NetworkResponse(val statusCode: Int, val body: String, val headers: Map<String, String>)
data class PingResult(val host: String, val latencyMs: Long, val success: Boolean)
data class TracerouteHop(val hopNumber: Int, val host: String, val latencyMs: Long)

interface NetworkService {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): NetworkResponse
    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): NetworkResponse
    suspend fun ping(host: String): PingResult
    suspend fun traceroute(host: String): List<TracerouteHop>
}
