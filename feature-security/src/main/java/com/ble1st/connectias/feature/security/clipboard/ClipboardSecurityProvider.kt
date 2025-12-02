package com.ble1st.connectias.feature.security.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for clipboard security monitoring functionality.
 *
 * Features:
 * - Clipboard access monitoring
 * - Sensitive data detection
 * - Auto-clear functionality
 * - Clipboard history (with security)
 * - Access logging
 */
@Singleton
class ClipboardSecurityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) 
        as ClipboardManager

    private val _accessLog = MutableStateFlow<List<ClipboardAccess>>(emptyList())
    val accessLog: StateFlow<List<ClipboardAccess>> = _accessLog.asStateFlow()

    private val _settings = MutableStateFlow(ClipboardSecuritySettings())
    val settings: StateFlow<ClipboardSecuritySettings> = _settings.asStateFlow()

    private val _currentContent = MutableStateFlow<ClipboardContent?>(null)
    val currentContent: StateFlow<ClipboardContent?> = _currentContent.asStateFlow()

    // Patterns for sensitive data detection
    private val sensitivePatterns = mapOf(
        SensitiveDataType.CREDIT_CARD to Regex("""(\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4})"""),
        SensitiveDataType.SSN to Regex("""\d{3}[-\s]?\d{2}[-\s]?\d{4}"""),
        SensitiveDataType.EMAIL to Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""),
        SensitiveDataType.PHONE to Regex("""\+?\d{1,3}[-.\s]?\(?\d{2,3}\)?[-.\s]?\d{3,4}[-.\s]?\d{4}"""),
        SensitiveDataType.PASSWORD to Regex("""(?i)(password|passwort|pwd|pass)[\s:=]+\S+"""),
        SensitiveDataType.API_KEY to Regex("""(?i)(api[_-]?key|apikey|access[_-]?token|bearer)[\s:=]+[\w-]+"""),
        SensitiveDataType.PRIVATE_KEY to Regex("""-----BEGIN\s+(RSA\s+)?PRIVATE\s+KEY-----"""),
        SensitiveDataType.IBAN to Regex("""[A-Z]{2}\d{2}[A-Z0-9]{1,30}""")
    )

    /**
     * Starts clipboard monitoring.
     */
    fun startMonitoring() {
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        Timber.d("Clipboard monitoring started")
    }

    /**
     * Stops clipboard monitoring.
     */
    fun stopMonitoring() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        Timber.d("Clipboard monitoring stopped")
    }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                processClipboardChange(text)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing clipboard change")
        }
    }

    private fun processClipboardChange(text: String?) {
        if (text.isNullOrEmpty()) return

        val detectedTypes = detectSensitiveData(text)
        val content = ClipboardContent(
            text = text,
            sensitiveDataTypes = detectedTypes,
            timestamp = System.currentTimeMillis()
        )

        _currentContent.value = content

        logAccess(ClipboardAccess(
            type = AccessType.WRITE,
            contentPreview = text.take(50) + if (text.length > 50) "..." else "",
            containsSensitiveData = detectedTypes.isNotEmpty(),
            sensitiveDataTypes = detectedTypes,
            timestamp = System.currentTimeMillis()
        ))

        // Auto-clear if sensitive and enabled
        if (detectedTypes.isNotEmpty() && _settings.value.autoClearSensitive) {
            scheduleAutoClear(_settings.value.autoClearDelayMs)
        }
    }

    /**
     * Analyzes current clipboard content.
     */
    suspend fun analyzeClipboard(): ClipboardAnalysis = withContext(Dispatchers.IO) {
        try {
            val clip = clipboardManager.primaryClip
            if (clip == null || clip.itemCount == 0) {
                return@withContext ClipboardAnalysis(isEmpty = true)
            }

            val text = clip.getItemAt(0).text?.toString() ?: ""
            val detectedTypes = detectSensitiveData(text)

            ClipboardAnalysis(
                isEmpty = text.isEmpty(),
                contentLength = text.length,
                contentPreview = text.take(100) + if (text.length > 100) "..." else "",
                containsSensitiveData = detectedTypes.isNotEmpty(),
                sensitiveDataTypes = detectedTypes,
                mimeType = clip.description?.getMimeType(0),
                analyzedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing clipboard")
            ClipboardAnalysis(isEmpty = true, error = e.message)
        }
    }

    /**
     * Detects sensitive data in text.
     */
    fun detectSensitiveData(text: String): List<SensitiveDataType> {
        val detected = mutableListOf<SensitiveDataType>()

        for ((type, pattern) in sensitivePatterns) {
            if (pattern.containsMatchIn(text)) {
                detected.add(type)
            }
        }

        return detected
    }

    /**
     * Clears the clipboard.
     */
    suspend fun clearClipboard(): Boolean = withContext(Dispatchers.Main) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboardManager.clearPrimaryClip()
            } else {
                clipboardManager.setPrimaryClip(
                    android.content.ClipData.newPlainText("", "")
                )
            }

            _currentContent.value = null

            logAccess(ClipboardAccess(
                type = AccessType.CLEAR,
                contentPreview = "[Cleared]",
                timestamp = System.currentTimeMillis()
            ))

            true
        } catch (e: Exception) {
            Timber.e(e, "Error clearing clipboard")
            false
        }
    }

    /**
     * Schedules automatic clipboard clear.
     */
    private fun scheduleAutoClear(delayMs: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboardManager.clearPrimaryClip()
                } else {
                    clipboardManager.setPrimaryClip(
                        android.content.ClipData.newPlainText("", "")
                    )
                }
                _currentContent.value = null
                Timber.d("Clipboard auto-cleared after $delayMs ms")
            } catch (e: Exception) {
                Timber.e(e, "Error in auto-clear")
            }
        }, delayMs)
    }

    /**
     * Monitors clipboard continuously.
     */
    fun monitorClipboard(intervalMs: Long = 5000): Flow<ClipboardAnalysis> = flow {
        while (true) {
            emit(analyzeClipboard())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Logs clipboard access.
     */
    private fun logAccess(access: ClipboardAccess) {
        _accessLog.update { log ->
            (listOf(access) + log).take(100) // Keep last 100 entries
        }
    }

    /**
     * Gets access log.
     */
    fun getAccessLog(): List<ClipboardAccess> = _accessLog.value

    /**
     * Clears access log.
     */
    fun clearAccessLog() {
        _accessLog.value = emptyList()
    }

    /**
     * Updates settings.
     */
    fun updateSettings(settings: ClipboardSecuritySettings) {
        _settings.value = settings
    }

    /**
     * Copies text securely (with auto-clear).
     */
    suspend fun copySecurely(
        text: String,
        label: String = "Secure",
        autoClearDelayMs: Long? = null
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            val clip = android.content.ClipData.newPlainText(label, text)
            clipboardManager.setPrimaryClip(clip)

            val delay = autoClearDelayMs ?: _settings.value.autoClearDelayMs
            scheduleAutoClear(delay)

            true
        } catch (e: Exception) {
            Timber.e(e, "Error copying securely")
            false
        }
    }

    /**
     * Gets clipboard security score.
     */
    suspend fun getSecurityScore(): ClipboardSecurityScore = withContext(Dispatchers.IO) {
        val analysis = analyzeClipboard()
        val recentLogs = _accessLog.value.filter { 
            System.currentTimeMillis() - it.timestamp < 24 * 60 * 60 * 1000 // Last 24 hours
        }

        val sensitiveAccessCount = recentLogs.count { it.containsSensitiveData }
        val settings = _settings.value

        var score = 100

        // Deduct for sensitive data in clipboard
        if (analysis.containsSensitiveData) {
            score -= 30
        }

        // Deduct for sensitive accesses
        score -= minOf(sensitiveAccessCount * 5, 30)

        // Add for security features enabled
        if (!settings.autoClearSensitive) {
            score -= 10
        }

        ClipboardSecurityScore(
            score = maxOf(score, 0),
            currentContentRisk = if (analysis.containsSensitiveData) RiskLevel.HIGH else RiskLevel.LOW,
            sensitiveAccessesLast24h = sensitiveAccessCount,
            recommendations = buildRecommendations(analysis, settings)
        )
    }

    private fun buildRecommendations(
        analysis: ClipboardAnalysis,
        settings: ClipboardSecuritySettings
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (analysis.containsSensitiveData) {
            recommendations.add("Clear clipboard - sensitive data detected")
        }

        if (!settings.autoClearSensitive) {
            recommendations.add("Enable auto-clear for sensitive data")
        }

        if (settings.autoClearDelayMs > 60000) {
            recommendations.add("Reduce auto-clear delay to under 1 minute")
        }

        return recommendations
    }
}

/**
 * Clipboard content.
 */
@Serializable
data class ClipboardContent(
    val text: String,
    val sensitiveDataTypes: List<SensitiveDataType>,
    val timestamp: Long
)

/**
 * Clipboard analysis result.
 */
@Serializable
data class ClipboardAnalysis(
    val isEmpty: Boolean,
    val contentLength: Int = 0,
    val contentPreview: String? = null,
    val containsSensitiveData: Boolean = false,
    val sensitiveDataTypes: List<SensitiveDataType> = emptyList(),
    val mimeType: String? = null,
    val analyzedAt: Long = System.currentTimeMillis(),
    val error: String? = null
)

/**
 * Clipboard access log entry.
 */
@Serializable
data class ClipboardAccess(
    val id: String = UUID.randomUUID().toString(),
    val type: AccessType,
    val contentPreview: String,
    val containsSensitiveData: Boolean = false,
    val sensitiveDataTypes: List<SensitiveDataType> = emptyList(),
    val timestamp: Long
)

/**
 * Access type.
 */
enum class AccessType {
    READ,
    WRITE,
    CLEAR
}

/**
 * Sensitive data types.
 */
enum class SensitiveDataType {
    CREDIT_CARD,
    SSN,
    EMAIL,
    PHONE,
    PASSWORD,
    API_KEY,
    PRIVATE_KEY,
    IBAN
}

/**
 * Clipboard security settings.
 */
@Serializable
data class ClipboardSecuritySettings(
    val autoClearSensitive: Boolean = true,
    val autoClearDelayMs: Long = 30000,
    val logAccesses: Boolean = true,
    val notifyOnSensitive: Boolean = true
)

/**
 * Risk level.
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Clipboard security score.
 */
@Serializable
data class ClipboardSecurityScore(
    val score: Int,
    val currentContentRisk: RiskLevel,
    val sensitiveAccessesLast24h: Int,
    val recommendations: List<String>
)
