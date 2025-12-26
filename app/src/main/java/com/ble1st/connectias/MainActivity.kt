package com.ble1st.connectias
import androidx.compose.material.icons.automirrored.filled.Note

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Usb
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
import androidx.compose.runtime.DisposableEffect
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
import com.ble1st.connectias.core.services.LoggingService
import com.ble1st.connectias.core.services.SecurityService
import com.ble1st.connectias.core.settings.SettingsRepository
import com.ble1st.connectias.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.absoluteValue

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var isSecurityCheckPending = true
    private var isMainUIInitialized = false
    private var lastHandledNavigateTo: String? = null

    @Inject
    lateinit var moduleRegistry: ModuleRegistry

    @Inject
    lateinit var loggingService: LoggingService

    @Inject
    lateinit var securityService: SecurityService

    @Inject
    lateinit var settingsRepository: SettingsRepository

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
                    securityService.performSecurityCheckWithTermination()
                }

                withContext(Dispatchers.Main) {
                    if (result == null) {
                        Timber.e("Security check timed out - blocking app in production")
                        if (!BuildConfig.DEBUG) {
                            blockApp()
                            return@withContext
                        }
                        initializeMainUI()
                    } else if (result.threats.isNotEmpty()) {
                        Timber.e("Security threats detected - ensuring app blocking")
                        if (!BuildConfig.DEBUG) {
                            blockApp()
                            return@withContext
                        }
                        initializeMainUI()
                    } else {
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

            // Log rotation
            lifecycleScope.launch {
                try {
                    loggingService.rotateLogs()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to perform log rotation on app start")
                }
            }
            
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
                val theme by settingsRepository.observeTheme().collectAsState(initial = settingsRepository.getTheme())
                val themeStyleString by settingsRepository.observeThemeStyle().collectAsState(initial = settingsRepository.getThemeStyle())
                val dynamicColor by settingsRepository.observeDynamicColor().collectAsState(initial = settingsRepository.getDynamicColor())
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
                        onFeatureSelected = { navId ->
                            navigateToFeature(navId)
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
    onFeatureSelected: (Int) -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    // P1: Cache resource IDs once for better performance
    val context = LocalContext.current
    data class NavIds(
        val dvdPlayer: Int,
        val dvdCdDetail: Int
    )
    val navIds = remember {
        NavIds(
            dvdPlayer = R.id.nav_dvd_player,
            dvdCdDetail = R.id.nav_dvd_cd_detail
        )
    }
    
    // Observe current navigation destination to hide FAB during DVD playback
    val currentBackStackEntry by navController?.currentBackStackEntryFlow?.collectAsState(
        initial = navController.currentBackStackEntry
    ) ?: remember { mutableStateOf(null) }
    val currentDestinationId = currentBackStackEntry?.destination?.id
    val shouldShowFab = currentDestinationId != navIds.dvdPlayer && currentDestinationId != navIds.dvdCdDetail

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
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeatureList(
    scrollState: LazyListState,
    onFeatureClick: (Int) -> Unit
) {
    val context = LocalContext.current
    // Resolve features dynamically on each composition to ensure only existing features are shown
    // This ensures that features are filtered based on what's actually available in the navigation graph
    // The function is fast enough to run on each composition without performance issues
    val categories = resolveFeatureCategories()

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
                        FeatureRow(feature, onClick = { onFeatureClick(feature.resolvedId) })
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

// Data classes for definition (String IDs) and resolved state (Int IDs)
data class FeatureCategoryDef(val title: String, val features: List<FeatureDef>)
data class FeatureDef(val name: String, val icon: ImageVector, val navIdName: String)

data class ResolvedFeatureCategory(val title: String, val features: List<ResolvedFeature>)
data class ResolvedFeature(val name: String, val icon: ImageVector, val resolvedId: Int)

/**
 * Maps navigation IDs to their corresponding Fragment class names.
 * This allows checking if the Fragment class actually exists at runtime.
 */
private fun getFragmentClassNameForNavId(navIdName: String): String? {
    return when (navIdName) {
        // Settings
        "nav_settings" -> "com.ble1st.connectias.feature.settings.ui.SettingsFragment"

        // System tools
        "nav_log_viewer" -> "com.ble1st.connectias.core.logging.ui.LogViewerFragment"

        // Secure notes
        "nav_secure_notes" -> "com.ble1st.connectias.feature.securenotes.ui.SecureNotesFragment"
        
        // Scanner feature
        "nav_scanner" -> "com.ble1st.connectias.feature.scanner.ui.ScannerFragment"

        // DVD features
        "nav_dvd_player" -> "com.ble1st.connectias.feature.dvd.ui.DvdPlayerFragment"
        "nav_dvd_cd_detail" -> "com.ble1st.connectias.feature.dvd.ui.DvdCdDetailFragment"
        "nav_bluetooth_scanner" -> "com.ble1st.connectias.feature.bluetooth.ui.BluetoothScannerFragment"
        "nav_network_tools" -> "com.ble1st.connectias.feature.network.ui.NetworkToolsFragment"
        "nav_dns_tools" -> "com.ble1st.connectias.feature.dnstools.ui.DnsToolsFragment"
        "nav_barcode_tools" -> "com.ble1st.connectias.feature.barcode.ui.BarcodeFragment"
        "nav_calendar" -> "com.ble1st.connectias.feature.calendar.ui.CalendarFragment"
        "nav_ntp" -> "com.ble1st.connectias.feature.ntp.ui.NtpFragment"
        "nav_ssh" -> "com.ble1st.connectias.feature.ssh.ui.SshFragment"
        "nav_password" -> "com.ble1st.connectias.feature.password.ui.PasswordFragment"
        "nav_gps" -> "com.ble1st.connectias.feature.satellite.ui.SatelliteFragment"
        "nav_device_info" -> "com.ble1st.connectias.feature.deviceinfo.ui.DeviceInfoFragment"
        else -> null
    }
}

/**
 * Maps navigation ID names (strings) to their corresponding R.id values.
 * This replaces getIdentifier() calls with compile-time verified resource IDs.
 */
private fun getNavIdByName(navIdName: String): Int? {
    return when (navIdName) {
        "nav_settings" -> R.id.nav_settings
        "nav_log_viewer" -> R.id.nav_log_viewer
        "nav_secure_notes" -> R.id.nav_secure_notes
        "nav_scanner" -> R.id.nav_scanner
        "nav_dvd_player" -> R.id.nav_dvd_player
        "nav_dvd_cd_detail" -> R.id.nav_dvd_cd_detail
        "nav_bluetooth_scanner" -> R.id.nav_bluetooth_scanner
        "nav_network_tools" -> R.id.nav_network_tools
        "nav_dns_tools" -> R.id.nav_dns_tools
        "nav_barcode_tools" -> R.id.nav_barcode_tools
        "nav_calendar" -> R.id.nav_calendar
        "nav_ntp" -> R.id.nav_ntp
        "nav_ssh" -> R.id.nav_ssh
        "nav_password" -> R.id.nav_password
        "nav_gps" -> R.id.nav_gps
        "nav_device_info" -> R.id.nav_device_info
        else -> null
    }
}

/**
 * Checks if a Fragment class exists at runtime by attempting to load it.
 */
private fun isFragmentClassAvailable(className: String?): Boolean {
    if (className == null) return false
    return try {
        Class.forName(className)
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}

fun resolveFeatureCategories(): List<ResolvedFeatureCategory> {
    val definitions = getFeatureDefinitions()
    
    return definitions.mapNotNull { category ->
        val resolvedFeatures = category.features.mapNotNull { featureDef ->
            // First check if navigation ID exists
            val id = getNavIdByName(featureDef.navIdName)
            if (id == null) {
                Timber.d("Feature filtered (nav ID not found): ${featureDef.name} (${featureDef.navIdName})")
                return@mapNotNull null
            }
            
            // Then check if Fragment class exists
            val fragmentClassName = getFragmentClassNameForNavId(featureDef.navIdName)
            if (!isFragmentClassAvailable(fragmentClassName)) {
                Timber.d("Feature filtered (Fragment class not available): ${featureDef.name} (${featureDef.navIdName}) - class: $fragmentClassName")
                return@mapNotNull null
            }
            
            ResolvedFeature(featureDef.name, featureDef.icon, id)
        }
        
        if (resolvedFeatures.isNotEmpty()) {
            ResolvedFeatureCategory(category.title, resolvedFeatures)
        } else {
            null
        }
    }
}

fun getFeatureDefinitions(): List<FeatureCategoryDef> {
    return listOf(
        FeatureCategoryDef("System & Security", listOf(
            FeatureDef("Settings", Icons.Default.Settings, "nav_settings"),
            FeatureDef("Log Viewer", Icons.Default.Description, "nav_log_viewer")
        )),
        FeatureCategoryDef("Notes", listOf(
            FeatureDef("Secure Notes", Icons.AutoMirrored.Filled.Note, "nav_secure_notes")
        )),
        FeatureCategoryDef("Connectivity", listOf(
            FeatureDef("Bluetooth Scanner", Icons.Default.Bluetooth, "nav_bluetooth_scanner"),
            FeatureDef("Network Tools", Icons.Default.Router, "nav_network_tools"),
            FeatureDef("DNS Tools", Icons.Default.Dns, "nav_dns_tools"),
            FeatureDef("NTP Checker", Icons.Default.History, "nav_ntp"),
            FeatureDef("SSH / SCP", Icons.Default.Lock, "nav_ssh")
        )),
        FeatureCategoryDef("Media", listOf(
            FeatureDef("DVD Player", Icons.Default.Album, "nav_dvd_cd_detail")
        )),
        FeatureCategoryDef("Utilities", listOf(
            FeatureDef("Barcode & QR", Icons.Default.QrCode, "nav_barcode_tools"),
            FeatureDef("Document Scanner", Icons.Default.Scanner, "nav_scanner"),
            FeatureDef("Password Tools", Icons.Default.Password, "nav_password")
        )),
        FeatureCategoryDef("Productivity", listOf(
            FeatureDef("Kalender", Icons.Default.Timeline, "nav_calendar")
        )),
        FeatureCategoryDef("Device", listOf(
            FeatureDef("Device Info", Icons.Default.PermDeviceInformation, "nav_device_info"),
            FeatureDef("GPS Satellites", Icons.Default.Sensors, "nav_gps")
        ))
    )
}
