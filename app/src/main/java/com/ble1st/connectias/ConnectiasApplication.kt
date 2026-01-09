package com.ble1st.connectias

import android.app.Application
import com.ble1st.connectias.core.logging.LoggingTreeEntryPoint
import com.ble1st.connectias.performance.StrictModeConfig
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

@HiltAndroidApp
class ConnectiasApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Phase 8: Enable StrictMode for performance monitoring in Debug builds
        StrictModeConfig.enableStrictMode(BuildConfig.DEBUG)
        
        // Initialize Timber for logging
        // Get ConnectiasLoggingTree via Hilt EntryPoint
        val loggingTree = EntryPointAccessors.fromApplication(
            this,
            LoggingTreeEntryPoint::class.java
        ).loggingTree()
        
        // Plant database logging tree first (for production)
        Timber.plant(loggingTree)
        
        // Also plant debug tree in debug builds for logcat output
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.d("ConnectiasApplication initialized")
    }
}

