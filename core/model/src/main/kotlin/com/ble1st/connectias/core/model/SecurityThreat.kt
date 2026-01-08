package com.ble1st.connectias.core.model

/**
 * Sealed hierarchy representing different types of security threats.
 * Pure Kotlin without Android dependencies.
 */
sealed class SecurityThreat(open val name: String) {
    data class RootDetected(val method: String) : SecurityThreat("Root Detected")
    data class DebuggerDetected(val method: String) : SecurityThreat("Debugger Detected")
    data class EmulatorDetected(val method: String) : SecurityThreat("Emulator Detected")
    data class TamperDetected(val method: String) : SecurityThreat("Tamper Detected")
    data class HookDetected(val method: String) : SecurityThreat("Hook Detected")
}
