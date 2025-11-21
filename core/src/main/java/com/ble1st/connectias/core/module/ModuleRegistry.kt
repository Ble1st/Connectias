package com.ble1st.connectias.core.module

data class ModuleInfo(
    val id: String,
    val name: String,
    val version: String,
    val isActive: Boolean
)

import java.util.concurrent.ConcurrentHashMap

class ModuleRegistry {
    private val modules = ConcurrentHashMap<String, ModuleInfo>()
    fun registerModule(moduleInfo: ModuleInfo) {
        modules[moduleInfo.id] = moduleInfo
    }

    fun getModule(id: String): ModuleInfo? {
        return modules[id]
    }

    fun getAllModules(): List<ModuleInfo> {
        return modules.values.toList()
    }

    fun getActiveModules(): List<ModuleInfo> {
        return modules.values.filter { it.isActive }
    }
}

