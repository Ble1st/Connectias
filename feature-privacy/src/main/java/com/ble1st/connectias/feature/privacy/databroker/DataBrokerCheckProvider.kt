package com.ble1st.connectias.feature.privacy.databroker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for data broker check functionality.
 *
 * Features:
 * - Have I Been Pwned integration
 * - Email breach checking
 * - Password breach checking
 * - Data broker exposure monitoring
 */
@Singleton
class DataBrokerCheckProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _checkHistory = MutableStateFlow<List<BreachCheck>>(emptyList())
    val checkHistory: StateFlow<List<BreachCheck>> = _checkHistory.asStateFlow()

    private val _monitoredEmails = MutableStateFlow<List<MonitoredEmail>>(emptyList())
    val monitoredEmails: StateFlow<List<MonitoredEmail>> = _monitoredEmails.asStateFlow()

    companion object {
        private const val HIBP_API_URL = "https://haveibeenpwned.com/api/v3"
        private const val HIBP_PASSWORD_API = "https://api.pwnedpasswords.com"
        // Note: HIBP requires API key for breach checks
        // This implementation uses the free password check API
    }

    /**
     * Checks if a password has been exposed in breaches.
     * Uses k-anonymity (only first 5 chars of hash sent).
     */
    suspend fun checkPassword(password: String): PasswordCheckResult = withContext(Dispatchers.IO) {
        try {
            // Hash password with SHA-1
            val sha1Hash = hashSha1(password).uppercase()
            val prefix = sha1Hash.take(5)
            val suffix = sha1Hash.drop(5)

            // Query HIBP API
            val request = Request.Builder()
                .url("$HIBP_PASSWORD_API/range/$prefix")
                .header("Add-Padding", "true")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            // Parse response
            val breachCount = body.lines()
                .mapNotNull { line ->
                    val parts = line.split(":")
                    if (parts.size == 2 && parts[0].equals(suffix, ignoreCase = true)) {
                        parts[1].trim().toIntOrNull()
                    } else null
                }
                .firstOrNull() ?: 0

            if (breachCount > 0) {
                PasswordCheckResult.Pwned(
                    exposureCount = breachCount,
                    recommendation = "This password has been exposed $breachCount times. Change it immediately."
                )
            } else {
                PasswordCheckResult.Safe
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking password")
            PasswordCheckResult.Error("Check failed: ${e.message}", e)
        }
    }

    /**
     * Checks email against known breaches.
     * Note: HIBP API requires API key for email checks.
     */
    suspend fun checkEmail(
        email: String,
        apiKey: String? = null
    ): EmailCheckResult = withContext(Dispatchers.IO) {
        if (apiKey == null) {
            return@withContext EmailCheckResult.ApiKeyRequired
        }

        try {
            val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
            val request = Request.Builder()
                .url("$HIBP_API_URL/breachedaccount/$encodedEmail?truncateResponse=false")
                .header("hibp-api-key", apiKey)
                .header("User-Agent", "Connectias-Android")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: "[]"
                    val breaches = parseBreaches(body)
                    
                    val check = BreachCheck(
                        email = email,
                        breachCount = breaches.size,
                        breaches = breaches,
                        checkedAt = System.currentTimeMillis()
                    )
                    _checkHistory.update { it + check }

                    EmailCheckResult.Breached(breaches)
                }
                404 -> EmailCheckResult.Clean
                401 -> EmailCheckResult.ApiKeyRequired
                429 -> EmailCheckResult.RateLimited
                else -> EmailCheckResult.Error("API returned ${response.code}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking email")
            EmailCheckResult.Error("Check failed: ${e.message}")
        }
    }

    /**
     * Gets known data brokers.
     */
    fun getKnownDataBrokers(): List<DataBroker> {
        return listOf(
            DataBroker(
                name = "Whitepages",
                category = DataBrokerCategory.PEOPLE_SEARCH,
                hasOptOut = true,
                optOutUrl = "https://www.whitepages.com/suppression_requests",
                description = "Public records aggregator"
            ),
            DataBroker(
                name = "Spokeo",
                category = DataBrokerCategory.PEOPLE_SEARCH,
                hasOptOut = true,
                optOutUrl = "https://www.spokeo.com/optout",
                description = "People search engine"
            ),
            DataBroker(
                name = "BeenVerified",
                category = DataBrokerCategory.BACKGROUND_CHECK,
                hasOptOut = true,
                optOutUrl = "https://www.beenverified.com/opt-out/",
                description = "Background check service"
            ),
            DataBroker(
                name = "Intelius",
                category = DataBrokerCategory.PEOPLE_SEARCH,
                hasOptOut = true,
                optOutUrl = "https://www.intelius.com/opt-out",
                description = "People search and background check"
            ),
            DataBroker(
                name = "PeopleFinder",
                category = DataBrokerCategory.PEOPLE_SEARCH,
                hasOptOut = true,
                optOutUrl = "https://www.peoplefinder.com/optout",
                description = "Public records search"
            ),
            DataBroker(
                name = "FastPeopleSearch",
                category = DataBrokerCategory.PEOPLE_SEARCH,
                hasOptOut = true,
                optOutUrl = "https://www.fastpeoplesearch.com/removal",
                description = "Free people search"
            ),
            DataBroker(
                name = "TruePeopleSearch",
                category = DataBrokerCategory.PEOPLE_SEARCH,
                hasOptOut = true,
                optOutUrl = "https://www.truepeoplesearch.com/removal",
                description = "Free people lookup"
            ),
            DataBroker(
                name = "Acxiom",
                category = DataBrokerCategory.MARKETING,
                hasOptOut = true,
                optOutUrl = "https://isapps.acxiom.com/optout/optout.aspx",
                description = "Marketing data broker"
            ),
            DataBroker(
                name = "Oracle Data Cloud",
                category = DataBrokerCategory.MARKETING,
                hasOptOut = true,
                optOutUrl = "https://www.oracle.com/legal/privacy/marketing-cloud-data-cloud-privacy-policy.html",
                description = "Advertising data"
            ),
            DataBroker(
                name = "Epsilon",
                category = DataBrokerCategory.MARKETING,
                hasOptOut = true,
                optOutUrl = "https://us.epsilon.com/consumer-information",
                description = "Marketing data provider"
            )
        )
    }

    /**
     * Adds email to monitoring.
     */
    fun addMonitoredEmail(email: String) {
        val monitored = MonitoredEmail(
            email = email,
            addedAt = System.currentTimeMillis()
        )
        _monitoredEmails.update { it + monitored }
    }

    /**
     * Removes email from monitoring.
     */
    fun removeMonitoredEmail(emailId: String) {
        _monitoredEmails.update { it.filter { e -> e.id != emailId } }
    }

    /**
     * Checks all monitored emails.
     */
    fun checkAllMonitoredEmails(apiKey: String): Flow<EmailCheckProgress> = flow {
        val emails = _monitoredEmails.value
        emit(EmailCheckProgress.Started(emails.size))

        var checked = 0
        for (email in emails) {
            emit(EmailCheckProgress.Checking(email.email, checked, emails.size))
            val result = checkEmail(email.email, apiKey)
            
            // Update last check
            _monitoredEmails.update { list ->
                list.map { e ->
                    if (e.id == email.id) {
                        e.copy(
                            lastCheckedAt = System.currentTimeMillis(),
                            lastResult = result
                        )
                    } else e
                }
            }

            checked++
            emit(EmailCheckProgress.Progress(email.email, result, checked, emails.size))

            // Rate limiting
            kotlinx.coroutines.delay(1500)
        }

        emit(EmailCheckProgress.Completed(checked))
    }.flowOn(Dispatchers.IO)

    /**
     * Gets privacy risk score based on breach data.
     */
    suspend fun getPrivacyRiskScore(): PrivacyRiskScore = withContext(Dispatchers.IO) {
        val checks = _checkHistory.value
        val totalBreaches = checks.sumOf { it.breachCount }
        val sensitiveBreaches = checks.flatMap { it.breaches }
            .count { it.isSensitive }

        val score = when {
            totalBreaches == 0 -> 100
            totalBreaches < 3 -> 80
            totalBreaches < 5 -> 60
            totalBreaches < 10 -> 40
            else -> 20
        } - (sensitiveBreaches * 10)

        PrivacyRiskScore(
            score = maxOf(score, 0),
            totalBreaches = totalBreaches,
            sensitiveBreaches = sensitiveBreaches,
            recommendations = buildRecommendations(checks)
        )
    }

    private fun buildRecommendations(checks: List<BreachCheck>): List<String> {
        val recommendations = mutableListOf<String>()

        if (checks.any { it.breachCount > 0 }) {
            recommendations.add("Change passwords for breached accounts")
            recommendations.add("Enable two-factor authentication where available")
        }

        if (checks.flatMap { it.breaches }.any { it.dataClasses.contains("Passwords") }) {
            recommendations.add("Use a password manager for unique passwords")
        }

        recommendations.add("Regularly check for new breaches")
        recommendations.add("Consider opting out from data brokers")

        return recommendations
    }

    private fun hashSha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parseBreaches(json: String): List<BreachInfo> {
        return try {
            // Simple parsing - in production use proper JSON deserialization
            val regex = Regex(""""Name"\s*:\s*"([^"]+)"""")
            regex.findAll(json).map { match ->
                BreachInfo(
                    name = match.groupValues[1],
                    domain = "",
                    breachDate = "",
                    dataClasses = emptyList(),
                    description = "",
                    isSensitive = false,
                    isVerified = true
                )
            }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Password check result.
 */
sealed class PasswordCheckResult {
    data object Safe : PasswordCheckResult()
    data class Pwned(val exposureCount: Int, val recommendation: String) : PasswordCheckResult()
    data class Error(val message: String, val exception: Throwable? = null) : PasswordCheckResult()
}

/**
 * Email check result.
 */
sealed class EmailCheckResult {
    data object Clean : EmailCheckResult()
    data class Breached(val breaches: List<BreachInfo>) : EmailCheckResult()
    data object ApiKeyRequired : EmailCheckResult()
    data object RateLimited : EmailCheckResult()
    data class Error(val message: String) : EmailCheckResult()
}

/**
 * Breach information.
 */
@Serializable
data class BreachInfo(
    val name: String,
    val domain: String,
    val breachDate: String,
    val dataClasses: List<String>,
    val description: String,
    val isSensitive: Boolean,
    val isVerified: Boolean
)

/**
 * Breach check record.
 */
@Serializable
data class BreachCheck(
    val id: String = UUID.randomUUID().toString(),
    val email: String,
    val breachCount: Int,
    val breaches: List<BreachInfo>,
    val checkedAt: Long
)

/**
 * Monitored email.
 */
@Serializable
data class MonitoredEmail(
    val id: String = UUID.randomUUID().toString(),
    val email: String,
    val addedAt: Long,
    val lastCheckedAt: Long? = null,
    @kotlinx.serialization.Transient
    val lastResult: EmailCheckResult? = null
)

/**
 * Data broker information.
 */
@Serializable
data class DataBroker(
    val name: String,
    val category: DataBrokerCategory,
    val hasOptOut: Boolean,
    val optOutUrl: String?,
    val description: String
)

/**
 * Data broker category.
 */
enum class DataBrokerCategory {
    PEOPLE_SEARCH,
    BACKGROUND_CHECK,
    MARKETING,
    CREDIT,
    HEALTH,
    OTHER
}

/**
 * Email check progress.
 */
sealed class EmailCheckProgress {
    data class Started(val total: Int) : EmailCheckProgress()
    data class Checking(val email: String, val current: Int, val total: Int) : EmailCheckProgress()
    data class Progress(val email: String, val result: EmailCheckResult, val current: Int, val total: Int) : EmailCheckProgress()
    data class Completed(val total: Int) : EmailCheckProgress()
}

/**
 * Privacy risk score.
 */
@Serializable
data class PrivacyRiskScore(
    val score: Int,
    val totalBreaches: Int,
    val sensitiveBreaches: Int,
    val recommendations: List<String>
)
