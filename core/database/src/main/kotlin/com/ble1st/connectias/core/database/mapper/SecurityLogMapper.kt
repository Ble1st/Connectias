package com.ble1st.connectias.core.database.mapper

import com.ble1st.connectias.core.database.entities.SecurityLogEntity
import com.ble1st.connectias.core.model.SecurityThreat

private const val TYPE_ROOT = "ROOT"
private const val TYPE_DEBUGGER = "DEBUGGER"
private const val TYPE_EMULATOR = "EMULATOR"
private const val TYPE_TAMPER = "TAMPER"
private const val TYPE_HOOK = "HOOK"
private const val TYPE_UNKNOWN = "UNKNOWN"

private const val LEVEL_CRITICAL = "CRITICAL"
private const val LEVEL_HIGH = "HIGH"
private const val LEVEL_MEDIUM = "MEDIUM"
private const val LEVEL_LOW = "LOW"

fun SecurityThreat.toEntity(): SecurityLogEntity {
    val (type, level) = when (this) {
        is SecurityThreat.RootDetected -> TYPE_ROOT to LEVEL_CRITICAL
        is SecurityThreat.TamperDetected -> TYPE_TAMPER to LEVEL_CRITICAL
        is SecurityThreat.DebuggerDetected -> TYPE_DEBUGGER to LEVEL_HIGH
        is SecurityThreat.HookDetected -> TYPE_HOOK to LEVEL_HIGH
        is SecurityThreat.EmulatorDetected -> TYPE_EMULATOR to LEVEL_MEDIUM
    }

    return SecurityLogEntity(
        threatType = type,
        threatLevel = level,
        description = name,
        details = null
    )
}

fun SecurityLogEntity.toModel(): SecurityThreat {
    return when (threatType) {
        TYPE_ROOT -> SecurityThreat.RootDetected()
        TYPE_TAMPER -> SecurityThreat.TamperDetected()
        TYPE_DEBUGGER -> SecurityThreat.DebuggerDetected()
        TYPE_EMULATOR -> SecurityThreat.EmulatorDetected()
        TYPE_HOOK -> SecurityThreat.HookDetected()
        else -> SecurityThreat.HookDetected()
    }
}
