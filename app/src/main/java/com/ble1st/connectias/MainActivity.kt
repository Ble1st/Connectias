package com.ble1st.connectias

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.common.ui.theme.ThemeStyle
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.core.services.SecurityService
import com.ble1st.connectias.core.settings.SettingsRepository
import com.ble1st.connectias.databinding.ActivityMainBinding
import com.ble1st.connectias.plugin.PluginFragmentWrapper
import com.ble1st.connectias.plugin.PluginManagerSandbox
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var isSecurityCheckPending = true
    private var isMainUIInitialized = false
    private var lastHandledNavigateTo: String? = null

    @Inject
    lateinit var moduleRegistry: ModuleRegistry

    @Inject
    lateinit var securityService: SecurityService

    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var pluginManager: PluginManagerSandbox

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Splash Screen before super.onCreate()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep the splash screen visible until security checks are complete
        splashScreen.setKeepOnScreenCondition {
            isSecurityCheckPending
        }

        // Perform security checks before initializing main UI
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(5000) {
                    securityService.performSecurityCheck()
                }

                withContext(Dispatchers.Main) {
                    if (result == null) {
                        Timber.e("Security check timed out - blocking app in production")
                        if (!BuildConfig.DEBUG) {
                            blockApp()
                            return@withContext
                        }
                        initializeMainUI()
                    } else {
                        // SecurityService now handles termination internally
                        initializeMainUI()
                    }
                    isSecurityCheckPending = false
                }
            } catch (e: Exception) {
                Timber.e(e, "Security check failed during app start - blocking app in production")
                withContext(Dispatchers.Main) {
                    if (!BuildConfig.DEBUG) {
                        blockApp()
                    } else {
                        initializeMainUI()
                    }
                    isSecurityCheckPending = false
                }
            }
        }
    }

    private fun initializeMainUI() {
        if (isMainUIInitialized) {
            Timber.w("Main UI already initialized, skipping")
            return
        }

        try {
            enableEdgeToEdge()
            
            // Use traditional setContentView for proper Fragment navigation
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Edge-to-Edge: Don't apply padding to root - let content extend under system bars
            // Each screen/fragment handles its own insets for proper fullscreen experience
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                // Consume insets at root level - children will handle their own insets
                insets
            }
            
            // Add FAB overlay as ComposeView
            addFabOverlay()

            // Module Discovery
            setupModuleDiscovery()
        setupPluginSystem()

            // Note: Log rotation moved to Domain Layer
            // Use CleanupOldDataUseCase for log cleanup
            
            // Handle initial intent
            window.decorView.post {
                setupNavigation()
            }

            isMainUIInitialized = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize main UI")
            finish()
        }
    }
    
    private fun addFabOverlay() {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // Observe theme, theme style, and dynamic color changes reactively
                // Use default values as initial state - Flow will emit actual value immediately
                // This avoids synchronous SharedPreferences access on main thread (StrictMode violation)
                val theme by settingsRepository.observeTheme().collectAsState(initial = "system")
                val themeStyleString by settingsRepository.observeThemeStyle().collectAsState(initial = "standard")
                val dynamicColor by settingsRepository.observeDynamicColor().collectAsState(initial = true)
                val themeStyle = remember(themeStyleString) { ThemeStyle.fromString(themeStyleString) }
                
                ConnectiasTheme(
                    themePreference = theme,
                    themeStyle = themeStyle,
                    dynamicColor = dynamicColor
                ) {
                    val navController = try {
                        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
                        navHostFragment?.navController
                    } catch (e: Exception) {
                        null
                    }
                    FabWithBottomSheet(
                        navController = navController,
                        pluginManager = pluginManager,
                        moduleRegistry = moduleRegistry,
                        onFeatureSelected = { navId ->
                            // If a plugin fragment is active, navigate back to dashboard first
                            if (isPluginFragmentActive()) {
                                navigateToDashboard(clearBackStack = true)
                            }
                            navigateToFeature(navId)
                        },
                        onPluginSelected = { pluginId ->
                            // If a plugin fragment is active, navigate back to dashboard first
                            if (isPluginFragmentActive()) {
                                navigateToDashboard(clearBackStack = true)
                            }
                            navigateToPlugin(pluginId)
                        }
                    )
                }
            }
        }
        
        // Add ComposeView as overlay to the CoordinatorLayout
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        binding.root.addView(composeView, params)
    }
    
    private fun navigateToFeature(navId: Int) {
        try {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
            navHostFragment?.navController?.navigate(navId)
            Timber.d("Navigated to feature with id: $navId")
        } catch (e: Exception) {
            Timber.e(e, "Navigation failed for id: $navId")
        }
    }
    
    /**
     * Navigates to a plugin fragment by loading it dynamically.
     * This allows plugins to be displayed without being in the static navigation graph.
     */
    fun navigateToPlugin(pluginId: String) {
        try {
            val pluginInfo = pluginManager.getPlugin(pluginId)
            if (pluginInfo == null) {
                Timber.w("Plugin not found: $pluginId")
                return
            }
            
            // Check if plugin is enabled (not just loaded or disabled)
            if (pluginInfo.state != PluginManagerSandbox.PluginState.ENABLED) {
                Timber.w("Plugin is not enabled: $pluginId (state: ${pluginInfo.state})")
                return
            }
            
            // Create fragment with critical error callback
            val fragment = pluginManager.createPluginFragment(
                pluginId = pluginId,
                onCriticalError = {
                    // Watchdog detected critical error - navigate to dashboard
                    Timber.w("[WATCHDOG] Critical error callback triggered, navigating to dashboard")
                    runOnUiThread {
                        navigateToDashboard(clearBackStack = true)
                    }
                }
            )
            if (fragment == null) {
                Timber.e("Failed to create fragment for plugin: $pluginId")
                return
            }
            
            // Replace current fragment with plugin fragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_content_main, fragment)
                .addToBackStack("plugin_$pluginId")
                .commit()
            
            Timber.i("Navigated to plugin: ${pluginInfo.metadata.pluginName}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to navigate to plugin: $pluginId")
        }
    }
    
    /**
     * Immediately "kills" a plugin fragment by removing it from the FragmentManager.
     * Similar to Linux kill command - forcefully terminates the plugin fragment.
     * 
     * This function:
     * 1. Finds and removes the plugin fragment from FragmentManager
     * 2. Removes the fragment's view from the view hierarchy
     * 3. Deactivates the plugin
     * 4. Navigates back to dashboard
     * 
     * @param pluginId The ID of the plugin to kill
     */
    fun killPluginFragment(pluginId: String) {
        try {
            Timber.w("Killing plugin fragment: $pluginId")
            
            // Step 1: Find the fragment
            val fragmentTag = "plugin_$pluginId"
            var fragment = supportFragmentManager.findFragmentByTag(fragmentTag)
            
            // If not found by tag, search through all fragments
            if (fragment == null) {
                fragment = supportFragmentManager.fragments.firstOrNull { 
                    it.javaClass.simpleName.contains("Plugin") || it is PluginFragmentWrapper
                }
            }
            
            // Step 2: CRITICAL - Remove the view from view hierarchy FIRST
            // This immediately hides the plugin UI, even before fragment is removed
            fragment?.view?.let { view ->
                try {
                    val parent = view.parent as? ViewGroup
                    parent?.removeView(view)
                    Timber.d("Removed plugin view from view hierarchy: $pluginId")
                } catch (e: Exception) {
                    Timber.w(e, "Could not remove view from hierarchy: ${e.message}")
                }
            }
            
            // Step 3: Immediately show dashboard by navigating
            try {
                // Immediately navigate to dashboard using NavController
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
                navHostFragment?.navController?.let { navController ->
                    navController.popBackStack(R.id.nav_dashboard, false)
                    if (navController.currentDestination?.id != R.id.nav_dashboard) {
                        navController.navigate(R.id.nav_dashboard)
                    }
                    Timber.d("Immediately navigated to dashboard")
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not immediately show dashboard: ${e.message}")
            }
            
            // Step 4: Remove fragment from FragmentManager
            if (fragment != null) {
                try {
                    supportFragmentManager.beginTransaction()
                        .remove(fragment)
                        .commitNowAllowingStateLoss() // Force immediate removal
                    Timber.d("Removed plugin fragment from FragmentManager: $pluginId")
                } catch (e: Exception) {
                    Timber.w(e, "Could not remove fragment: ${e.message}")
                }
            }
            
            // Step 5: Remove from back stack
            try {
                supportFragmentManager.popBackStack(fragmentTag, android.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            } catch (e: Exception) {
                Timber.d("Could not remove from back stack: ${e.message}")
            }
            
            // Step 6: Deactivate the plugin
            try {
                val pluginInfo = pluginManager.getPlugin(pluginId)
                if (pluginInfo != null) {
                    pluginInfo.state = PluginManagerSandbox.PluginState.ERROR
                    Timber.d("Set plugin state to ERROR: $pluginId")
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not deactivate plugin: ${e.message}")
            }
            
            // Step 7: Ensure dashboard is shown (fallback)
            navigateToDashboard(clearBackStack = true)
            
            Timber.i("Successfully killed plugin fragment: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to kill plugin fragment: $pluginId")
            // Fallback: Just navigate to dashboard
            navigateToDashboard(clearBackStack = true)
        }
    }
    
    /**
     * Checks if a plugin fragment is currently active.
     * @return true if a plugin fragment is in the back stack or currently displayed
     */
    private fun isPluginFragmentActive(): Boolean {
        return try {
            // Check if there are any entries in the back stack with "plugin_" tag
            val backStackEntryCount = supportFragmentManager.backStackEntryCount
            if (backStackEntryCount > 0) {
                val lastEntry = supportFragmentManager.getBackStackEntryAt(backStackEntryCount - 1)
                if (lastEntry.name?.startsWith("plugin_") == true) {
                    return true
                }
            }
            
            // Also check if there's a PluginFragmentWrapper currently visible
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
            val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
            currentFragment is PluginFragmentWrapper
        } catch (e: Exception) {
            Timber.w(e, "Failed to check if plugin fragment is active")
            false
        }
    }
    
    /**
     * Navigates back to the dashboard.
     * This is used to recover from plugin exceptions by returning to a safe state.
     * 
     * @param clearBackStack If true, clears the back stack to prevent returning to the plugin
     */
    fun navigateToDashboard(clearBackStack: Boolean = false) {
        try {
            // If a plugin fragment is active, remove it from the back stack first
            if (isPluginFragmentActive()) {
                val backStackEntryCount = supportFragmentManager.backStackEntryCount
                if (backStackEntryCount > 0) {
                    // Pop all plugin fragments from the back stack
                    for (i in 0 until backStackEntryCount) {
                        val entry = supportFragmentManager.getBackStackEntryAt(backStackEntryCount - 1 - i)
                        if (entry.name?.startsWith("plugin_") == true) {
                            supportFragmentManager.popBackStackImmediate(entry.id, 0)
                        }
                    }
                }
            }
            
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
            val navController = navHostFragment?.navController
            
            if (navController != null) {
                if (clearBackStack) {
                    // Clear back stack and navigate to dashboard
                    navController.popBackStack(R.id.nav_dashboard, false)
                    // If not already at dashboard, navigate to it
                    if (navController.currentDestination?.id != R.id.nav_dashboard) {
                        navController.navigate(R.id.nav_dashboard)
                    }
                } else {
                    // Just navigate to dashboard, keeping back stack
                    navController.navigate(R.id.nav_dashboard)
                }
                Timber.i("Navigated to dashboard (clearBackStack=$clearBackStack)")
            } else {
                // Fallback: Use fragment transaction if NavController is not available
                Timber.w("NavController not available, using fragment transaction fallback")
                supportFragmentManager.popBackStack(null, android.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to navigate to dashboard")
        }
    }

    private fun setupNavigation() {
        try {
            handleNavigateIntent(intent)
            Timber.d("Navigation setup completed")
        } catch (e: Exception) {
            Timber.w("Navigation setup deferred or failed: ${e.message}")
        }
    }
    
    private fun handleNavigateIntent(intent: Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to") ?: return
        
        if (navigateTo == lastHandledNavigateTo) {
            Timber.d("Navigation target already handled: $navigateTo, skipping duplicate navigation")
            return
        }
        
        try {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
            val navController = navHostFragment?.navController
            if (navController == null) {
                Timber.w("NavController not available")
                return
            }
            
            val navId = getNavIdByName(navigateTo)
            
            if (navId == null) {
                Timber.w("Navigation destination not found: $navigateTo")
                return
            }
            
            Timber.d("Navigating to: $navigateTo")
            navController.navigate(navId)
            lastHandledNavigateTo = navigateTo
        } catch (e: Exception) {
            Timber.e(e, "Error navigating to: $navigateTo")
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isMainUIInitialized) {
            handleNavigateIntent(intent)
        }
    }

    private fun setupModuleDiscovery() {
        Timber.d("Starting module discovery from ModuleCatalog")
        com.ble1st.connectias.core.module.ModuleCatalog.CORE_MODULES.forEach { metadata ->
            moduleRegistry.registerFromMetadata(metadata, isActive = true)
            Timber.d("Core module registered: ${metadata.name} (${metadata.id})")
        }

        val availableModules = com.ble1st.connectias.core.module.ModuleCatalog.getAvailableModules()
        availableModules.forEach { metadata ->
            if (!metadata.isCore) {
                moduleRegistry.registerFromMetadata(metadata, isActive = true)
                Timber.d("Optional module registered: ${metadata.name} (${metadata.id})")
            }
        }

        val activeModules = moduleRegistry.getActiveModules()
        Timber.d("Module discovery completed: ${activeModules.size} active modules")
    }
    
    private fun setupPluginSystem() {
        lifecycleScope.launch {
            try {
                Timber.d("Initializing plugin system...")
                
                val initResult = pluginManager.initialize()
                
                initResult.onSuccess { loadedMetadata ->
                    Timber.i("Plugin system initialized with ${loadedMetadata.size} plugins")
                    
                    val loadedPlugins = pluginManager.getLoadedPlugins()
                    loadedPlugins.forEach { pluginInfo ->
                        val enableResult = pluginManager.enablePlugin(pluginInfo.pluginId)
                        
                        enableResult.onSuccess {
                            Timber.i("Plugin enabled: ${pluginInfo.metadata.pluginName}")
                            
                            withContext(Dispatchers.Main) {
                                val moduleInfo = com.ble1st.connectias.core.module.ModuleInfo(
                                    id = pluginInfo.metadata.pluginId,
                                    name = pluginInfo.metadata.pluginName,
                                    version = pluginInfo.metadata.version,
                                    isActive = true
                                )
                                moduleRegistry.registerModule(moduleInfo)
                            }
                        }
                    }
                    
                    Timber.i("Plugin system setup completed")
                }.onFailure { error ->
                    Timber.e(error, "Failed to initialize plugin system")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception during plugin system setup")
            }
        }
    }

    private fun blockApp() {
        Timber.e("Blocking app due to security failure")
        val intent = Intent(this, SecurityBlockedActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FabWithBottomSheet(
    navController: NavController?,
    pluginManager: com.ble1st.connectias.plugin.PluginManagerSandbox,
    moduleRegistry: com.ble1st.connectias.core.module.ModuleRegistry,
    onFeatureSelected: (Int) -> Unit,
    onPluginSelected: (String) -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    // Observe current navigation destination
    val currentBackStackEntry by navController?.currentBackStackEntryFlow?.collectAsState(
        initial = navController.currentBackStackEntry
    ) ?: remember { mutableStateOf(null) }
    // FAB is always visible now that DVD features are removed
    val shouldShowFab = true

    // Auto-hide FAB when scrolling: Create scroll state for FeatureList
    val featureListScrollState = rememberLazyListState()
    val isScrolling = remember {
        derivedStateOf {
            featureListScrollState.isScrollInProgress
        }
    }
    
    // Determine if FAB should be visible (hide during DVD playback, when bottom sheet is open, or when scrolling)
    val fabVisible = shouldShowFab && !sheetState.isVisible && !isScrolling.value
    
    // P0: Show bottom sheet after it's rendered to avoid composition disposal issues
    LaunchedEffect(showBottomSheet) {
        if (showBottomSheet && !sheetState.isVisible) {
            // Small delay to ensure the bottom sheet is rendered before showing
            delay(50)
            try {
                sheetState.show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellation is expected when composition is disposed, ignore it
                Timber.d("Bottom sheet show() was cancelled (composition disposed)")
                showBottomSheet = false
            } catch (e: Exception) {
                Timber.e(e, "Failed to show bottom sheet")
                showBottomSheet = false
            }
        }
    }
    
    // P0: Synchronize showBottomSheet with sheetState.isVisible
    LaunchedEffect(sheetState.isVisible) {
        if (!sheetState.isVisible && showBottomSheet) {
            showBottomSheet = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter
    ) {
        // P1: FAB with animations and haptic feedback
        // Auto-hide: Hide when scrolling, bottom sheet open, or DVD playback
        AnimatedVisibility(
            visible = fabVisible,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(
                initialScale = 0.8f,
                animationSpec = tween(200)
            ),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(150)
            )
        ) {
            LargeFloatingActionButton(
                onClick = {
                    // P1: Haptic feedback on click
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // P0: Set flag first to render the bottom sheet, then show it
                    showBottomSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Icon(
                    Icons.Rounded.Apps,
                    contentDescription = "Open features menu",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    try {
                        sheetState.hide()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to hide bottom sheet")
                        showBottomSheet = false
                    }
                }
            },
            sheetState = sheetState
        ) {
            FeatureList(
                scrollState = featureListScrollState,
                pluginManager = pluginManager,
                moduleRegistry = moduleRegistry,
                onFeatureClick = { navId ->
                    scope.launch {
                        try {
                            // P0: Proper error handling for sheet closing
                            sheetState.hide()
                            // Wait until sheet is actually hidden
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                            
                            // P0: Error handling for navigation
                            try {
                                onFeatureSelected(navId)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to navigate to feature with id: $navId")
                                // Sheet is already closed, error is logged
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to close bottom sheet")
                            showBottomSheet = false
                        }
                    }
                },
                onPluginClick = { pluginId ->
                    scope.launch {
                        try {
                            // P0: Proper error handling for sheet closing
                            sheetState.hide()
                            // Wait until sheet is actually hidden
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                            
                            // P0: Error handling for plugin navigation
                            try {
                                onPluginSelected(pluginId)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to navigate to plugin: $pluginId")
                                // Sheet is already closed, error is logged
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to close bottom sheet")
                            showBottomSheet = false
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeatureList(
    scrollState: LazyListState,
    pluginManager: com.ble1st.connectias.plugin.PluginManagerSandbox,
    moduleRegistry: com.ble1st.connectias.core.module.ModuleRegistry,
    onFeatureClick: (Int) -> Unit,
    onPluginClick: (String) -> Unit
) {
    val context = LocalContext.current
    // Observe module registry changes reactively
    val allModules by moduleRegistry.modulesFlow.collectAsState()
    val categories = remember(allModules) {
        resolveFeatureCategories(pluginManager, allModules)
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "All Features",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
        )
        
        LazyColumn(
            state = scrollState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            categories.forEach { category ->
                if (category.features.isNotEmpty()) {
                    stickyHeader {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    
                    items(category.features) { feature ->
                        if (feature.isPlugin && feature.pluginId != null) {
                            // Plugin feature - use plugin navigation
                            PluginFeatureRow(
                                name = feature.name,
                                icon = feature.icon,
                                onClick = { onPluginClick(feature.pluginId) }
                            )
                        } else if (feature.resolvedId != null) {
                            // Regular feature - use normal navigation
                            FeatureRow(feature, onClick = { onFeatureClick(feature.resolvedId!!) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureRow(feature: ResolvedFeature, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                feature.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = feature.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun PluginFeatureRow(
    name: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Plugin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// Data classes for definition (String IDs) and resolved state (Int IDs)
data class FeatureCategoryDef(val title: String, val features: List<FeatureDef>)
data class FeatureDef(val name: String, val icon: ImageVector, val navIdName: String)

data class ResolvedFeatureCategory(val title: String, val features: List<ResolvedFeature>)
data class ResolvedFeature(
    val name: String, 
    val icon: ImageVector, 
    val resolvedId: Int? = null,  // Nav ID for regular features
    val isPlugin: Boolean = false,
    val pluginId: String? = null  // Plugin ID for plugin features
)

/**
 * Maps navigation ID names (strings) to their corresponding R.id values.
 * This replaces getIdentifier() calls with compile-time verified resource IDs.
 */
private fun getNavIdByName(navIdName: String): Int? {
    return when (navIdName) {
        "nav_settings" -> R.id.nav_settings
        "nav_log_viewer" -> R.id.nav_log_viewer
        "nav_plugin_management" -> R.id.nav_plugin_management
        else -> null
    }
}

fun resolveFeatureCategories(
    pluginManager: com.ble1st.connectias.plugin.PluginManagerSandbox,
    allModules: List<com.ble1st.connectias.core.module.ModuleInfo>
): List<ResolvedFeatureCategory> {
    val definitions = getFeatureDefinitions()
    
    // Get plugin modules - filter for active plugins only
    val pluginModules = allModules
        .filter { moduleInfo ->
            moduleInfo.isActive && pluginManager.getPlugin(moduleInfo.id) != null
        }
    
    // Convert plugin modules to ResolvedFeature
    val pluginFeatures = pluginModules.map { moduleInfo ->
        ResolvedFeature(
            name = moduleInfo.name,
            icon = Icons.Default.Extension,
            resolvedId = null,
            isPlugin = true,
            pluginId = moduleInfo.id
        )
    }
    
    val regularCategories = definitions.mapNotNull { category ->
        val resolvedFeatures = category.features.mapNotNull { featureDef ->
            // Check if navigation ID exists
            val id = getNavIdByName(featureDef.navIdName) ?: return@mapNotNull null

            ResolvedFeature(featureDef.name, featureDef.icon, id, isPlugin = false, pluginId = null)
        }
        
        if (resolvedFeatures.isNotEmpty()) {
            ResolvedFeatureCategory(category.title, resolvedFeatures)
        } else {
            null
        }
    }
    
    // Add plugins to Extensions category or create new category
    val extensionsCategory = regularCategories.find { it.title == "Extensions" }
    val finalCategories = if (pluginFeatures.isNotEmpty()) {
        if (extensionsCategory != null) {
            // Add plugins to existing Extensions category
            regularCategories.map { category ->
                if (category.title == "Extensions") {
                    ResolvedFeatureCategory(
                        category.title,
                        category.features + pluginFeatures
                    )
                } else {
                    category
                }
            }
        } else {
            // Create new Extensions category for plugins
            regularCategories + ResolvedFeatureCategory("Extensions", pluginFeatures)
        }
    } else {
        regularCategories
    }
    
    return finalCategories
}

fun getFeatureDefinitions(): List<FeatureCategoryDef> {
    return listOf(
        FeatureCategoryDef("System & Security", listOf(
            FeatureDef("Settings", Icons.Default.Settings, "nav_settings"),
            FeatureDef("Log Viewer", Icons.Default.Description, "nav_log_viewer")
        )),
        FeatureCategoryDef("Extensions", listOf(
            FeatureDef("Plugin Management", Icons.Rounded.Apps, "nav_plugin_management")
        ))
    )
}
