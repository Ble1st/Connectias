package com.ble1st.connectias.core.model

/**
 * Sealed hierarchy representing different types of security threats.
 * Pure Kotlin without Android dependencies.
 */
sealed class SecurityThreat(open val name: String) {
    class RootDetected : SecurityThreat("Root Detected")
    class DebuggerDetected : SecurityThreat("Debugger Detected")
    class EmulatorDetected : SecurityThreat("Emulator Detected")
    class TamperDetected : SecurityThreat("Tamper Detected")
    class HookDetected : SecurityThreat("Hook Detected")
}
