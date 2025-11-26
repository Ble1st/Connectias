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

    private var binding: ActivityMainBinding? = null

    @Inject
    lateinit var moduleRegistry: ModuleRegistry
    
    @Inject
    lateinit var loggingService: LoggingService
    
    @Inject
    lateinit var securityService: SecurityService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Hilt injection happens here
        
        // Show splash screen immediately to prevent empty/white screen
        // This is a secure, non-interactive screen while security checks run
        // Note: Splash screen intentionally does not use edge-to-edge to avoid system bar overlap
        // Edge-to-edge is enabled only for the main UI after security checks pass
        setContentView(R.layout.activity_splash)
        
        // Perform security checks before initializing main UI
        // This prevents security vulnerabilities from race conditions
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(5000) {
                    securityService.performSecurityCheckWithTermination()
                }
                
                // Switch back to Main thread for UI initialization or termination
                withContext(Dispatchers.Main) {
                    if (result == null) {
                        // Timeout occurred - treat as failure in production
                        Timber.e("Security check timed out - terminating app in production")
                        if (!BuildConfig.DEBUG) {
                            terminateApp()
                            return@withContext
                        }
                        // In debug mode, continue with UI initialization
                        initializeMainUI()
                    } else if (result.threats.isNotEmpty()) {
                        // Threats detected - app should be terminated by SecurityService
                        // But ensure termination if it didn't happen
                        Timber.e("Security threats detected - ensuring app termination")
                        if (!BuildConfig.DEBUG) {
                            terminateApp()
                            return@withContext
                        }
                        // In debug mode, continue with UI initialization
                        initializeMainUI()
                    } else {
                        // Security check passed - safe to initialize main UI
                        initializeMainUI()
                    }
                }
            } catch (e: Exception) {
                // Exception during security check - treat as failure in production
                Timber.e(e, "Security check failed during app start - terminating app in production")
                withContext(Dispatchers.Main) {
                    if (!BuildConfig.DEBUG) {
                        terminateApp()
                    } else {
                        // In debug mode, continue with UI initialization
                        initializeMainUI()
                    }
                }
            }
        }
    }
    
    /**
     * Initializes the main UI after security checks have passed.
     * This method is only called after successful security validation.
     * This ensures no sensitive UI components are accessible before security validation.
     */
    private fun initializeMainUI() {
        // Initialize main UI binding
        val mainBinding = ActivityMainBinding.inflate(layoutInflater)
        binding = mainBinding
        setContentView(mainBinding.root)
        
        // Enable edge-to-edge for main UI (splash screen intentionally does not use it)
        enableEdgeToEdge()
        
        // Handle system UI insets
        ViewCompat.setOnApplyWindowInsetsListener(mainBinding.root) { v, insets ->
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
    }

    private fun setupNavigation() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        
        // Bottom Navigation mit NavController verbinden
        binding?.bottomNavigation?.setupWithNavController(navController)
        
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

        // Register optional modules
        registerOptionalModule(
            className = "com.ble1st.connectias.feature.privacy.ui.PrivacyDashboardFragment",
            moduleId = "privacy",
            moduleName = "Privacy Dashboard"
        )
        
        registerOptionalModule(
            className = "com.ble1st.connectias.feature.network.ui.NetworkDashboardFragment",
            moduleId = "network",
            moduleName = "Network Dashboard"
        )
        
        // Log active modules
        val activeModules = moduleRegistry.getActiveModules()
        Timber.d("Active modules: ${activeModules.size}")
        activeModules.forEach { module ->
            Timber.d("  - ${module.name} (${module.id}) v${module.version}")
        }
    }
    
    /**
     * Helper function to register an optional module if its class is available.
     * Reduces code duplication for module discovery.
     * 
     * @param className Fully qualified class name of the module's fragment
     * @param moduleId Unique identifier for the module
     * @param moduleName Display name of the module
     */
    private fun registerOptionalModule(className: String, moduleId: String, moduleName: String) {
        try {
            Class.forName(className)
            moduleRegistry.registerModule(
                com.ble1st.connectias.core.module.ModuleInfo(
                    id = moduleId,
                    name = moduleName,
                    version = "1.0.0",
                    isActive = true
                )
            )
            Timber.d("$moduleName module registered")
        } catch (e: ClassNotFoundException) {
            Timber.d("$moduleName module not available (not compiled)")
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
