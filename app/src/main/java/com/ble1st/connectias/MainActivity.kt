package com.ble1st.connectias

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.ble1st.connectias.core.BuildConfig
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.core.services.LoggingService
import com.ble1st.connectias.core.services.SecurityService
import com.ble1st.connectias.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
        
        // Perform security checks before UI initialization
        lifecycleScope.launch {
            try {
                val result = withTimeoutOrNull(5000) {
                    securityService.performSecurityCheckWithTermination()
                }
                
                if (result == null) {
                    Timber.w("Security check timed out - continuing with app start")
                    // Continue app start even if check timed out
                } else if (result.threats.isNotEmpty() && !BuildConfig.DEBUG) {
                    // App will be terminated by SecurityService
                    // This code should not be reached, but kept for safety
                    Timber.e("Security threats detected - app should have been terminated")
                    return@launch
                }
            } catch (e: Exception) {
                Timber.e(e, "Security check failed during app start")
                // In production: decide whether to continue or terminate
                // For now: continue with app start to avoid blocking users
                if (!BuildConfig.DEBUG) {
                    // In production, consider terminating on security check failure
                    // For MVP: log and continue
                }
            }
        }
        
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
}
