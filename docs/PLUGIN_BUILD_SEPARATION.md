# 🔧 Plugin-System: Build-Separation

**Datum:** 2026-01-07  
**Prinzip:** App lädt Plugins, baut sie NICHT

---

## 📋 Architektur-Prinzip

Das Plugin-System folgt einer **strikten Trennung**:

```
┌─────────────────────────────────────────────────────────────┐
│                    Connectias App                            │
│                                                              │
│  ✅ Lädt kompilierte Plugins (.apk/.jar)                    │
│  ✅ Verwaltet Plugin-Lifecycle                              │
│  ✅ Bietet Sandbox-Prozess                                  │
│  ❌ Baut KEINE Plugins                                      │
│  ❌ Benötigt KEIN Plugin-SDK                                │
│                                                              │
│  Komponenten:                                                │
│  - PluginSandboxService (läuft in :plugin_sandbox)         │
│  - PluginSandboxProxy (IPC-Kommunikation)                  │
│  - AIDL Interface (IPluginSandbox)                         │
│  - Parcelable-Klassen (für IPC)                            │
└─────────────────────────────────────────────────────────────┘

                          │
                          │ Lädt kompilierte .apk/.jar
                          ▼

┌─────────────────────────────────────────────────────────────┐
│              Separater Plugin-Ordner                         │
│           (plugin-sdk-temp/)                                 │
│                                                              │
│  ✅ Plugin-SDK für Entwickler                               │
│  ✅ Beispiel-Plugins                                        │
│  ✅ Build-Skripte für Plugins                               │
│  ❌ NICHT Teil der App                                      │
│                                                              │
│  Struktur:                                                   │
│  - connectias-plugin-sdk/        (SDK-Bibliothek)          │
│  - core-plugin-service/          (Service-Implementierung)  │
│  - connectias-plugin-barcode-example/ (Beispiel)           │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 Verzeichnis-Struktur

### **Connectias App (Haupt-Repository)**

```
Connectias/
├── app/                          # Android App
│   ├── src/main/
│   │   ├── AndroidManifest.xml   # Enthält PluginSandboxService
│   │   └── java/
│   │       └── com/ble1st/connectias/
│   │           ├── MainActivity.kt
│   │           └── ui/
│   │               ├── PluginManagementScreen.kt
│   │               ├── PluginPermissionDialog.kt
│   │               └── PluginNotificationManager.kt
│   └── build.gradle.kts
│
├── core/                         # Core-Module
│   ├── src/main/java/
│   │   └── com/ble1st/connectias/core/
│   │       ├── security/
│   │       └── module/
│   └── build.gradle.kts
│
├── common/                       # Gemeinsame Utilities
├── feature-settings/             # Settings-Feature
│
└── docs/                         # Dokumentation
    ├── PLUGIN_SYSTEM_FINAL.md
    ├── PLUGIN_SANDBOX_IMPLEMENTATION.md
    └── PLUGIN_BUILD_SEPARATION.md  # Diese Datei
```

**Wichtig:** Die App enthält **KEINE** Plugin-SDK-Klassen!

---

### **Plugin-SDK (Separater Ordner)**

```
plugin-sdk-temp/                  # SEPARATER Ordner für Plugin-Entwicklung
│
├── connectias-plugin-sdk/        # SDK-Bibliothek für Plugin-Entwickler
│   ├── src/main/kotlin/
│   │   └── com/ble1st/connectias/plugin/
│   │       ├── IPlugin.kt        # Plugin-Interface
│   │       ├── PluginMetadata.kt # Metadaten-Klassen
│   │       ├── PluginContext.kt  # Context-Interface
│   │       └── native/
│   │           └── INativeLibraryManager.kt
│   └── build.gradle.kts
│
├── core-plugin-service/          # Service-Implementierung (für Sandbox)
│   ├── PluginSandboxService.kt   # Wird in App kopiert
│   ├── PluginSandboxProxy.kt     # Wird in App kopiert
│   ├── IPluginSandbox.aidl       # Wird in App kopiert
│   ├── PluginMetadataParcel.kt   # Wird in App kopiert
│   ├── PluginResultParcel.kt     # Wird in App kopiert
│   └── SandboxPluginContext.kt   # Wird in App kopiert
│
└── connectias-plugin-barcode-example/  # Beispiel-Plugin
    ├── src/main/
    │   ├── kotlin/
    │   │   └── com/ble1st/plugins/barcode/
    │   │       └── BarcodePlugin.kt
    │   ├── assets/
    │   │   └── plugin-manifest.json
    │   └── AndroidManifest.xml
    └── build.gradle.kts
```

---

## 🔄 Workflow

### **1. Plugin-Entwicklung (Separater Ordner)**

```bash
cd plugin-sdk-temp/

# Plugin bauen
./gradlew :connectias-plugin-barcode-example:assembleRelease

# Output:
# connectias-plugin-barcode-example/build/outputs/apk/release/plugin-barcode.apk
```

### **2. Plugin-Installation (in App)**

```bash
# Plugin in App-Verzeichnis kopieren
adb push plugin-barcode.apk /sdcard/Download/

# In App:
# - Öffne Plugin-Management
# - Wähle "Plugin importieren"
# - Wähle plugin-barcode.apk
# - Plugin wird nach /data/data/com.ble1st.connectias/files/plugins/ kopiert
```

### **3. Plugin-Laden (zur Laufzeit)**

```kotlin
// In MainActivity
pluginService.initialize()  // Scannt /files/plugins/
pluginService.enablePlugin("barcode_scanner")

// Sandbox-Prozess lädt Plugin:
// 1. DexClassLoader lädt plugin-barcode.apk
// 2. Instanziiert BarcodePlugin
// 3. Ruft onLoad() auf
// 4. Plugin läuft in :plugin_sandbox Prozess
```

---

## 🚫 Was die App NICHT enthält

Die Connectias-App enthält **NICHT**:

- ❌ `IPlugin.kt` - Plugin-Interface
- ❌ `PluginMetadata.kt` - Metadaten-Klassen
- ❌ `PluginContext.kt` - Context-Interface
- ❌ Plugin-SDK-Bibliothek
- ❌ Plugin-Build-Skripte
- ❌ Beispiel-Plugins

**Warum?** Die App **lädt** nur kompilierte Plugins, sie **baut** sie nicht!

---

## ✅ Was die App ENTHÄLT

Die Connectias-App enthält **NUR**:

- ✅ `PluginSandboxService.kt` - Service zum Laden von Plugins
- ✅ `PluginSandboxProxy.kt` - IPC-Kommunikation
- ✅ `IPluginSandbox.aidl` - AIDL Interface
- ✅ `PluginMetadataParcel.kt` - Serialisierung für IPC
- ✅ `PluginResultParcel.kt` - Result-Wrapper für IPC
- ✅ `SandboxPluginContext.kt` - Minimaler Context für Sandbox
- ✅ UI-Komponenten (Management, Permissions, Notifications)

**Diese Dateien wurden aus `plugin-sdk-temp/core-plugin-service/` kopiert!**

---

## 📦 Plugin-Format (zur Laufzeit)

Plugins sind **kompilierte APK-Dateien**:

```
plugin-barcode.apk
├── classes.dex                    # Kompilierter Kotlin/Java-Code
├── plugin-manifest.json           # Metadaten
├── lib/                           # Native Libraries (optional)
│   ├── arm64-v8a/libbarcode.so
│   └── armeabi-v7a/libbarcode.so
└── META-INF/                      # APK-Signatur
    ├── MANIFEST.MF
    ├── CERT.SF
    └── CERT.RSA
```

Die App lädt diese APK zur Laufzeit mit `DexClassLoader`.

---

## 🔧 Build-Prozess

### **App bauen (ohne Plugins)**

```bash
cd Connectias/
./gradlew assembleDebug

# Output:
# app/build/outputs/apk/debug/app-debug.apk
```

**Wichtig:** Die App baut **KEINE** Plugins!

### **Plugin bauen (separater Prozess)**

```bash
cd plugin-sdk-temp/
./gradlew :connectias-plugin-barcode-example:assembleRelease

# Output:
# connectias-plugin-barcode-example/build/outputs/apk/release/plugin-barcode.apk
```

**Zwei separate Build-Prozesse!**

---

## 🎯 Vorteile dieser Trennung

1. **Saubere Separation** ✅
   - App-Code und Plugin-SDK sind getrennt
   - Keine Vermischung von Laufzeit und Build-Zeit

2. **Kleinere App** ✅
   - App enthält kein Plugin-SDK
   - Reduzierte APK-Größe

3. **Flexibilität** ✅
   - Plugins können unabhängig entwickelt werden
   - Verschiedene Plugin-Versionen möglich

4. **Sicherheit** ✅
   - App kann Plugins validieren
   - Keine Build-Zeit-Abhängigkeiten

5. **Wartbarkeit** ✅
   - Klare Verantwortlichkeiten
   - Einfachere Updates

---

## 📝 Integration in Connectias

### **Dateien, die in die App kopiert werden müssen:**

Aus `plugin-sdk-temp/core-plugin-service/`:

```bash
# Diese Dateien manuell in die App kopieren:
cp plugin-sdk-temp/core-plugin-service/PluginSandboxService.kt \
   app/src/main/java/com/ble1st/connectias/plugin/

cp plugin-sdk-temp/core-plugin-service/PluginSandboxProxy.kt \
   app/src/main/java/com/ble1st/connectias/plugin/

cp plugin-sdk-temp/core-plugin-service/src/main/aidl/com/ble1st/connectias/plugin/IPluginSandbox.aidl \
   app/src/main/aidl/com/ble1st/connectias/plugin/

cp plugin-sdk-temp/core-plugin-service/PluginMetadataParcel.kt \
   app/src/main/java/com/ble1st/connectias/plugin/

cp plugin-sdk-temp/core-plugin-service/PluginResultParcel.kt \
   app/src/main/java/com/ble1st/connectias/plugin/

cp plugin-sdk-temp/core-plugin-service/SandboxPluginContext.kt \
   app/src/main/java/com/ble1st/connectias/plugin/
```

**Wichtig:** Nur diese 6 Dateien werden benötigt!

---

## ✅ Zusammenfassung

### **Prinzip:**
- **App** = Plugin-Loader (Laufzeit)
- **plugin-sdk-temp/** = Plugin-Builder (Build-Zeit)

### **Trennung:**
- App enthält **NUR** Laufzeit-Komponenten
- Plugin-SDK ist **SEPARAT** für Entwickler

### **Workflow:**
1. Plugin-Entwickler nutzt `plugin-sdk-temp/`
2. Baut Plugin zu `.apk`
3. User installiert Plugin in App
4. App lädt Plugin zur Laufzeit in Sandbox

---

**Implementiert von:** Cascade AI  
**Datum:** 2026-01-07  
**Prinzip:** Strikte Trennung von Laufzeit und Build-Zeit
