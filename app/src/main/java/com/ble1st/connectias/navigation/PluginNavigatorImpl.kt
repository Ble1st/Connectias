// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.navigation

import androidx.navigation.NavController
import com.ble1st.connectias.R
import com.ble1st.connectias.plugin.navigation.PluginNavigator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PluginNavigator that uses NavController for navigation.
 *
 * This implementation is provided by the app module and uses app-specific navigation resources.
 * It allows the :plugin module to trigger navigation without depending on :app.
 */
@Singleton
class PluginNavigatorImpl @Inject constructor() : PluginNavigator {

    private var navController: NavController? = null

    /**
     * Set the NavController to use for navigation.
     * This should be called when the MainActivity is created.
     */
    fun setNavController(navController: NavController) {
        this.navController = navController
    }

    override fun navigateToPluginStore() {
        navController?.navigate(R.id.nav_plugin_store)
    }

    override fun navigateToPluginManagement() {
        navController?.navigate(R.id.nav_plugin_management)
    }

    override fun navigateToPermissionDetail(pluginId: String) {
        // Create a bundle with the plugin ID argument
        val bundle = android.os.Bundle().apply {
            putString("pluginId", pluginId)
        }
        navController?.navigate(R.id.nav_plugin_permission_detail, bundle)
    }

    override fun navigateToSecurityAudit() {
        navController?.navigate(R.id.nav_security_audit_dashboard)
    }

    override fun navigateToNetworkPolicy() {
        navController?.navigate(R.id.nav_network_policy_config)
    }

    override fun navigateToSecurityAuditDashboard() {
        navController?.navigate(R.id.nav_security_audit_dashboard)
    }

    override fun navigateToPrivacyDashboard() {
        navController?.navigate(R.id.nav_privacy_dashboard)
    }

    override fun navigateToPluginAnalytics() {
        navController?.navigate(R.id.nav_plugin_analytics_dashboard)
    }

    override fun navigateToDeclarativeBuilder() {
        navController?.navigate(R.id.nav_declarative_plugin_builder)
    }

    override fun navigateToDeveloperKeys() {
        navController?.navigate(R.id.nav_developer_keys)
    }

    override fun navigateToFlowRunViewer(runId: String) {
        navController?.navigate(R.id.nav_declarative_flow_runs)
    }

    override fun navigateToPluginSecurityDashboard() {
        navController?.navigate(R.id.nav_security_dashboard)
    }

    override fun navigateBack() {
        navController?.popBackStack()
    }
}
