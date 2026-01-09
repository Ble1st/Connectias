package com.ble1st.connectias.core.security

import com.ble1st.connectias.core.security.debug.DebuggerDetector
import com.ble1st.connectias.core.security.emulator.EmulatorDetector
import com.ble1st.connectias.core.security.models.SecurityCheckResult
import com.ble1st.connectias.core.security.models.SecurityThreat
import com.ble1st.connectias.core.security.root.RootDetector
import com.ble1st.connectias.core.security.tamper.TamperDetector
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RaspManager @Inject constructor(
    private val rootDetector: RootDetector,
    private val debuggerDetector: DebuggerDetector,
    private val tamperDetector: TamperDetector,
    private val emulatorDetector: EmulatorDetector
) {

    suspend fun performSecurityChecks(): SecurityCheckResult {
        Timber.d("Performing comprehensive security checks")

        val threats = mutableListOf<SecurityThreat>()
        val failedChecks = mutableListOf<String>()
        var allChecksCompleted = true

        // Root Detection
        try {
            val rootResult = rootDetector.detectRoot()
            if (rootResult.isRooted) {
                rootResult.detectionMethods.forEach { method ->
                    threats.add(SecurityThreat.RootDetected())
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Root detection failed: SecurityException")
            failedChecks.add("RootDetector")
            allChecksCompleted = false
        } catch (e: java.io.IOException) {
            Timber.e(e, "Root detection failed: IOException")
            failedChecks.add("RootDetector")
            allChecksCompleted = false
        } catch (e: IllegalStateException) {
            Timber.e(e, "Root detection failed: IllegalStateException")
            failedChecks.add("RootDetector")
            allChecksCompleted = false
        } catch (e: Exception) {
            Timber.e(e, "Root detection failed: Unexpected exception")
            failedChecks.add("RootDetector")
            allChecksCompleted = false
        }

        // Debugger Detection
        try {
            val debuggerResult = debuggerDetector.detectDebugger()
            if (debuggerResult.isDebuggerAttached) {
                debuggerResult.detectionMethods.forEach { method ->
                    threats.add(SecurityThreat.DebuggerDetected())
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Debugger detection failed: SecurityException")
            failedChecks.add("DebuggerDetector")
            allChecksCompleted = false
        } catch (e: java.io.IOException) {
            Timber.e(e, "Debugger detection failed: IOException")
            failedChecks.add("DebuggerDetector")
            allChecksCompleted = false
        } catch (e: Exception) {
            Timber.e(e, "Debugger detection failed: Unexpected exception")
            failedChecks.add("DebuggerDetector")
            allChecksCompleted = false
        }

        // Tamper Detection
        try {
            val tamperResult = tamperDetector.detectTampering()
            if (tamperResult.isTampered) {
                tamperResult.detectionMethods.forEach { method ->
                    threats.add(SecurityThreat.TamperDetected())
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Tamper detection failed: SecurityException")
            failedChecks.add("TamperDetector")
            allChecksCompleted = false
        } catch (e: java.io.IOException) {
            Timber.e(e, "Tamper detection failed: IOException")
            failedChecks.add("TamperDetector")
            allChecksCompleted = false
        } catch (e: Exception) {
            Timber.e(e, "Tamper detection failed: Unexpected exception")
            failedChecks.add("TamperDetector")
            allChecksCompleted = false
        }

        // Emulator Detection
        try {
            val emulatorResult = emulatorDetector.detectEmulator()
            if (emulatorResult.isEmulator) {
                emulatorResult.detectionMethodNames.forEach { method ->
                    threats.add(SecurityThreat.EmulatorDetected())
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Emulator detection failed: SecurityException")
            failedChecks.add("EmulatorDetector")
            allChecksCompleted = false
        } catch (e: java.io.IOException) {
            Timber.e(e, "Emulator detection failed: IOException")
            failedChecks.add("EmulatorDetector")
            allChecksCompleted = false
        } catch (e: Exception) {
            Timber.e(e, "Emulator detection failed: Unexpected exception")
            failedChecks.add("EmulatorDetector")
            allChecksCompleted = false
        }

        val isSecure = threats.isEmpty() && failedChecks.isEmpty() && allChecksCompleted

        Timber.i("Security check completed. Secure: $isSecure, Threats: ${threats.size}, Failed: ${failedChecks.size}")

        return SecurityCheckResult.create(
            isSecure = isSecure,
            threats = threats,
            failedChecks = failedChecks,
            allChecksCompleted = allChecksCompleted,
            timestamp = System.currentTimeMillis()
        )
    }
}

