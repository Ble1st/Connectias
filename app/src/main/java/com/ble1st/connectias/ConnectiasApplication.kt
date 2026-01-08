package com.ble1st.connectias

import android.app.Application
import com.ble1st.connectias.performance.StrictModeConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ConnectiasApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Phase 8: Enable StrictMode for performance monitoring in Debug builds
        StrictModeConfig.enableStrictMode(BuildConfig.DEBUG)
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.d("ConnectiasApplication initialized")
        // Note: Advanced logging with ConnectiasLoggingTree will be re-implemented
        // in a future update using the new Domain Layer architecture
    }
}

