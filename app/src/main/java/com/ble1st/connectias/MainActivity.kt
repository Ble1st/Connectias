package com.ble1st.connectias

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.ble1st.connectias.BuildConfig
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.core.services.LoggingService
import com.ble1st.connectias.core.services.SecurityService
import com.ble1st.connectias.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var moduleRegistry: ModuleRegistry
    
    @Inject
    lateinit var loggingService: LoggingService
    
    @Inject
    lateinit var securityService: SecurityService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Hilt injection happens here
        
        // Initialize UI immediately to prevent empty/white screen
        // Security checks run in parallel and will terminate app if threats detected
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        
        // System UI Insets handhaben
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Navigation setup
        setupNavigation()
        
        // Module Discovery
        setupModuleDiscovery()
        
        // Log rotation (Hilt injection is guaranteed after super.onCreate())
        lifecycleScope.launch {
            try {
                loggingService.rotateLogs()
            } catch (e: Exception) {
                Timber.e(e, "Failed to perform log rotation on app start")
            }
        }
        
        // Perform security checks asynchronously (UI is already initialized)
        // If threats are detected, app will be terminated
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(5000) {
                    securityService.performSecurityCheckWithTermination()
                }
                
                // Switch back to Main thread for termination if needed
                withContext(Dispatchers.Main) {
                    if (result == null) {
                        // Timeout occurred - treat as failure in production
                        Timber.e("Security check timed out - terminating app in production")
                        if (!BuildConfig.DEBUG) {
                            terminateApp()
                            return@withContext
                        }
                    } else if (result.threats.isNotEmpty()) {
                        // Threats detected - app should be terminated by SecurityService
                        // But ensure termination if it didn't happen
                        Timber.e("Security threats detected - ensuring app termination")
                        if (!BuildConfig.DEBUG) {
                            terminateApp()
                            return@withContext
                        }
                    }
                    // Security check passed - UI is already initialized and ready
                }
            } catch (e: Exception) {
                // Exception during security check - treat as failure in production
                Timber.e(e, "Security check failed during app start - terminating app in production")
                withContext(Dispatchers.Main) {
                    if (!BuildConfig.DEBUG) {
                        terminateApp()
                    }
                    // In debug mode, continue with already-initialized UI
                }
            }
        }
    }

    private fun setupNavigation() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        
        // Bottom Navigation mit NavController verbinden
        binding.bottomNavigation.setupWithNavController(navController)
        
        Timber.d("Navigation setup completed")
    }

    private fun setupModuleDiscovery() {
        // Register core modules (always active)
        moduleRegistry.registerModule(
            com.ble1st.connectias.core.module.ModuleInfo(
                id = "security",
                name = "Security Dashboard",
                version = "1.0.0",
                isActive = true
            )
        )
        
        moduleRegistry.registerModule(
            com.ble1st.connectias.core.module.ModuleInfo(
                id = "settings",
                name = "Settings",
                version = "1.0.0",
                isActive = true
            )
        )
        
        // Register Device Info module if available (optional - MVP)
        try {
            Class.forName("com.ble1st.connectias.feature.deviceinfo.ui.DeviceInfoFragment")
            moduleRegistry.registerModule(
                com.ble1st.connectias.core.module.ModuleInfo(
                    id = "device-info",
                    name = "Device Info",
                    version = "1.0.0",
                    isActive = true
                )
            )
            Timber.d("Device Info module registered")
        } catch (e: ClassNotFoundException) {
            Timber.d("Device Info module not available (not compiled)")
        }

        // Register Privacy module if available (optional)
        try {
            Class.forName("com.ble1st.connectias.feature.privacy.ui.PrivacyDashboardFragment")
            moduleRegistry.registerModule(
                com.ble1st.connectias.core.module.ModuleInfo(
                    id = "privacy",
                    name = "Privacy Dashboard",
                    version = "1.0.0",
                    isActive = true
                )
            )
            Timber.d("Privacy module registered")
        } catch (e: ClassNotFoundException) {
            Timber.d("Privacy module not available (not compiled)")
        }
        
        // Log active modules
        val activeModules = moduleRegistry.getActiveModules()
        Timber.d("Active modules: ${activeModules.size}")
        activeModules.forEach { module ->
            Timber.d("  - ${module.name} (${module.id}) v${module.version}")
        }
    }
    
    /**
     * Terminates the app when security checks fail or threats are detected.
     * Uses finishAffinity() to close all activities and then kills the process.
     */
    private fun terminateApp() {
        Timber.e("Terminating app due to security failure")
        try {
            finishAffinity() // Close all activities in the task
        } catch (e: Exception) {
            Timber.e(e, "Error calling finishAffinity")
        }
        Process.killProcess(Process.myPid())
        System.exit(1)
    }
}
