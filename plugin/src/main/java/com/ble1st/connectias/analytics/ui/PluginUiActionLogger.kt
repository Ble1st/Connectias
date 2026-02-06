package com.ble1st.connectias.analytics.ui

import android.content.Context
import com.ble1st.connectias.analytics.di.AnalyticsStoreEntryPoint
import com.ble1st.connectias.analytics.model.PluginUiActionEvent
import com.ble1st.connectias.analytics.model.PluginSessionEvent
import com.ble1st.connectias.analytics.model.SessionEventType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Records UI interaction events from the UI process.
 *
 * The UI process cannot rely on main-process singletons being initialized; therefore we resolve
 * the store via Hilt EntryPoint.
 */
object PluginUiActionLogger {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun record(context: Context, pluginId: String, actionType: String, targetId: String) {
        val appContext = context.applicationContext
        val store = try {
            EntryPointAccessors.fromApplication(appContext, AnalyticsStoreEntryPoint::class.java).analyticsStore()
        } catch (e: Exception) {
            Timber.w(e, "[ANALYTICS] Failed to resolve analytics store in UI process")
            return
        }

        val event = PluginUiActionEvent(
            timestamp = System.currentTimeMillis(),
            pluginId = pluginId,
            actionType = actionType,
            targetId = targetId
        )

        scope.launch {
            store.appendUiAction(event)
        }
    }

    /**
     * Records plugin session events (foreground start/end) for usage tracking.
     *
     * @param context Application context
     * @param pluginId Plugin identifier
     * @param eventType Session event type (FOREGROUND_START or FOREGROUND_END)
     */
    fun recordSession(context: Context, pluginId: String, eventType: SessionEventType) {
        val appContext = context.applicationContext
        val store = try {
            EntryPointAccessors.fromApplication(appContext, AnalyticsStoreEntryPoint::class.java).analyticsStore()
        } catch (e: Exception) {
            Timber.w(e, "[ANALYTICS] Failed to resolve analytics store for session tracking")
            return
        }

        val event = PluginSessionEvent(
            timestamp = System.currentTimeMillis(),
            pluginId = pluginId,
            eventType = eventType
        )

        scope.launch {
            store.appendSessionEvent(event)
        }
    }
}

