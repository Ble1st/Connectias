// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.navigation

/**
 * Navigation interface for plugin-related screens.
 *
 * This interface decouples the :plugin module from app-specific navigation resources (R.id.*).
 * The app module implements this interface and binds it via Hilt, allowing plugin screens
 * to trigger navigation without depending on :app.
 */
interface PluginNavigator {
    /**
     * Navigate to the plugin store screen
     */
    fun navigateToPluginStore()

    /**
     * Navigate to the plugin management screen
     */
    fun navigateToPluginManagement()

    /**
     * Navigate to plugin permission detail screen
     * @param pluginId The ID of the plugin whose permissions to display
     */
    fun navigateToPermissionDetail(pluginId: String)

    /**
     * Navigate to security audit dashboard
     */
    fun navigateToSecurityAudit()

    /**
     * Navigate to network policy configuration
     */
    fun navigateToNetworkPolicy()

    /**
     * Navigate to security audit dashboard (alternative name)
     */
    fun navigateToSecurityAuditDashboard()

    /**
     * Navigate to privacy dashboard
     */
    fun navigateToPrivacyDashboard()

    /**
     * Navigate to plugin analytics dashboard
     */
    fun navigateToPluginAnalytics()

    /**
     * Navigate to declarative plugin builder
     */
    fun navigateToDeclarativeBuilder()

    /**
     * Navigate to developer keys management
     */
    fun navigateToDeveloperKeys()

    /**
     * Navigate to declarative flow run viewer
     * @param runId The ID of the run to view
     */
    fun navigateToFlowRunViewer(runId: String)

    /**
     * Navigate to plugin security dashboard
     */
    fun navigateToPluginSecurityDashboard()

    /**
     * Navigate back to the previous screen
     */
    fun navigateBack()
}
