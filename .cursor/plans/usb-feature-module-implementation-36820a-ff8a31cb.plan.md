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

#### Hoch: Native Library-Integration (libusb JNI)

**Risiko:** libusb JNI-Integration erfordert NDK-Setup, ABI-spezifische Builds und kann zu nativen Crashes führen.

**Mitigationen:**
1. **Fallback zu Android USB Host API**: Dokumentierter Fallback-Mechanismus, der automatisch auf Android USB Host API wechselt, wenn libusb nicht verfügbar ist oder fehlschlägt.
2. **Feature-Flag und Runtime Capability-Check**: Feature-Flag `ENABLE_LIBUSB_NATIVE` in `gradle.properties`. Runtime-Check mit `System.loadLibrary()` in try/catch, klare Fehlerlogs und benutzerfreundliche Diagnose-Meldungen.
3. **Prebuilt ABI-spezifische Libraries**: Primär: Build from source via CMake/FetchContent in `gradle-externalNativeBuild`. Fallback: Prebuilt `.so` Dateien für CI/Quick-Tests pro ABI (arm64-v8a, armeabi-v7a, x86_64, x86) mit klaren Fehlerlogs bei fehlender ABI.
4. **Crash-by-Design Mode**: Optionale Crash-by-Design-Mode für Tests, der nativen Crashes simuliert und Recovery testet.

**Acceptance Criteria:**
- App startet auch wenn libusb nicht verfügbar ist (Fallback aktiv)
- Klare Fehlermeldungen in Logs und UI bei libusb-Fehlern
- Feature-Flag ermöglicht vollständige Deaktivierung

**Owner:** Native Development Team  
**Rollback:** Feature-Flag auf `false` setzen → Fallback zu Android USB Host API

#### Mittel: USB Permission Timeouts

**Risiko:** USB-Berechtigungsanfragen können hängen bleiben oder Timeouts verursachen.

**Mitigationen:**
1. **Konfigurierbare Timeout/Retry-Policy**: Timeout-Konfiguration in `UsbPermissionManager` (Standard: 30 Sekunden), retry mit exponential backoff (max 3 Versuche), konfigurierbar via Settings.
2. **Explizite User Prompts und Cancellation Paths**: User kann Permission-Request abbrechen, klare UI-Feedback während Wartezeit, Progress-Indicator mit Timeout-Anzeige.
3. **Background Worker Handling**: Permission-Requests in separatem Worker-Thread, keine UI-Blockierung, Telemetrie für hängende Requests (Logging + Analytics).

**Acceptance Criteria:**
- Permission-Requests haben definiertes Timeout (kein Hängen)
- User kann Requests abbrechen
- Telemetrie erfasst hängende Requests

**Owner:** Android Development Team  
**Rollback:** Timeout auf sehr kurzen Wert setzen (5 Sekunden) für schnelle Fehlerbehandlung

#### Mittel: Verpasste Device-Detection Events

**Risiko:** BroadcastReceiver kann Events verpassen, besonders bei schnellen Connect/Disconnect-Zyklen.

**Mitigationen:**
1. **Debounce/Retry-Logic**: Debounce für Connect/Disconnect-Events (200ms), Retry-Logic für verpasste Events, periodische Re-Scan (alle 5 Sekunden) als Fallback.
2. **Event Queue/Watchdog**: Event-Queue für USB-Events, Watchdog-Timer der periodisch re-scannt wenn keine Events kommen, Queue-Persistierung für Recovery nach App-Restart.
3. **Race-Condition Tests**: Unit-Tests für schnelle Connect/Disconnect-Zyklen, Integration-Tests mit USB-Simulator, Stress-Tests (50+ Events/Minute).

**Acceptance Criteria:**
- Keine verpassten Events bei normaler Nutzung
- Re-Scan erkennt verpasste Geräte innerhalb 5 Sekunden
- Tests decken Race-Conditions ab

**Owner:** Android Development Team  
**Rollback:** Erhöhung Re-Scan-Intervall auf 1 Sekunde (höherer Battery-Verbrauch akzeptabel)

#### Mittel: Integration Testing Komplexität

**Risiko:** USB-Hardware-Tests sind schwer zu automatisieren und erfordern physische Geräte.

**Mitigationen:**
1. **CI-Strategie mit USB Mocks/Simulatoren**: USB-Mock-Framework für Unit-Tests, USB/IP oder Lightweight-Emulator für Integration-Tests, CI-Pipeline mit Mock-Tests als Standard.
2. **Tagged Hardware Lab Matrix**: Hardware-Lab mit getaggten Geräten (Device-Model, OEM, Android-Version), Nightly Compatibility-Runs auf Hardware-Matrix, Test-Ergebnisse in Dashboard.
3. **USB Mocks für Unit-Tests**: Mock-Implementierungen für `UsbManager`, `UsbDevice`, `UsbDeviceConnection`, parametrisierte Test-Fixtures.

**Acceptance Criteria:**
- CI-Pipeline läuft vollständig mit Mocks
- Hardware-Lab-Tests laufen nightly
- Mock-Tests decken 80%+ der Code-Pfade ab

**Owner:** QA/Test Team  
**Rollback:** Manuelle Tests als Fallback, CI nur mit Mocks

#### Niedrig: OEM Customization Risk

**Risiko:** Verschiedene Android-OEMs können USB Host API unterschiedlich implementieren.

**Mitigationen:**
1. **Device-Compatibility Test Matrix**: Test-Matrix mit Top-10 OEMs (Samsung, Xiaomi, Huawei, etc.), dokumentierte Inkompatibilitäten mit Workarounds, Telemetrie-basierte Reporting für neue Inkompatibilitäten.
2. **Vendor-Specific Fallbacks**: Vendor-Erkennung (Build.MANUFACTURER), vendor-spezifische Workarounds in Code, Feature-Flags pro Vendor.
3. **Telemetrie-basiertes Reporting**: Automatisches Reporting von USB-Fehlern mit Device-Info, schnelle Identifikation neuer Inkompatibilitäten, Dashboard für Device-Compatibility.

**Acceptance Criteria:**
- Top-10 OEMs getestet und dokumentiert
- Telemetrie identifiziert neue Inkompatibilitäten innerhalb 24h
- Workarounds für bekannte Issues implementiert

**Owner:** Android Development Team  
**Rollback:** Feature-Flag pro Vendor für problematische OEMs

#### Niedrig: OpenSSL-Kompatibilität

**Risiko:** OpenSSL-Kompatibilität über BouncyCastle (bereits im Projekt vorhanden) - geringes Risiko.

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
- `feature-usb/src/main/cpp/usb_wrapper.cpp`
- `feature-usb/src/main/java/.../usb/native/UsbNative.kt`
- `feature-usb/build.gradle.kts` (externalNativeBuild Konfiguration)

**Was wird gemacht:**

#### 1. libusb Sourcing/Build-Strategie

**Primärer Ansatz: Build from Source**
- libusb wird via CMake/FetchContent in `gradle-externalNativeBuild` gebaut
- CMakeLists.txt verwendet `FetchContent_Declare` und `FetchContent_MakeAvailable` für libusb
- Build erfolgt automatisch bei Gradle-Build, keine manuellen Schritte nötig
- Vorteil: Immer aktuelle Version, keine ABI-spezifischen Prebuilt-Dateien nötig

**Fallback: Prebuilt .so Libraries**
- Prebuilt `.so` Dateien für CI/Quick-Tests pro ABI (arm64-v8a, armeabi-v7a, x86_64, x86)
- Werden in `src/main/jniLibs/{abi}/` abgelegt
- Aktivierung via Feature-Flag `USE_PREBUILT_LIBUSB=true` in `gradle.properties`
- Vorteil: Schnellere CI-Builds, keine NDK-Kompilierung nötig

**Entscheidung:** Primär = Build from Source, Fallback = Prebuilt für CI

#### 2. ABI Support

**Erforderliche ABIs:**
- `arm64-v8a` (64-bit ARM, primär für moderne Geräte)
- `armeabi-v7a` (32-bit ARM, Legacy-Support)
- `x86_64` (64-bit x86, Emulator/Tablets)
- `x86` (32-bit x86, Legacy-Emulator)

**Produktion pro-ABI .so Artefakte:**
- CMake baut automatisch für alle konfigurierten ABIs
- Gradle `splits.abi` Konfiguration erstellt separate APKs pro ABI (optional)
- AAR-Packaging: Alle ABI-spezifischen `.so` Dateien werden in AAR eingebunden
- Verifikation: `./gradlew :feature-usb:assembleRelease` prüft alle ABIs

#### 3. JNI Error-Handling Pattern

**Uniformes Error-Handling Pattern:**

1. **Argument Validation**: Alle JNI-Funktionen validieren Eingabeparameter (null-checks, range-checks)
2. **libusb Return Value Checks**: Alle libusb-Aufrufe prüfen Return-Werte (`LIBUSB_SUCCESS`, etc.)
3. **Error Conversion**: libusb-Errors werden zu beschreibenden errno-style Messages konvertiert
4. **Exception Propagation**: Fehler werden via `env->ThrowNew()` als Java-Exceptions nach Kotlin propagiert
5. **Native-Side Logging**: Alle Fehler werden auf Native-Seite geloggt (Android Log) mit Kontext

**Beispiel:**
```cpp
jlong JNICALL Java_com_ble1st_connectias_feature_usb_native_UsbNative_openDevice
  (JNIEnv *env, jclass clazz, jint vendorId, jint productId) {
    // 1. Argument validation
    if (vendorId < 0 || productId < 0) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                     "Invalid vendor or product ID");
        return -1;
    }
    
    // 2. libusb call with return value check
    libusb_device_handle *handle = libusb_open_device_with_vid_pid(ctx, vendorId, productId);
    if (handle == nullptr) {
        int error = libusb_get_last_error();
        // 3. Error conversion and logging
        __android_log_print(ANDROID_LOG_ERROR, "UsbNative",
                           "Failed to open device: %s", libusb_error_name(error));
        // 4. Exception propagation
        env->ThrowNew(env->FindClass("java/io/IOException"),
                     libusb_error_name(error));
        return -1;
    }
    
    return reinterpret_cast<jlong>(handle);
}
```

#### 4. Thread-Safety Guarantees

**Gewählter Ansatz: Dedicated Native Worker Thread**

- **Ein libusb_context pro Prozess**: Singleton-Pattern für libusb_context, initialisiert bei App-Start
- **Dedicated Native Worker Thread**: Alle libusb-Aufrufe laufen auf einem dedizierten Thread
- **JNI Call Marshalling**: JNI-Aufrufe von Kotlin werden zum Worker-Thread gemarshalled
- **Mutex Protection**: Kritische libusb-Operationen sind mit Mutex geschützt (falls nötig)

**Implementierung:**
- Native Thread-Pool mit einem Worker-Thread für libusb-Operationen
- Kotlin-Seite: Coroutines mit `Dispatchers.IO` für asynchrone USB-Operationen
- JNI-Bridge: Synchronisiert Aufrufe zum Worker-Thread

**Fallback: Mutex-Protected Calls**
- Falls Worker-Thread-Ansatz Probleme verursacht: Alle libusb-Aufrufe mit Mutex schützen
- Einfacher, aber weniger performant

**Dokumentation:** Alle libusb-Interaktionen müssen auf Worker-Thread laufen, JNI-Calls werden automatisch gemarshalled.

#### 5. Resource Cleanup Lifecycle

**Explicit Init/Shutdown JNI Bindings:**
- `UsbNative.init()`: Ruft `libusb_init()` auf, erstellt libusb_context
- `UsbNative.shutdown()`: Ruft `libusb_exit()` auf, gibt libusb_context frei
- Werden von Application-Klasse aufgerufen (onCreate/onTerminate)

**RAII-Style Wrappers in C++:**
- C++ Wrapper-Klassen für libusb_device_handle mit Destruktoren
- Destruktoren rufen automatisch `libusb_close()` auf
- Exception-Safe: Ressourcen werden auch bei Exceptions freigegeben

**Finalizer Hooks:**
- Kotlin-Seite: Finalizer für UsbDevice-Handles, ruft `UsbNative.closeDevice()` auf
- JNI OnUnload: Cleanup aller verbleibenden Ressourcen bei Library-Unload

**Testing:**
- Memory-Leak-Tests: Valgrind/AddressSanitizer für native Memory-Leaks
- Lifecycle-Tests: App-Start/Stop, Fragment-Lifecycle, Configuration-Changes
- Verifikation: Keine Leaks über ABI-Builds und Lifecycle-Übergänge

**Warum:**

- libusb erfordert native Integration
- JNI ermöglicht Zugriff von Kotlin
- Thread-Safety und Resource-Management sind kritisch für Stabilität

**Risiko:** Hoch - Erfordert NDK-Kenntnisse, Thread-Safety und Resource-Management

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

### 1. Native-Layer Mocking

**Empfohlener Ansatz:** Android NDK gtest Harness für JNI Unit Tests plus Symbol-Level Stubs für libusb.

**Implementierung:**
- **JNI Unit Tests**: Android NDK gtest Framework für C++ Unit Tests (`src/test/cpp/usb_jni_test.cpp`)
- **Symbol-Level Stubs**: Mock-Implementierungen für libusb-Funktionen (`libusb_init`, `libusb_open`, etc.) in Test-Build
- **CMake Test Configuration**: Separate Test-Targets in `CMakeLists.txt` für native Tests
- **Test Execution**: Native Tests laufen via `./gradlew :feature-usb:connectedAndroidTest` oder direkt via `adb shell`

**Alternative:** Vollständige Mock-Implementierung von libusb für Unit Tests (aufwändiger, aber vollständige Kontrolle).

### 2. Mock USB Device Fixtures

**Parameterisierte Test-Fixtures** die USB-Deskriptoren, Endpoints und Transfer-Verhalten emulieren.

**Tools:**
- **USB/IP**: Linux-basiertes Tool für USB-Device-Simulation (für Integration-Tests)
- **Lightweight Emulator**: Eigener USB-Device-Emulator für Unit-Tests (Mock-Implementierung von `UsbManager`, `UsbDevice`, `UsbDeviceConnection`)

**Fixture-Parameter:**
- Vendor ID, Product ID, Serial Number
- Device Class, Interface Classes
- Endpoint-Deskriptoren (Bulk, Interrupt, Control)
- Transfer-Verhalten (Success, Timeout, Error)

**Beispiel:**
```kotlin
data class MockUsbDeviceFixture(
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?,
    val deviceClass: Int,
    val interfaces: List<MockUsbInterface>,
    val transferBehavior: TransferBehavior = TransferBehavior.Success
)
```

### 3. Edge Case Test Szenarien

**Unit/Integration Test Cases:**

1. **Permission Denied/Revoked Mid-Transfer**
   - Test: Permission während aktiver USB-Transfer wird widerrufen
   - Erwartetes Verhalten: Transfer wird abgebrochen, Fehler wird gemeldet, Recovery-Mechanismus aktiviert
   - Test: `testPermissionRevokedDuringBulkTransfer()`

2. **Device Disconnect During Enumeration**
   - Test: Gerät wird während Enumeration getrennt
   - Erwartetes Verhalten: Enumeration wird abgebrochen, teilweise Ergebnisse werden zurückgegeben, Fehler wird geloggt
   - Test: `testDeviceDisconnectDuringEnumeration()`

3. **Device Disconnect During Transfer**
   - Test: Gerät wird während aktiver USB-Transfer getrennt
   - Erwartetes Verhalten: Transfer wird abgebrochen, Ressourcen werden freigegeben, Fehler wird gemeldet
   - Test: `testDeviceDisconnectDuringTransfer()`

4. **Rapid Connect/Disconnect Flapping**
   - Test: Gerät wird schnell mehrfach verbunden/getrennt (10x in 1 Sekunde)
   - Erwartetes Verhalten: Debounce-Logic verhindert Duplikate, alle Events werden verarbeitet, keine Race-Conditions
   - Test: `testRapidConnectDisconnectFlapping()`

5. **OOM During Enumeration**
   - Test: Out-of-Memory während Enumeration großer Geräte-Liste
   - Erwartetes Verhalten: Enumeration wird abgebrochen, Ressourcen werden freigegeben, Fehler wird gemeldet
   - Test: `testOomDuringEnumeration()`

6. **Simulated JNI/Native Crashes**
   - Test: Native Crash wird simuliert (via Crash-by-Design Mode)
   - Erwartetes Verhalten: App bleibt stabil, Fehler wird geloggt, Fallback-Mechanismus aktiviert
   - Test: `testNativeCrashRecovery()`

### 4. UI Test Injection Methods

**BroadcastReceiver Test Hooks:**
- Dependency-injectable Event Dispatcher für USB-Events
- Test-Utilities zum Simulieren von `ACTION_USB_DEVICE_ATTACHED`/`DETACHED` Intents
- Mock `UsbManager` für Permission-Request-Simulation

**Espresso/Compose Test Rule Utilities:**
- `UsbDeviceTestRule`: JUnit Rule für USB-Device-Simulation in Tests
- `PermissionTestRule`: JUnit Rule für Permission-Request-Simulation
- Compose Test Utilities: `simulateUsbDeviceAttached()`, `simulatePermissionGranted()`

**Beispiel:**
```kotlin
@get:Rule
val usbTestRule = UsbDeviceTestRule()

@Test
fun testDeviceDetection() {
    usbTestRule.simulateDeviceAttached(vendorId = 0x1234, productId = 0x5678)
    composeTestRule.onNodeWithText("USB Device Detected").assertIsDisplayed()
}
```

### 5. Performance/Stress Test Requirements

**Benchmarks:**
- **50 Concurrent Device Enumerations**: 50 Geräte gleichzeitig enumerieren, max. 5 Sekunden, max. 100MB Memory
- **N Rapid Connect/Disconnects**: 100 Connect/Disconnect-Events pro Minute, keine Memory-Leaks, CPU < 20%
- **Memory/Battery Thresholds**: Memory-Verbrauch < 50MB pro Gerät, Battery-Impact < 5% pro Stunde bei kontinuierlicher Überwachung

**Pass/Fail Criteria:**
- **Performance**: Alle Benchmarks müssen innerhalb definierter Thresholds bleiben
- **Stability**: Keine Crashes, Memory-Leaks oder Race-Conditions
- **Resource Usage**: CPU, Memory und Battery innerhalb Limits

**CI Execution Guidance:**
- Performance-Tests laufen nightly auf CI (nicht bei jedem Commit)
- Stress-Tests laufen auf dedizierten Hardware-Geräten
- Ergebnisse werden in Performance-Dashboard gespeichert

### Unit Tests (Detailliert)

- **UsbProvider Tests**: Mocked Native Layer, Test aller USB-Operationen (Enumeration, Open, Close, Transfer)
- **UsbCryptoProvider Tests**: OpenSSL-Kompatibilitätstests, Verschlüsselung/Entschlüsselung mit Test-Vektoren
- **UsbPermissionManager Tests**: Permission-Request-Simulation, Timeout-Handling, Retry-Logic
- **UsbDeviceDetector Tests**: BroadcastReceiver-Simulation, Deduplication-Logic, Race-Condition-Tests
- **Model Tests**: UsbDevice, UsbDescriptor, UsbTransfer Model-Validierung

### Integration Tests (Detailliert)

- **USB-Enumeration**: Mock-Geräte mit verschiedenen Konfigurationen, Edge-Cases (leere Liste, sehr große Liste)
- **USB-Transfer-Tests**: Bulk, Interrupt, Control Transfers mit verschiedenen Daten-Größen
- **Automatische Erkennung und Berechtigungsabfrage**: End-to-End-Tests mit simulierten USB-Events

### UI Tests (Detailliert)

- **Compose UI Tests**: Screens mit verschiedenen Device-Listen, Loading-States, Error-States
- **Navigation-Tests**: Navigation zwischen USB-Dashboard und Device-Detail-Screens
- **Berechtigungsdialog-Tests**: Permission-Request-Flow, User-Interaktionen (Grant/Deny)
- **Automatische Erkennungs-Tests**: UI-Updates bei Device-Detection, State-Synchronisation

### Native Tests (Detailliert)

- **JNI-Wrapper Tests**: C++ Unit Tests für alle JNI-Funktionen, Error-Handling, Resource-Cleanup
- **libusb Integration Tests**: Native Tests für libusb-Funktionen (falls möglich ohne Hardware)

## 6. Rollback-Plan

### Bei Problemen

1. **Feature-Flag Deaktivierung**: Feature-Flag `ENABLE_USB_FEATURE` auf `false` setzen → Modul nicht gebaut, Navigation-Route deaktiviert
2. **Modul-Dependency Entfernen**: Modul-Dependency aus `app/build.gradle.kts` entfernen, `ModuleCatalog` Eintrag deaktivieren
3. **Native Libraries Deaktivierung**: Native Libraries können deaktiviert werden ohne Code-Änderungen via Feature-Flag `ENABLE_LIBUSB_NATIVE=false`
4. **Vendor-Specific Rollback**: Feature-Flag pro Vendor für problematische OEMs (z.B. `ENABLE_USB_SAMSUNG=false`)
5. **Permission Manager Fallback**: Bei Permission-Problemen: Timeout auf sehr kurzen Wert setzen (5 Sekunden) für schnelle Fehlerbehandlung

### Backup

- Git-Commit vor Implementierung (Branch: `feature/usb-module`)
- Feature-Flag-basierte Deaktivierung (keine Code-Änderungen nötig)
- Rollback-Commits dokumentiert in Git-Tags (`rollback/usb-module-v1.0`)

### Kritische Rollback-Punkte

1. **Native Library Crashes**: Sofortiger Rollback via Feature-Flag, Fallback zu Android USB Host API
2. **Permission Timeouts**: Timeout auf 5 Sekunden reduzieren, User-Prompts vereinfachen
3. **Device Detection Failures**: Re-Scan-Intervall auf 1 Sekunde erhöhen (höherer Battery-Verbrauch)
4. **OEM Inkompatibilitäten**: Vendor-spezifische Feature-Flags deaktivieren

## 7. Annahmen

### Technische Annahmen

1. **libusb**: Wird als native Library via JNI integriert
2. **OpenSSL-Kompatibilität**: Wird über BouncyCastle erreicht (bereits im Projekt)
3. **State Management**: Direkt in @Composable, keine ViewModels
4. **Navigation**: Fragment-basiert mit ComposeView (wie bestehende Module)
5. **NDK**: Android NDK ist verfügbar für native Compilation
6. **USB-Erkennung**: Automatische Erkennung beim Anschließen via BroadcastReceiver
7. **Berechtigungsabfrage**: Automatisch nach Erkennung, bevor USB-Operationen ausgeführt werden

### Android USB Host API Constraints

#### 1. Android USB Host APIs und Minimum API Level

**Verwendete Android USB Host API Klassen:**
- `UsbManager` (API Level 12+): System-Service für USB-Zugriff, verfügbar ab Android 3.1
- `UsbDevice` (API Level 12+): Repräsentiert USB-Gerät, verfügbar ab Android 3.1
- `UsbDeviceConnection` (API Level 12+): Verbindung zu USB-Gerät, verfügbar ab Android 3.1
- `UsbEndpoint` (API Level 12+): USB-Endpoint für Transfers, verfügbar ab Android 3.1
- `BroadcastReceiver` mit `ACTION_USB_DEVICE_ATTACHED`/`DETACHED` (API Level 12+): Automatische Geräte-Erkennung

**Minimum API Level: minSdk 33 (Android 13)**
- USB Host API ist stabil und vollständig verfügbar ab API Level 12
- minSdk 33 gewählt für moderne Android-Versionen und bessere Security-Features
- Verhalten bei minSdk 33: Alle USB Host API Funktionen sind verfügbar und stabil
- **Verifiziert:** Android USB Host API Dokumentation bestätigt Stabilität ab API 12, keine Breaking Changes bis API 33

#### 2. Background USB Detection Requirements

**Foreground Service Requirement:**
- **Kontinuierliche/automatische Erkennung im Hintergrund erfordert Foreground Service** (ab Android 8.0+)
- Foreground Service benötigt persistente Notification (kann nicht versteckt werden)
- Lifecycle: Service muss gestartet werden bevor App in Hintergrund geht
- Permission: `FOREGROUND_SERVICE` Permission in AndroidManifest erforderlich

**Lifecycle/Permission Implications:**
- Battery-Optimization: Foreground Service kann von Battery-Optimization ausgenommen werden
- Doze-Mode: Foreground Service kann in Doze-Mode weiterlaufen (mit entsprechender Permission)
- User-Experience: Persistente Notification kann User stören, muss gut designed sein

**Alternative: Kein Foreground Service**
- USB-Detection nur wenn App im Vordergrund (keine Background-Detection)
- BroadcastReceiver funktioniert nur wenn App aktiv ist
- Vorteil: Keine persistente Notification, einfacher
- Nachteil: Verpasste Events wenn App im Hintergrund

**Entscheidung:** USB-Detection primär im Vordergrund, optional Foreground Service für Background-Detection (via Feature-Flag)

#### 3. Android Enterprise/Work-Profile Restrictions

**Multi-User und Work-Profile Einschränkungen:**
- **Work-Profile**: USB-Zugriff kann von Enterprise-Policy eingeschränkt sein
- **Multi-User**: USB-Geräte sind pro-User isoliert, keine Geräte-Sharing zwischen Usern
- **Android Enterprise**: MDM-Policies können USB-Zugriff komplett blockieren

**Erforderliche Permissions/Workarounds:**
- Keine zusätzlichen Permissions nötig (USB Host Permission reicht)
- Workaround: App prüft `UserManager.isManagedProfile()` und zeigt entsprechende Fehlermeldung
- Enterprise-Mode: Feature kann deaktiviert werden wenn Policy USB blockiert

**Testing:** Tests mit Work-Profile und Multi-User-Setup erforderlich

#### 4. Battery/Power Considerations

**Broadcast Frequency:**
- USB-Device-Attach/Detach Broadcasts sind selten (nur bei physischem An-/Abstecken)
- Broadcast-Frequenz: Typisch < 1 Event pro Minute (sehr niedrig)
- Battery-Impact: Minimal durch Broadcast-Frequenz

**Wakelock Requirements:**
- **Kein Wakelock erforderlich** für USB-Detection (BroadcastReceiver läuft ohne Wakelock)
- Optional: Partial Wakelock für USB-Transfers (nur während aktiver Transfers)

**Doze-Mode Impacts:**
- Doze-Mode blockiert BroadcastReceiver nicht (System-Broadcasts sind erlaubt)
- USB-Detection funktioniert auch in Doze-Mode
- USB-Transfers können in Doze-Mode blockiert werden (Wakelock erforderlich)

**Empfohlene Mitigationen:**
- Keine kontinuierlichen Polling-Operationen (nur Event-basiert)
- Wakelocks nur während aktiver USB-Transfers
- Battery-Optimization Whitelist für App (optional, User kann aktivieren)

#### 5. Concurrent Device Limits

**Unterstützte Concurrent-Device-Limits:**
- **Theoretisches Limit**: Android/USB-Host-Controller unterstützt typisch 127 Geräte gleichzeitig
- **Praktisches Limit**: App-Limit von **10 gleichzeitigen USB-Geräten** (konfigurierbar)
- **Overflow-Verhalten**: Bei > 10 Geräten werden nur die ersten 10 angezeigt, Rest wird geloggt aber nicht verarbeitet
- **User-Feedback**: Warnung in UI wenn Limit erreicht wird

**Konfiguration:**
- Limit konfigurierbar via `UsbProvider.MAX_CONCURRENT_DEVICES` (Standard: 10)
- Kann via Feature-Flag oder Settings angepasst werden

### Quellen und Verifikation

- **Android USB Host API Dokumentation**: https://developer.android.com/guide/topics/connectivity/usb/host
- **USB Host API Stabilität**: Verifiziert via Android Source Code und API Level History
- **Foreground Service Requirements**: Android 8.0+ Background Execution Limits
- **Work-Profile Restrictions**: Android Enterprise Documentation

## 8. Technische Details

### Automatische USB-Erkennung mit Berechtigungsabfrage

```kotlin
// UsbDeviceDetector.kt
class UsbDeviceDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbManager: UsbManager,
    private val permissionManager: UsbPermissionManager
) {
    private val _detectedDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val detectedDevices: StateFlow<List<UsbDevice>> = _detectedDevices.asStateFlow()
    
    private var isReceiverRegistered = false
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    device?.let { 
                        // Check for duplicates before appending
                        val deviceKey = DeviceKey(device.serialNumber, device.vendorId, device.productId)
                        val isDuplicate = _detectedDevices.value.any { existing ->
                            DeviceKey(existing.serialNumber, existing.vendorId, existing.productId) == deviceKey
                        }
                        
                        if (!isDuplicate) {
                            val deviceModel = convertToModel(device)
                            _detectedDevices.value = _detectedDevices.value + deviceModel
                            // Automatically request permission after detection
                            onDeviceDetected(deviceModel)
                        } else {
                            Timber.d("USB device already detected, skipping duplicate: Vendor=0x%04X, Product=0x%04X",
                                device.vendorId, device.productId)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Handles device detection by automatically requesting permission.
     * Uses UsbManager.requestPermission() and updates state based on result.
     */
    private fun onDeviceDetected(device: UsbDevice) {
        val androidDevice = usbManager.deviceList.values.find { d ->
            d.vendorId == device.vendorId && d.productId == device.productId &&
            (d.serialNumber == device.serialNumber || 
             (d.serialNumber == null && device.serialNumber == null))
        }
        
        androidDevice?.let { usbDevice ->
            if (!usbManager.hasPermission(usbDevice)) {
                Timber.d("Requesting permission for device: Vendor=0x%04X, Product=0x%04X",
                    device.vendorId, device.productId)
                permissionManager.requestPermission(usbDevice) { granted ->
                    if (granted) {
                        Timber.i("Permission granted for device: Vendor=0x%04X, Product=0x%04X",
                            device.vendorId, device.productId)
                        // Refresh device info with permission-granted data
                        refreshDeviceInfo(device.vendorId, device.productId)
                    } else {
                        Timber.w("Permission denied for device: Vendor=0x%04X, Product=0x%04X",
                            device.vendorId, device.productId)
                    }
                }
            } else {
                Timber.d("Permission already granted for device: Vendor=0x%04X, Product=0x%04X",
                    device.vendorId, device.productId)
            }
        }
    }
    
    fun registerReceiver(activity: Activity) {
        // Guard against double registration
        if (isReceiverRegistered) {
            Timber.w("BroadcastReceiver already registered, skipping")
            return
        }
        
        try {
            val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            // For Android 13+ (API 33+), use RECEIVER_NOT_EXPORTED for security
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(broadcastReceiver, filter)
            }
            isReceiverRegistered = true
            Timber.d("USB BroadcastReceiver registered successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register USB BroadcastReceiver")
            isReceiverRegistered = false
        }
    }
    
    fun unregisterReceiver() {
        // Guard against double unregistration
        if (!isReceiverRegistered) {
            Timber.w("BroadcastReceiver not registered, skipping unregister")
            return
        }
        
        try {
            context.unregisterReceiver(broadcastReceiver)
            isReceiverRegistered = false
            Timber.d("USB BroadcastReceiver unregistered successfully")
        } catch (e: IllegalArgumentException) {
            Timber.w("BroadcastReceiver was not registered")
            isReceiverRegistered = false
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering USB BroadcastReceiver")
        }
    }
    
    private data class DeviceKey(
        val serialNumber: String?,
        val vendorId: Int,
        val productId: Int
    )
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
    
    // Automatische Erkennung via StateFlow with distinctUntilChanged to prevent redundant updates
    val detectedDevices by deviceDetector.detectedDevices
        .distinctUntilChanged()
        .collectAsState()
    
    // Track processed devices to avoid race conditions and duplicate permission requests
    val processedDeviceIds = remember { mutableSetOf<String>() }
    val scope = rememberCoroutineScope()
    
    // Handle new device detections (only new additions, not full list re-emits)
    LaunchedEffect(detectedDevices) {
        val newDevices = detectedDevices.filter { device ->
            !processedDeviceIds.contains(device.uniqueId)
        }
        
        newDevices.forEach { device ->
            processedDeviceIds.add(device.uniqueId)
            
            // Check for race condition: device might already be in enumerated list
            val alreadyEnumerated = devices.any { it.uniqueId == device.uniqueId }
            if (alreadyEnumerated) {
                Timber.d("Device already in enumerated list, skipping: ${device.uniqueId}")
                return@forEach
            }
            
            // Automatically request permission after detection
            if (!permissionManager.hasPermission(device)) {
                permissionRequest = device
            } else {
                devices = devices + device
            }
        }
    }
    
    // Initiale Enumeration (only once)
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            val result = usbProvider.enumerateDevices()
            when (result) {
                is UsbResult.Success -> {
                    devices = result.data
                    // Mark enumerated devices as processed
                    result.data.forEach { processedDeviceIds.add(it.uniqueId) }
                }
                is UsbResult.Failure -> {
                    Timber.e(result.error, "Failed to enumerate USB devices")
                    // Handle error state (could show error message to user)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during USB device enumeration")
        } finally {
            isLoading = false
        }
    }
    
    // Berechtigungsdialog nach Erkennung with stable callback
    permissionRequest?.let { device ->
        UsbPermissionDialog(
            device = device,
            onGranted = {
                // Use rememberCoroutineScope for stable callback across recompositions
                scope.launch {
                    try {
                        permissionManager.requestPermission(device) { granted ->
                            if (granted) {
                                // Check again for duplicates before adding
                                if (!devices.any { it.uniqueId == device.uniqueId }) {
                                    devices = devices + device
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error requesting USB permission")
                    } finally {
                        permissionRequest = null
                    }
                }
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