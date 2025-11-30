<!-- ff8a31cb-e1d1-44d2-818d-af7c9d0e8b76 be28aa75-ef5c-4f50-9d74-5fb781874a68 -->
# Plan: USB Feature Module Implementation

## 1. Übersicht

Implementierung eines neuen Feature-Moduls `feature-usb` für USB-Geräte-Analyse und -Management. Das Modul verwendet libusb für USB-Zugriff, ist OpenSSL-kompatibel für kryptographische Operationen und implementiert die UI vollständig in Jetpack Compose ohne ViewModels.

**Ziele:**

- Automatische USB-Geräte-Erkennung beim Anschließen
- Berechtigungsabfrage nach Erkennung, bevor USB verwendet wird
- USB-Geräteinformationen (Vendor ID, Product ID, Descriptors)
- USB-Kommunikation (Bulk, Interrupt, Control Transfers)
- OpenSSL-kompatible Verschlüsselung für USB-Daten
- Reine Compose-UI ohne ViewModels

## 2. Analyse-Phase

### Aktueller Zustand

- Projekt verwendet modulare Architektur mit Feature-Modulen
- Hilt für Dependency Injection
- Jetpack Compose für UI (mit Fragment-Wrapper)
- Bestehende Module folgen Pattern: Fragment -> ComposeView -> @Composable Screen
- Einige Module verwenden ViewModels, andere nicht

### Identifizierte Anforderungen

- libusb-Integration für USB-Zugriff (via JNI)
- OpenSSL-kompatible Kryptographie
- Reine Compose-UI ohne ViewModels
- State-Management direkt in @Composable
- Automatische USB-Erkennung beim Anschließen
- Berechtigungsabfrage nach Erkennung, bevor USB verwendet wird
- Integration in bestehende Modul-Architektur

### Abhängigkeiten

- `:core` - Core-Funktionalität, EventBus, Services
- `:common` - Shared Models, Theme, Extensions
- libusb (native Library via JNI)
- OpenSSL (native Library oder BouncyCastle als Alternative)

### Risiken

- **Hoch**: Native Library-Integration (libusb JNI) erfordert NDK-Setup
- **Mittel**: USB-Berechtigungen und Android USB Host API Kompatibilität
- **Niedrig**: OpenSSL-Kompatibilität über BouncyCastle (bereits im Projekt)

## 3. Lösungsansatz

### Strategie

1. **Native Library Integration**: libusb via JNI Wrapper
2. **Kryptographie**: BouncyCastle für OpenSSL-Kompatibilität (bereits vorhanden)
3. **State Management**: Direkt in @Composable mit `remember`, `mutableStateOf`, `LaunchedEffect`
4. **Architektur**: Provider-Pattern für USB-Operationen, injiziert via Hilt
5. **Automatische Erkennung**: USB BroadcastReceiver für `USB_DEVICE_ATTACHED` Events
6. **Berechtigungsabfrage**: Nach Erkennung automatisch Berechtigung anfordern, bevor USB-Operationen ausgeführt werden

### Alternative Ansätze

- **Android USB Host API**: Einfacher, aber weniger Features → Verworfen (libusb erforderlich)
- **ViewModels**: Standard-Pattern → Verworfen (Benutzer-Anforderung: keine ViewModels)

## 4. Detaillierte Schritte

### Schritt 1: Modul-Struktur erstellen

**Dateien:**

- `feature-usb/build.gradle.kts`
- `feature-usb/src/main/AndroidManifest.xml`
- `feature-usb/consumer-rules.pro`

**Was wird gemacht:**

- Neues Android Library Modul erstellen
- Gradle-Konfiguration mit Compose, Hilt, NDK
- AndroidManifest mit USB-Berechtigungen (`android.hardware.usb.host`)
- USB BroadcastReceiver für automatische Erkennung registrieren
- ProGuard Rules

**Warum:**

- Modulare Architektur erfordert separates Modul
- NDK für libusb JNI-Integration
- USB Host Permission für USB-Zugriff erforderlich
- BroadcastReceiver ermöglicht automatische Erkennung beim Anschließen

**Risiko:** Niedrig

### Schritt 2: Native Library Setup (libusb JNI)

**Dateien:**

- `feature-usb/src/main/cpp/CMakeLists.txt`
- `feature-usb/src/main/cpp/usb_jni.cpp`
- `feature-usb/src/main/cpp/usb_wrapper.h`
- `feature-usb/src/main/java/.../usb/native/UsbNative.kt`

**Was wird gemacht:**

- CMake-Konfiguration für libusb
- JNI-Wrapper für libusb-Funktionen
- Kotlin Native Interface (JNI)
- libusb als native Dependency

**Warum:**

- libusb erfordert native Integration
- JNI ermöglicht Zugriff von Kotlin

**Risiko:** Hoch - Erfordert NDK-Kenntnisse

### Schritt 3: USB Provider Implementation

**Dateien:**

- `feature-usb/src/main/java/.../usb/provider/UsbProvider.kt`
- `feature-usb/src/main/java/.../usb/models/UsbDevice.kt`
- `feature-usb/src/main/java/.../usb/models/UsbDescriptor.kt`
- `feature-usb/src/main/java/.../usb/models/UsbTransfer.kt`
- `feature-usb/src/main/java/.../usb/permission/UsbPermissionManager.kt`
- `feature-usb/src/main/java/.../usb/detection/UsbDeviceDetector.kt`

**Was wird gemacht:**

- UsbProvider Interface und Implementation
- Data Models für USB-Geräte
- USB-Operationen (Enumeration, Descriptor-Reading, Transfers)
- UsbPermissionManager für automatische Berechtigungsabfrage nach Erkennung
- UsbDeviceDetector für automatische Erkennung via BroadcastReceiver
- Error Handling

**Warum:**

- Provider-Pattern für Testbarkeit
- Klare Datenmodelle
- Automatische Erkennung mit anschließender Berechtigungsabfrage
- Separater Detector für automatische Erkennungslogik

**Risiko:** Mittel

### Schritt 4: OpenSSL-kompatible Kryptographie

**Dateien:**

- `feature-usb/src/main/java/.../usb/crypto/UsbCryptoProvider.kt`

**Was wird gemacht:**

- Kryptographie-Provider mit BouncyCastle
- OpenSSL-kompatible Algorithmen (AES, RSA, etc.)
- Verschlüsselung/Entschlüsselung für USB-Daten

**Warum:**

- OpenSSL-Kompatibilität erforderlich
- BouncyCastle bereits im Projekt vorhanden

**Risiko:** Niedrig

### Schritt 5: Hilt Dependency Injection

**Dateien:**

- `feature-usb/src/main/java/.../usb/di/UsbModule.kt`

**Was wird gemacht:**

- Hilt-Modul für USB-Provider
- Singleton-Provider für UsbProvider
- Context-Injection für USB-Zugriff
- UsbPermissionManager als Singleton
- UsbDeviceDetector als Singleton

**Warum:**

- Konsistenz mit bestehender Architektur
- Dependency Injection für Testbarkeit

**Risiko:** Niedrig

### Schritt 6: Compose UI - USB Dashboard Screen

**Dateien:**

- `feature-usb/src/main/java/.../usb/ui/UsbDashboardScreen.kt`
- `feature-usb/src/main/java/.../usb/ui/components/UsbDeviceCard.kt`
- `feature-usb/src/main/java/.../usb/ui/components/UsbDeviceList.kt`
- `feature-usb/src/main/java/.../usb/ui/components/UsbPermissionDialog.kt`

**Was wird gemacht:**

- @Composable UsbDashboardScreen ohne ViewModel
- State-Management mit `remember` und `mutableStateOf`
- USB-Geräte-Liste mit LazyColumn
- LaunchedEffect für initiale USB-Enumeration
- DisposableEffect für BroadcastReceiver-Registrierung
- Automatische Erkennung beim Screen-Load und bei neuen Geräten
- Berechtigungsdialog automatisch nach Erkennung eines neuen Geräts

**Warum:**

- Reine Compose-UI wie gewünscht
- State direkt in Composable
- Automatische Erkennung mit anschließender Berechtigungsabfrage

**Risiko:** Niedrig

### Schritt 7: Compose UI - USB Device Detail Screen

**Dateien:**

- `feature-usb/src/main/java/.../usb/ui/UsbDeviceDetailScreen.kt`
- `feature-usb/src/main/java/.../usb/ui/components/UsbDescriptorView.kt`
- `feature-usb/src/main/java/.../usb/ui/components/UsbTransferDialog.kt`

**Was wird gemacht:**

- Detail-Screen für einzelnes USB-Gerät
- Anzeige von Descriptors, Vendor/Product IDs
- USB-Transfer-UI (Bulk, Interrupt, Control)
- Berechtigungsprüfung vor Transfer-Operationen
- Automatische Berechtigungsabfrage falls nicht vorhanden

**Warum:**

- Detaillierte Geräteinformationen
- USB-Kommunikation ermöglichen
- Sicherheit durch Berechtigungsprüfung und -abfrage

**Risiko:** Niedrig

### Schritt 8: Fragment-Wrapper für Navigation

**Dateien:**

- `feature-usb/src/main/java/.../usb/ui/UsbDashboardFragment.kt`
- `feature-usb/src/main/java/.../usb/ui/UsbDeviceDetailFragment.kt`

**Was wird gemacht:**

- Fragment-Wrapper mit ComposeView
- Hilt-Injection für UsbProvider, UsbPermissionManager und UsbDeviceDetector
- Navigation-Integration
- USB-BroadcastReceiver-Registrierung im Fragment Lifecycle
- Automatische Weitergabe von USB-Events an Compose Screen

**Warum:**

- Bestehende Navigation verwendet Fragments
- ComposeView-Pattern wie in anderen Modulen
- BroadcastReceiver für automatische Erkennung
- Lifecycle-Management für Receiver

**Risiko:** Niedrig

### Schritt 9: Module-Integration

**Dateien:**

- `settings.gradle.kts` (Modul hinzufügen)
- `app/build.gradle.kts` (Dependency hinzufügen)
- `gradle.properties` (Feature-Flag)
- `core/src/main/java/.../module/ModuleCatalog.kt` (Route hinzufügen)
- `app/src/main/res/navigation/nav_graph.xml` (Navigation-Route)

**Was wird gemacht:**

- Modul in Build-System integrieren
- Feature-Flag für optionales Modul
- Navigation-Route registrieren

**Warum:**

- Modulare Architektur erfordert Integration
- Optionales Feature via Feature-Flag

**Risiko:** Niedrig

### Schritt 10: Strings und Ressourcen

**Dateien:**

- `feature-usb/src/main/res/values/strings.xml`
- `feature-usb/src/main/res/xml/device_filter.xml` (USB Device Filter)

**Was wird gemacht:**

- String-Ressourcen für UI
- USB Device Filter für automatische Erkennung
- Lokalisierung vorbereiten

**Warum:**

- Best Practices für Android
- Device Filter ermöglicht automatische Erkennung

**Risiko:** Niedrig

## 5. Test-Strategie

### Unit Tests

- UsbProvider Tests (mit Mocked Native Layer)
- UsbCryptoProvider Tests
- UsbPermissionManager Tests
- UsbDeviceDetector Tests
- Model Tests

### Integration Tests

- USB-Enumeration (mit Mock-Geräten)
- USB-Transfer-Tests
- Automatische Erkennung und Berechtigungsabfrage-Tests

### UI Tests

- Compose UI Tests für Screens
- Navigation-Tests
- Berechtigungsdialog-Tests
- Automatische Erkennungs-Tests

### Native Tests

- JNI-Wrapper Tests (C++ Unit Tests)

## 6. Rollback-Plan

### Bei Problemen

1. Feature-Flag auf `false` setzen → Modul nicht gebaut
2. Modul-Dependency aus `app/build.gradle.kts` entfernen
3. Native Libraries können deaktiviert werden ohne Code-Änderungen

### Backup

- Git-Commit vor Implementierung
- Feature-Flag-basierte Deaktivierung

## 7. Annahmen

1. **libusb**: Wird als native Library via JNI integriert
2. **OpenSSL-Kompatibilität**: Wird über BouncyCastle erreicht (bereits im Projekt)
3. **State Management**: Direkt in @Composable, keine ViewModels
4. **Navigation**: Fragment-basiert mit ComposeView (wie bestehende Module)
5. **Android Version**: minSdk 33, USB Host API verfügbar
6. **NDK**: Android NDK ist verfügbar für native Compilation
7. **USB-Erkennung**: Automatische Erkennung beim Anschließen via BroadcastReceiver
8. **Berechtigungsabfrage**: Automatisch nach Erkennung, bevor USB-Operationen ausgeführt werden

## 8. Technische Details

### Automatische USB-Erkennung mit Berechtigungsabfrage

```kotlin
// UsbDeviceDetector.kt
class UsbDeviceDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _detectedDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val detectedDevices: StateFlow<List<UsbDevice>> = _detectedDevices.asStateFlow()
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    device?.let { 
                        _detectedDevices.value = _detectedDevices.value + it
                        // Automatisch Berechtigung anfordern
                        onDeviceDetected(it)
                    }
                }
            }
        }
    }
    
    fun registerReceiver(activity: Activity) {
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        context.registerReceiver(broadcastReceiver, filter)
    }
    
    fun unregisterReceiver() {
        context.unregisterReceiver(broadcastReceiver)
    }
}
```

### State Management Pattern (ohne ViewModel)

```kotlin
@Composable
fun UsbDashboardScreen(
    usbProvider: UsbProvider,
    permissionManager: UsbPermissionManager,
    deviceDetector: UsbDeviceDetector
) {
    var devices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var permissionRequest by remember { mutableStateOf<UsbDevice?>(null) }
    
    // Automatische Erkennung via StateFlow
    val detectedDevices by deviceDetector.detectedDevices.collectAsState()
    
    LaunchedEffect(detectedDevices) {
        detectedDevices.forEach { device ->
            // Automatisch Berechtigung anfordern nach Erkennung
            if (!permissionManager.hasPermission(device)) {
                permissionRequest = device
            } else {
                devices = devices + device
            }
        }
    }
    
    // Initiale Enumeration
    LaunchedEffect(Unit) {
        isLoading = true
        devices = usbProvider.enumerateDevices()
        isLoading = false
    }
    
    // Berechtigungsdialog nach Erkennung
    permissionRequest?.let { device ->
        UsbPermissionDialog(
            device = device,
            onGranted = {
                permissionManager.requestPermission(device) { granted ->
                    if (granted) {
                        devices = devices + device
                    }
                }
                permissionRequest = null
            },
            onDenied = {
                permissionRequest = null
            }
        )
    }
    
    // UI Rendering
    // ...
}
```

### Hilt Injection ohne ViewModel

```kotlin
@AndroidEntryPoint
class UsbDashboardFragment : Fragment() {
    
    @Inject lateinit var usbProvider: UsbProvider
    @Inject lateinit var permissionManager: UsbPermissionManager
    @Inject lateinit var deviceDetector: UsbDeviceDetector
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // BroadcastReceiver für automatische Erkennung registrieren
        deviceDetector.registerReceiver(requireActivity())
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        deviceDetector.unregisterReceiver()
    }
    
    override fun onCreateView(...): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ConnectiasTheme {
                    UsbDashboardScreen(
                        usbProvider = usbProvider,
                        permissionManager = permissionManager,
                        deviceDetector = deviceDetector
                    )
                }
            }
        }
    }
}
```

### AndroidManifest USB-Konfiguration

```xml
<uses-feature android:name="android.hardware.usb.host" />
<uses-permission android:name="android.permission.USB_PERMISSION" />

<activity ...>
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
    </intent-filter>
    <meta-data
        android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
        android:resource="@xml/device_filter" />
</activity>

<receiver android:name=".usb.detection.UsbDeviceReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
    </intent-filter>
</receiver>
```

## 9. Implementierungs-Checkliste

- [ ] Modul-Struktur erstellen: build.gradle.kts, AndroidManifest.xml, consumer-rules.pro mit USB-Berechtigungen und BroadcastReceiver
- [ ] Native Library Setup: CMakeLists.txt, JNI-Wrapper für libusb (usb_jni.cpp, UsbNative.kt)
- [ ] USB Provider Implementation: UsbProvider, Models (UsbDevice, UsbDescriptor, UsbTransfer), UsbPermissionManager, UsbDeviceDetector
- [ ] OpenSSL-kompatible Kryptographie: UsbCryptoProvider mit BouncyCastle
- [ ] Hilt Dependency Injection: UsbModule mit Provider, PermissionManager und DeviceDetector
- [ ] Compose UI - USB Dashboard Screen: UsbDashboardScreen ohne ViewModel, automatische Erkennung via StateFlow, Berechtigungsdialog nach Erkennung
- [ ] Compose UI - USB Device Detail Screen: UsbDeviceDetailScreen mit Descriptors und Transfer-UI, Berechtigungsprüfung
- [ ] Fragment-Wrapper: UsbDashboardFragment, UsbDeviceDetailFragment mit ComposeView, Hilt-Injection und BroadcastReceiver Lifecycle
- [ ] Module-Integration: settings.gradle.kts, app/build.gradle.kts, gradle.properties, ModuleCatalog, Navigation
- [ ] Strings und Ressourcen: strings.xml, device_filter.xml für automatische Erkennung

### To-dos

- [ ] Modul-Struktur erstellen: build.gradle.kts, AndroidManifest.xml, consumer-rules.pro mit USB-Berechtigungen
- [ ] Native Library Setup: CMakeLists.txt, JNI-Wrapper für libusb (usb_jni.cpp, UsbNative.kt)
- [ ] USB Provider Implementation: UsbProvider, Models (UsbDevice, UsbDescriptor, UsbTransfer), UsbPermissionManager
- [ ] OpenSSL-kompatible Kryptographie: UsbCryptoProvider mit BouncyCastle
- [ ] Hilt Dependency Injection: UsbModule mit Provider und PermissionManager
- [ ] Compose UI - USB Dashboard Screen: UsbDashboardScreen ohne ViewModel, automatische Erkennung, Berechtigungsdialog
- [ ] Compose UI - USB Device Detail Screen: UsbDeviceDetailScreen mit Descriptors und Transfer-UI
- [ ] Fragment-Wrapper: UsbDashboardFragment, UsbDeviceDetailFragment mit ComposeView und Hilt-Injection
- [ ] Module-Integration: settings.gradle.kts, app/build.gradle.kts, gradle.properties, ModuleCatalog, Navigation
- [ ] Strings und Ressourcen: strings.xml, device_filter.xml für automatische Erkennung