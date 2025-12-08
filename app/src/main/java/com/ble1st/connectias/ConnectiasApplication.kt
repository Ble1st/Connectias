package com.ble1st.connectias

import android.app.Application
import com.ble1st.connectias.core.logging.ConnectiasLoggingTree
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ConnectiasApplication : Application() {

    @Inject
    lateinit var connectiasLoggingTree: ConnectiasLoggingTree

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging (App-only logs)
        Timber.plant(connectiasLoggingTree)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Apply initial logging level and keep it in sync with settings
        connectiasLoggingTree.setMinPriority(settingsRepository.getLoggingLevel())
        appScope.launch {
            settingsRepository.observeLoggingLevel().collect { level ->
                connectiasLoggingTree.setMinPriority(level)
            }
        }
        
        Timber.d("ConnectiasApplication initialized")
        // Log rotation moved to MainActivity.onCreate()
    }
}

