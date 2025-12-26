package com.ble1st.connectias.core.services

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for network operations.
 * Provides common network APIs for use across features.
 * 
 * Required permissions:
 * - android.permission.ACCESS_NETWORK_STATE (declared in AndroidManifest.xml)
 * 
 * Note: This service does not require runtime permission requests as ACCESS_NETWORK_STATE
 * is a normal permission that is automatically granted at install time.
 */
@Singleton
class NetworkService @Inject constructor()

