package com.ble1st.connectias

import android.app.Application
import com.ble1st.connectias.core.services.LoggingService
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
    lateinit var loggingService: LoggingService
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.d("ConnectiasApplication initialized")
        
        // Perform log rotation on app start (after Hilt injection is ready)
        // This is done asynchronously and won't block app startup
        // Note: Hilt injection happens after onCreate, so we use a coroutine with a small delay
        applicationScope.launch {
            try {
                // Small delay to ensure Hilt injection is complete
                kotlinx.coroutines.delay(500)
                if (::loggingService.isInitialized) {
                    loggingService.rotateLogs()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to perform log rotation on app start")
            }
        }
    }
}

