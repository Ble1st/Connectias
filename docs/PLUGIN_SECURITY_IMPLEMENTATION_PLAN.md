# 🔒 Connectias Plugin System - Security & Implementation Plan

**Erstellt:** 2026-01-07  
**Status:** Planning Phase  
**Ziel:** Sicheres Runtime-Plugin-System mit vollständiger Integration

---

## 📋 Executive Summary

Das aktuelle Plugin-System in `plugin-sdk-temp/` ist architektonisch gut strukturiert, aber **nicht produktionsreif**. Es fehlen kritische Sicherheitskomponenten, funktionierende ClassLoader-Implementierungen und UI-Integration. Dieser Plan definiert alle fehlenden Elemente und deren Implementierungsreihenfolge.

---

## 🚨 Kritische Sicherheitslücken (BLOCKER)

### 1. **Fehlende Signatur-/Integritätsprüfung**
- **Status:** ❌ Nicht implementiert
- **Risiko:** Malicious Plugins können ohne Prüfung geladen werden
- **Aktuell:** `PluginValidator` prüft nur Dateiformat und Manifest-Felder
- **Datei:** `plugin-sdk-temp/core-plugin-service/PluginValidator.kt:20-155`

### 2. **Keine Permission-Enforcement**
- **Status:** ⚠️ Nur Logging, keine Blockierung
- **Risiko:** Plugins können gefährliche Berechtigungen ohne User-Consent nutzen
- **Aktuell:** Warnung in Logs, aber kein User-Dialog oder Reject
- **Datei:** `plugin-sdk-temp/core-plugin-service/PluginValidator.kt:123-145`

### 3. **Unsicherer ClassLoader (Android-inkompatibel)**
- **Status:** ❌ URLClassLoader funktioniert nicht auf Android
- **Risiko:** Plugins können nicht geladen werden; wenn doch, unsicher
- **Aktuell:** URLClassLoader statt DexClassLoader, keine DEX-Extraktion
- **Datei:** `plugin-sdk-temp/core-plugin-service/PluginManager.kt:92-95`

### 4. **Fehlende Sandbox/Isolation**
- **Status:** ❌ Nicht implementiert
- **Risiko:** Plugin-Crashes können App crashen, unbegrenzter Ressourcenzugriff
- **Aktuell:** Plugins laufen im selben Prozess ohne Isolation

### 5. **Native Library Loading unsicher**
- **Status:** ❌ NativeLibraryManager fehlt komplett
- **Risiko:** .so-Dateien können nicht kontrolliert geladen werden
- **Aktuell:** Referenziert aber nicht implementiert
- **Datei:** `plugin-sdk-temp/core-plugin-service/PluginManager.kt:108`

---

## 🔧 Fehlende Core-Komponenten

### A. **PluginContextImpl** (CRITICAL)
**Datei:** Fehlt - muss erstellt werden als `core-plugin-service/PluginContextImpl.kt`

**Erforderliche Funktionen:**
```kotlin
class PluginContextImpl(
    private val appContext: Context,
    private val pluginDir: File,
    private val nativeLibManager: INativeLibraryManager,
    private val serviceRegistry: MutableMap<String, Any>
) : PluginContext {
    override fun getApplicationContext(): Context
    override fun getPluginDirectory(): File
    override fun getNativeLibraryManager(): INativeLibraryManager
    override fun registerService(name: String, service: Any)
    override fun getService(name: String): Any?
    override fun logDebug(message: String)
    override fun logError(message: String, throwable: Throwable?)
}
```

**Abhängigkeiten:**
- Service-Registry für Plugin-zu-Plugin-Kommunikation
- Isoliertes Plugin-Verzeichnis pro Plugin-ID
- Logging-Integration mit Timber

---

### B. **NativeLibraryManager** (CRITICAL)
**Datei:** Fehlt - muss erstellt werden als `core-plugin-service/NativeLibraryManager.kt`

**Erforderliche Funktionen:**
```kotlin
class NativeLibraryManager : INativeLibraryManager {
    // ABI-Prüfung (arm64-v8a, armeabi-v7a, x86_64, x86)
    // Laden aus Plugin-spezifischem lib-Verzeichnis
    // Fehlerbehandlung für fehlende ABIs
    // Tracking geladener Libraries
    
    override suspend fun loadLibrary(libraryName: String, libraryPath: File): Result<Unit>
    override suspend fun unloadLibrary(libraryName: String): Result<Unit>
    override fun isLoaded(libraryName: String): Boolean
    override fun getLoadedLibraries(): List<String>
}
```

**Besonderheiten:**
- Android kann .so nicht entladen (JVM-Limitation) - nur Tracking
- ABI-Matching: `Build.SUPPORTED_ABIS` prüfen
- Kopie nach `/data/data/com.ble1st.connectias/app_plugins/<plugin-id>/lib/<abi>/`

---

### C. **DexClassLoader-Integration** (CRITICAL)
**Datei:** `plugin-sdk-temp/core-plugin-service/PluginManager.kt:92-95` ersetzen

**Aktuell (FALSCH):**
```kotlin
val classLoader = URLClassLoader(
    arrayOf(pluginFile.toURI().toURL()),
    context.classLoader
)
```

**Erforderlich:**
```kotlin
// 1. DEX-Extraktion
val dexOutputDir = File(context.codeCacheDir, "plugins/${metadata.pluginId}")
dexOutputDir.mkdirs()

// 2. DexClassLoader
val classLoader = DexClassLoader(
    pluginFile.absolutePath,           // APK/JAR mit classes.dex
    dexOutputDir.absolutePath,         // Optimized DEX output
    null,                              // Native lib path (optional)
    context.classLoader                // Parent ClassLoader
)
```

**AAB-Handling:**
- Entweder: Nur APK/JAR unterstützen (einfacher)
- Oder: bundletool-Integration für AAB → APK-Extraktion (komplex)

---

### D. **Plugin Signature Validator** (HIGH PRIORITY)
**Datei:** Neue Klasse `core-plugin-service/PluginSignatureValidator.kt`

**Funktionen:**
```kotlin
class PluginSignatureValidator(private val trustedKeys: List<PublicKey>) {
    
    fun validateSignature(pluginFile: File): Result<Boolean> {
        // 1. APK-Signatur extrahieren (PackageManager)
        // 2. Gegen trustedKeys prüfen
        // 3. Zertifikat-Chain validieren
    }
    
    fun validateHash(pluginFile: File, expectedHash: String): Result<Boolean> {
        // SHA-256 Hash des Plugin-Files
    }
}
```

**Trusted Keys Management:**
- Hardcoded Public Key für offizielle Connectias-Plugins
- Optional: User-definierte Trust-Store
- Reject bei fehlender/ungültiger Signatur

---

### E. **Permission Consent Manager** (HIGH PRIORITY)
**Datei:** Neue Klasse `core-plugin-service/PluginPermissionManager.kt`

**Funktionen:**
```kotlin
class PluginPermissionManager(private val context: Context) {
    
    suspend fun requestPermissionConsent(
        pluginMetadata: PluginMetadata
    ): Result<Boolean> {
        // 1. Gefährliche Permissions identifizieren
        // 2. User-Dialog anzeigen (Compose/AlertDialog)
        // 3. User-Entscheidung speichern
        // 4. Bei Reject: Plugin nicht laden
    }
    
    fun getDangerousPermissions(permissions: List<String>): List<String>
    fun isPermissionAllowed(pluginId: String, permission: String): Boolean
}
```

**Gefährliche Permissions (Whitelist):**
- `READ/WRITE_EXTERNAL_STORAGE`
- `ACCESS_FINE_LOCATION`
- `CAMERA`, `RECORD_AUDIO`
- `READ/WRITE_CONTACTS`
- `READ/SEND_SMS`
- `CALL_PHONE`

---

### F. **Plugin Import Service** (MEDIUM PRIORITY)
**Datei:** Neue Klasse `core-plugin-service/PluginImportService.kt`

**Funktionen:**
```kotlin
class PluginImportService(
    private val context: Context,
    private val pluginDirectory: File,
    private val validator: PluginValidator,
    private val signatureValidator: PluginSignatureValidator
) {
    
    suspend fun importFromExternalPath(
        sourcePath: String  // z.B. /Schreibtisch/Connectias-Plugins/plugin.apk
    ): Result<File> {
        // 1. Datei-Validierung (Größe, Extension)
        // 2. Hash-Prüfung
        // 3. Signatur-Prüfung
        // 4. Kopie nach filesDir/plugins/
        // 5. Return kopierte Datei
    }
    
    suspend fun importViaSAF(): Result<File> {
        // Storage Access Framework für User-Auswahl
    }
}
```

---

### G. **Plugin Dependency Resolver** (MEDIUM PRIORITY)
**Datei:** Neue Klasse `core-plugin-service/PluginDependencyResolver.kt`

**Funktionen:**
```kotlin
class PluginDependencyResolver(private val pluginManager: PluginManager) {
    
    fun resolveDependencies(metadata: PluginMetadata): Result<List<String>> {
        // 1. Dependencies aus Metadata lesen
        // 2. Prüfen ob Dependencies geladen/enabled
        // 3. Lade-Reihenfolge berechnen (topological sort)
        // 4. Return sortierte Plugin-IDs
    }
    
    fun checkDependenciesLoaded(pluginId: String): Boolean
}
```

**Integration:**
- Vor `enablePlugin()` Dependencies prüfen
- Bei fehlenden Dependencies: Queue oder Reject

---

### H. **PluginService Erweiterungen** (MEDIUM PRIORITY)
**Datei:** `plugin-sdk-temp/core-plugin-service/PluginService.kt` erweitern

**Fehlende Methoden:**
```kotlin
// Aktuell fehlt:
suspend fun enablePlugin(pluginId: String): Result<Unit>
suspend fun disablePlugin(pluginId: String): Result<Unit>
suspend fun importPlugin(sourcePath: String): Result<PluginInfo>
fun getEnabledPlugins(): List<PluginInfo>
```

**Integration mit PluginManager:**
- Service delegiert an Manager, aber mit zusätzlicher Validierung
- Permission-Check vor enable
- Dependency-Check vor enable

---

## 🎨 UI/Navigation-Integration (REQUIRED)

### I. **MainActivity Plugin-Integration**
**Datei:** `app/src/main/java/com/ble1st/connectias/MainActivity.kt`

**Erforderliche Änderungen:**

1. **Inject PluginService:**
```kotlin
@Inject
lateinit var pluginService: PluginService
```

2. **Initialize in onCreate:**
```kotlin
lifecycleScope.launch {
    pluginService.initialize().onSuccess {
        Timber.i("PluginService initialized")
        
        // Auto-enable alle geladenen Plugins
        pluginService.getLoadedPlugins().forEach { pluginInfo ->
            pluginService.enablePlugin(pluginInfo.metadata.pluginId)
        }
        
        // Registriere in ModuleRegistry für Navigation
        registerPluginsInModuleRegistry()
    }
}
```

3. **Shutdown in onDestroy:**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    pluginService.shutdown()
}
```

---

### J. **Plugin Navigation/Compose Integration**
**Datei:** Neue Klasse `app/src/main/java/com/ble1st/connectias/ui/PluginNavigationManager.kt`

**Funktionen:**
```kotlin
class PluginNavigationManager(
    private val pluginService: PluginService,
    private val navController: NavController
) {
    
    fun registerPluginDestinations() {
        // 1. Alle enabled Plugins holen
        // 2. Fragment-ClassName aus Metadata
        // 3. Dynamische Navigation-Destination erstellen
        // 4. Icon/Name aus Metadata
    }
    
    fun getPluginMenuItems(): List<PluginMenuItem>
}

data class PluginMenuItem(
    val pluginId: String,
    val name: String,
    val icon: ImageVector,
    val navId: Int
)
```

**Integration in FabWithBottomSheet:**
- Plugin-Items dynamisch zur Feature-Liste hinzufügen
- Icon-Mapping aus PluginMetadata.category

---

## 📦 Build & Deployment

### K. **Plugin Build Pipeline**
**Ort:** `/Schreibtisch/Connectias-Plugins/` (separates Repo)

**Erforderlich:**
1. **Gradle-Task für Plugin-Packaging:**
```gradle
task packagePlugin(type: Jar) {
    // 1. Compile Kotlin/Java
    // 2. Package als APK mit classes.dex
    // 3. Include plugin-manifest.json im Root
    // 4. Include native libs in lib/<abi>/
    // 5. Sign mit Debug/Release Key
}
```

2. **Plugin-Manifest Generator:**
```kotlin
// Automatisch aus @ConnectiasPlugin Annotation
// Generiert plugin-manifest.json
```

3. **Native Library Inclusion:**
- Rust .so-Dateien nach `src/main/jniLibs/<abi>/`
- Automatisches Packaging in APK

---

## 🔄 Implementierungsreihenfolge (Phasen)

### **Phase 1: Core Security (BLOCKER)** ⏱️ 2-3 Tage
1. ✅ PluginContextImpl erstellen
2. ✅ NativeLibraryManager implementieren
3. ✅ DexClassLoader-Integration (URLClassLoader ersetzen)
4. ✅ PluginSignatureValidator erstellen
5. ✅ PluginPermissionManager erstellen

**Deliverable:** Plugins können sicher geladen werden

---

### **Phase 2: Import & Validation** ⏱️ 1-2 Tage
6. ✅ PluginImportService implementieren
7. ✅ PluginValidator erweitern (Signatur-Check integrieren)
8. ✅ PluginDependencyResolver implementieren
9. ✅ PluginService um enable/disable/import erweitern

**Deliverable:** Plugins können importiert und validiert werden

---

### **Phase 3: UI Integration** ⏱️ 2-3 Tage
10. ✅ MainActivity Plugin-Integration
11. ✅ PluginNavigationManager implementieren
12. ✅ FabWithBottomSheet erweitern (Plugin-Items)
13. ✅ Plugin-Management-UI (Liste, Enable/Disable, Uninstall)

**Deliverable:** Plugins sind in der App sichtbar und nutzbar

---

### **Phase 4: Build Pipeline** ⏱️ 1-2 Tage
14. ✅ Gradle-Tasks für Plugin-Packaging
15. ✅ Manifest-Generator aus Annotations
16. ✅ Signing-Integration
17. ✅ Test-Plugin erstellen (Barcode-Example migrieren)

**Deliverable:** Plugins können gebaut und deployed werden

---

### **Phase 5: Testing & Hardening** ⏱️ 2-3 Tage
18. ✅ Unit-Tests für alle Core-Komponenten
19. ✅ Integration-Tests (Plugin Load/Enable/Disable)
20. ✅ Security-Audit (Penetration Testing)
21. ✅ Performance-Tests (Memory Leaks, ClassLoader Cleanup)

**Deliverable:** Produktionsreifes Plugin-System

---

## 🎯 Akzeptanzkriterien

### Sicherheit
- ✅ Nur signierte Plugins können geladen werden
- ✅ Gefährliche Permissions erfordern User-Consent
- ✅ Plugin-Crashes crashen nicht die App
- ✅ Native Libraries werden ABI-korrekt geladen

### Funktionalität
- ✅ Plugins können zur Laufzeit importiert werden
- ✅ Plugins erscheinen in Navigation/UI
- ✅ Plugin-Dependencies werden aufgelöst
- ✅ Enable/Disable funktioniert ohne App-Neustart

### Performance
- ✅ Plugin-Load < 500ms (ohne native libs)
- ✅ Kein Memory Leak bei Unload
- ✅ ClassLoader wird korrekt geschlossen

---

## 📊 Risiken & Mitigationen

| Risiko | Wahrscheinlichkeit | Impact | Mitigation |
|--------|-------------------|--------|------------|
| DexClassLoader funktioniert nicht mit AAB | Hoch | Hoch | Nur APK/JAR unterstützen, AAB später |
| Native Library ABI-Mismatch | Mittel | Hoch | ABI-Prüfung vor Load, Fallback-Mechanismus |
| Plugin-Crashes crashen App | Hoch | Kritisch | Try-Catch in allen Plugin-Calls, Crash-Isolation |
| Signatur-Bypass | Niedrig | Kritisch | Code-Review, Security-Audit |
| Performance-Probleme bei vielen Plugins | Mittel | Mittel | Lazy-Loading, Plugin-Limit |

---

## 📝 Offene Fragen

1. **AAB vs. APK:** Sollen AAB-Plugins unterstützt werden oder nur APK/JAR?
   - **Empfehlung:** Start mit APK/JAR, AAB später via bundletool

2. **Plugin-Sandboxing:** Separate Prozesse oder im Main-Process?
   - **Empfehlung:** Start im Main-Process, später optional separate Process

3. **Plugin-Updates:** Automatisch oder manuell?
   - **Empfehlung:** Manuell mit Notification, später Auto-Update-Option

4. **Plugin-Store:** GitHub Releases oder eigener Server?
   - **Empfehlung:** GitHub Releases (bereits in GitHubPluginDownloadManager)

5. **Trust-Model:** Nur offizielle Plugins oder auch Third-Party?
   - **Empfehlung:** Start nur offizielle, später User-Trust-Store

---

## 📚 Referenzen

- **Architektur-Spec:** `/gggggg/_Connectias_Plugin_System_Architecture`
- **Aktueller Code:** `/plugin-sdk-temp/`
- **Android DexClassLoader:** [Android Docs](https://developer.android.com/reference/dalvik/system/DexClassLoader)
- **APK Signature Scheme:** [Android Docs](https://source.android.com/docs/security/features/apksigning)

---

## ✅ Nächste Schritte

1. **Review dieses Plans** mit Team
2. **Priorisierung** der Phasen bestätigen
3. **Phase 1 starten:** PluginContextImpl + NativeLibraryManager
4. **Wöchentliche Reviews** nach jeder Phase

---

**Erstellt von:** Cascade AI  
**Letzte Aktualisierung:** 2026-01-07
