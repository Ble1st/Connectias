package com.ble1st.connectias.core.logging

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt EntryPoint for accessing ConnectiasLoggingTree in Application.onCreate().
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LoggingTreeEntryPoint {
    fun loggingTree(): ConnectiasLoggingTree
}
