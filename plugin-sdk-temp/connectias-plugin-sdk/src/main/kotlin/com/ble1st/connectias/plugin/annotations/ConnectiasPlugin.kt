package com.ble1st.connectias.plugin.annotations

/**
 * Marks the plugin entry class
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConnectiasPlugin(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "Unknown",
    val category: String = "UTILITY"
)

/**
 * Marks a Fragment class that comes from a plugin
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PluginFragment(
    val id: String,
    val name: String,
    val icon: String = ""
)

/**
 * Marks a Kotlin coroutine function as a plugin service
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PluginService(
    val name: String
)
