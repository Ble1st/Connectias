package com.ble1st.connectias.core.model

/**
 * Sealed hierarchy representing different types of security threats.
 * Pure Kotlin without Android dependencies.
 */
sealed class SecurityThreat(open val name: String) {
    data class RootDetected() : SecurityThreat("Root Detected")
    data class DebuggerDetected() : SecurityThreat("Debugger Detected")
    data class EmulatorDetected() : SecurityThreat("Emulator Detected")
    data class TamperDetected() : SecurityThreat("Tamper Detected")
    data class HookDetected() : SecurityThreat("Hook Detected")
}
