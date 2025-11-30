
package com.ble1st.connectias

import android.content.Intent

import android.os.Bundle

import androidx.activity.enableEdgeToEdge

import androidx.appcompat.app.AppCompatActivity

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

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

    private var isSecurityCheckPending = true
    private var isMainUIInitialized = false
    private var lastHandledNavigateTo: String? = null



    @Inject

    lateinit var moduleRegistry: ModuleRegistry



    @Inject

    lateinit var loggingService: LoggingService



    @Inject

    lateinit var securityService: SecurityService



    override fun onCreate(savedInstanceState: Bundle?) {

        // Install Splash Screen before super.onCreate()

        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState) // Hilt injection happens here



        // Keep the splash screen visible until security checks are complete

        splashScreen.setKeepOnScreenCondition {

            isSecurityCheckPending

        }



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

                        Timber.e("Security check timed out - blocking app in production")

                        if (!BuildConfig.DEBUG) {

                            blockApp()

                            return@withContext

                        }

                        // In debug mode, continue with UI initialization

                        initializeMainUI()

                    } else if (result.threats.isNotEmpty()) {

                        // Threats detected

                        Timber.e("Security threats detected - ensuring app blocking")

                        if (!BuildConfig.DEBUG) {

                            blockApp()

                            return@withContext

                        }

                        // In debug mode, continue with UI initialization

                        initializeMainUI()

                    } else {

                        // Security check passed - safe to initialize main UI

                        initializeMainUI()

                    }

                    // Allow splash screen to dismiss

                    isSecurityCheckPending = false

                }

            } catch (e: Exception) {

                // Exception during security check - treat as failure in production

                Timber.e(e, "Security check failed during app start - blocking app in production")

                withContext(Dispatchers.Main) {

                    if (!BuildConfig.DEBUG) {

                        blockApp()

                    } else {

                        // In debug mode, continue with UI initialization

                        initializeMainUI()

                    }

                    isSecurityCheckPending = false

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
        // Prevent multiple initializations
        if (isMainUIInitialized) {
            Timber.w("Main UI already initialized, skipping")
            return
        }

        try {
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

            // Set flag only after all initialization completes successfully
            isMainUIInitialized = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize main UI")
            // Clean up partial state
            binding = null
            // Don't set flag on error so retry is possible
            // Finish activity to prevent broken UI state
            finish()
        }
    }



    private fun setupNavigation() {

        val navController = findNavController(R.id.nav_host_fragment_content_main)



        // Bottom Navigation mit NavController verbinden

        binding?.bottomNavigation?.setupWithNavController(navController)



        // Handle navigation from intent extras (e.g., USB device attached)
        handleNavigateIntent(intent)

        Timber.d("Navigation setup completed")

    }
    
    /**
     * Handles navigation from intent extras.
     * Extracted to a separate method to be called from both setupNavigation() and onNewIntent().
     */
    private fun handleNavigateIntent(intent: Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to") ?: return
        
        // Skip if this navigation target was already handled (null-safe string comparison)
        if (navigateTo == lastHandledNavigateTo) {
            Timber.d("Navigation target already handled: $navigateTo, skipping duplicate navigation")
            return
        }
        
        try {
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            val navId = resources.getIdentifier(navigateTo, "id", packageName)
            
            if (navId == 0) {
                Timber.w("Navigation destination not found: $navigateTo")
                return
            }
            
            Timber.d("Navigating to: $navigateTo")
            navController.navigate(navId)
            
            // Mark this navigation target as handled to prevent duplicate navigations
            lastHandledNavigateTo = navigateTo
        } catch (e: Exception) {
            Timber.e(e, "Error navigating to: $navigateTo")
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle navigation from new intent (e.g., when activity is singleTop)
        if (isMainUIInitialized) {
            handleNavigateIntent(intent)
        }
    }



    private fun setupModuleDiscovery() {

        Timber.d("Starting module discovery from ModuleCatalog")



        // Register all core modules (always active)

        com.ble1st.connectias.core.module.ModuleCatalog.CORE_MODULES.forEach { metadata ->

            moduleRegistry.registerFromMetadata(metadata, isActive = true)

            Timber.d("Core module registered: ${metadata.name} (${metadata.id})")

        }



        // Register available optional modules

        val availableModules = com.ble1st.connectias.core.module.ModuleCatalog.getAvailableModules()

        availableModules.forEach { metadata ->

            if (!metadata.isCore) {

                moduleRegistry.registerFromMetadata(metadata, isActive = true)

                Timber.d("Optional module registered: ${metadata.name} (${metadata.id})")

            }

        }



        // Log summary

        val activeModules = moduleRegistry.getActiveModules()

        Timber.d("Module discovery completed: ${activeModules.size} active modules")

        activeModules.forEach { module ->

            val metadata = com.ble1st.connectias.core.module.ModuleCatalog.findById(module.id)

            val category = metadata?.category?.name ?: "UNKNOWN"

            Timber.d("  - ${module.name} (${module.id}) v${module.version} [${category}]")

        }

    }



    /**

     * Blocks the app when security checks fail or threats are detected.

     * Navigates to SecurityBlockedActivity and clears the task.

     */

    private fun blockApp() {

        Timber.e("Blocking app due to security failure")

        val intent = Intent(this, SecurityBlockedActivity::class.java)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)

        finish()

    }

}
