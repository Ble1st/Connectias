package com.ble1st.connectias

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.common.ui.theme.ThemeStyle
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.core.services.LoggingService
import com.ble1st.connectias.core.services.SecurityService
import com.ble1st.connectias.core.settings.SettingsRepository
import com.ble1st.connectias.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
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
                    FabWithBottomSheet(
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
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            navController.navigate(navId)
            Timber.d("Navigated to feature with id: $navId")
        } catch (e: Exception) {
            Timber.e(e, "Navigation failed for id: $navId")
        }
    }

    private fun setupNavigation() {
        try {
            val navController = findNavController(R.id.nav_host_fragment_content_main)
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
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            val navId = resources.getIdentifier(navigateTo, "id", packageName)
            
            if (navId == 0) {
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
fun FabWithBottomSheet(onFeatureSelected: (Int) -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter
    ) {
        // FAB at bottom center with proper inset handling
        LargeFloatingActionButton(
            onClick = { showBottomSheet = true },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Icon(
                Icons.Rounded.Apps,
                contentDescription = "All Features",
                modifier = Modifier.size(32.dp)
            )
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            FeatureList(
                onFeatureClick = { navId ->
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                        onFeatureSelected(navId)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeatureList(onFeatureClick: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Resolve features dynamically on each composition to ensure only existing features are shown
    // This ensures that features are filtered based on what's actually available in the navigation graph
    // The function is fast enough to run on each composition without performance issues
    val categories = resolveFeatureCategories(context)

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "All Features",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
        )
        
        LazyColumn(
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
        // Core modules (always available)
        "nav_security_dashboard" -> "com.ble1st.connectias.feature.security.ui.SecurityDashboardFragment"
        "nav_settings" -> "com.ble1st.connectias.feature.settings.ui.SettingsFragment"
        
        // Security features
        "nav_certificate_analyzer" -> "com.ble1st.connectias.feature.security.certificate.CertificateAnalyzerFragment"
        "nav_password_strength" -> "com.ble1st.connectias.feature.security.password.PasswordStrengthFragment"
        "nav_encryption_tools" -> "com.ble1st.connectias.feature.security.encryption.EncryptionFragment"
        "nav_firewall_analyzer" -> "com.ble1st.connectias.feature.security.firewall.FirewallAnalyzerFragment"
        "nav_wifi_security_auditor" -> "com.ble1st.connectias.feature.security.wifi.WifiSecurityAuditorFragment"
        "nav_vulnerability_scanner" -> "com.ble1st.connectias.feature.security.scanner.ui.VulnerabilityScannerFragment"
        "nav_hash_tool" -> "com.ble1st.connectias.feature.security.crypto.hash.HashFragment"
        
        // Device Info features
        "nav_device_info" -> "com.ble1st.connectias.feature.deviceinfo.ui.DeviceInfoFragment"
        "nav_battery_analyzer" -> "com.ble1st.connectias.feature.deviceinfo.battery.BatteryAnalyzerFragment"
        "nav_storage_analyzer" -> "com.ble1st.connectias.feature.deviceinfo.storage.StorageAnalyzerFragment"
        "nav_process_monitor" -> "com.ble1st.connectias.feature.deviceinfo.process.ProcessMonitorFragment"
        "nav_sensor_monitor" -> "com.ble1st.connectias.feature.deviceinfo.sensor.SensorMonitorFragment"
        
        // Network features
        "nav_network_dashboard" -> "com.ble1st.connectias.feature.network.ui.NetworkDashboardFragment"
        "nav_port_scanner" -> "com.ble1st.connectias.feature.network.ui.PortScannerFragment"
        "nav_dns_lookup" -> "com.ble1st.connectias.feature.network.ui.DnsLookupFragment"
        "nav_network_monitor" -> "com.ble1st.connectias.feature.network.ui.NetworkMonitorFragment"
        "nav_wifi_analyzer" -> "com.ble1st.connectias.feature.network.ui.WifiAnalyzerFragment"
        "nav_mac_analyzer" -> "com.ble1st.connectias.feature.network.analysis.ui.MacAnalyzerFragment"
        "nav_subnet_analyzer" -> "com.ble1st.connectias.feature.network.analysis.ui.SubnetAnalyzerFragment"
        "nav_vlan_analyzer" -> "com.ble1st.connectias.feature.network.analysis.ui.VlanAnalyzerFragment"
        "nav_topology" -> "com.ble1st.connectias.feature.network.topology.ui.TopologyFragment"
        "nav_bandwidth_monitor" -> "com.ble1st.connectias.feature.network.ui.BandwidthMonitorFragment"
        "nav_speed_test" -> "com.ble1st.connectias.feature.network.speedtest.SpeedTestFragment"
        "nav_wake_on_lan" -> "com.ble1st.connectias.feature.network.wol.WolFragment"
        "nav_flow_analyzer" -> "com.ble1st.connectias.feature.network.ui.FlowAnalyzerFragment"
        "nav_dhcp_lease" -> "com.ble1st.connectias.feature.network.ui.DhcpLeaseFragment"
        "nav_hypervisor_detector" -> "com.ble1st.connectias.feature.network.ui.HypervisorDetectorFragment"
        
        // System tools
        "nav_log_viewer" -> "com.ble1st.connectias.core.logging.ui.LogViewerFragment"
        
        // WASM features
        "nav_plugin_manager" -> "com.ble1st.connectias.feature.wasm.ui.PluginManagerFragment"
        
        // USB features
        "nav_usb_dashboard" -> "com.ble1st.connectias.feature.usb.ui.UsbDashboardFragment"
        "nav_usb_storage_browser" -> "com.ble1st.connectias.feature.usb.browser.UsbStorageBrowserFragment"
        
        // DVD features
        "nav_dvd_player" -> "com.ble1st.connectias.feature.dvd.ui.DvdPlayerFragment"
        "nav_dvd_cd_detail" -> "com.ble1st.connectias.feature.dvd.ui.DvdCdDetailFragment"
        
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

fun resolveFeatureCategories(context: android.content.Context): List<ResolvedFeatureCategory> {
    val definitions = getFeatureDefinitions()
    val packageName = context.packageName
    
    return definitions.mapNotNull { category ->
        val resolvedFeatures = category.features.mapNotNull { featureDef ->
            // First check if navigation ID exists
            val id = context.resources.getIdentifier(featureDef.navIdName, "id", packageName)
            if (id == 0) {
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
        FeatureCategoryDef("Dashboards", listOf(
            FeatureDef("Security Dashboard", Icons.Default.Security, "nav_security_dashboard"),
            FeatureDef("Network Dashboard", Icons.Default.Wifi, "nav_network_dashboard"),
            FeatureDef("Device Info", Icons.Default.PermDeviceInformation, "nav_device_info"),
            FeatureDef("USB Devices", Icons.Default.Usb, "nav_usb_dashboard"),
            FeatureDef("USB Storage Browser", Icons.Default.FolderOpen, "nav_usb_storage_browser"),
            FeatureDef("WASM Plugins", Icons.Default.Extension, "nav_plugin_manager"),
            FeatureDef("Settings", Icons.Default.Settings, "nav_settings")
        )),
        FeatureCategoryDef("Security Suite", listOf(
            FeatureDef("Certificate Analyzer", Icons.Default.VerifiedUser, "nav_certificate_analyzer"),
            FeatureDef("Password Strength", Icons.Default.Password, "nav_password_strength"),
            FeatureDef("Encryption Tools", Icons.Default.EnhancedEncryption, "nav_encryption_tools"),
            FeatureDef("Firewall Analyzer", Icons.Default.Security, "nav_firewall_analyzer"),
            FeatureDef("WiFi Security Auditor", Icons.Default.WifiTethering, "nav_wifi_security_auditor"),
            FeatureDef("Vulnerability Scanner", Icons.Default.BugReport, "nav_vulnerability_scanner"),
            FeatureDef("Hash Calculator", Icons.Default.Tag, "nav_hash_tool")
        )),
        FeatureCategoryDef("Network Command", listOf(
            FeatureDef("Port Scanner", Icons.Default.Search, "nav_port_scanner"),
            FeatureDef("DNS Lookup", Icons.Default.Dns, "nav_dns_lookup"),
            FeatureDef("Network Monitor", Icons.Default.Speed, "nav_network_monitor"),
            FeatureDef("WiFi Analyzer", Icons.Default.SignalWifi4Bar, "nav_wifi_analyzer"),
            FeatureDef("Bandwidth Monitor", Icons.Default.DataUsage, "nav_bandwidth_monitor"),
            FeatureDef("Flow Analyzer", Icons.Default.Timeline, "nav_flow_analyzer"),
            FeatureDef("DHCP Lease Viewer", Icons.Default.List, "nav_dhcp_lease"),
            FeatureDef("Hypervisor Detector", Icons.Default.Computer, "nav_hypervisor_detector"),
            FeatureDef("Speed Test", Icons.Default.Router, "nav_speed_test"),
            FeatureDef("Wake-on-LAN", Icons.Default.Power, "nav_wake_on_lan"),
            FeatureDef("Network Topology", Icons.Default.Share, "nav_topology"),
            FeatureDef("MAC Analyzer", Icons.Default.Fingerprint, "nav_mac_analyzer"),
            FeatureDef("Subnet Analyzer", Icons.Default.Grid4x4, "nav_subnet_analyzer"),
            FeatureDef("VLAN Analyzer", Icons.Default.Layers, "nav_vlan_analyzer")
        )),
        FeatureCategoryDef("Machine Spirit (Hardware)", listOf(
            FeatureDef("Battery Analyzer", Icons.Default.BatteryStd, "nav_battery_analyzer"),
            FeatureDef("Storage Analyzer", Icons.Default.Storage, "nav_storage_analyzer"),
            FeatureDef("Process Monitor", Icons.Default.Memory, "nav_process_monitor"),
            FeatureDef("Sensor Monitor", Icons.Default.Sensors, "nav_sensor_monitor")
        )),
        FeatureCategoryDef("System Tools", listOf(
            FeatureDef("Log Viewer", Icons.Default.Description, "nav_log_viewer")
        )),
        FeatureCategoryDef("Media", listOf(
            FeatureDef("DVD Player", Icons.Default.Album, "nav_dvd_cd_detail")
        ))
    )
}
