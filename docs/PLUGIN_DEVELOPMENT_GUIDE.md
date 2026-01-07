# Connectias Plugin Development Guide

## Übersicht

Das Connectias Plugin-System ermöglicht es, die App-Funktionalität durch dynamisch geladene Plugins zu erweitern.

## Plugin-Architektur

### Plugin-Typen
- **APK-Plugins**: Vollständige Android-Anwendungen mit DEX-Code
- **JAR-Plugins**: Java/Kotlin-Bibliotheken (experimentell)

### Plugin-Struktur

```
plugin.apk
├── classes.dex              # Kompilierter Kotlin/Java-Code
├── assets/
│   └── plugin-manifest.json # Plugin-Metadaten
├── lib/
│   └── arm64-v8a/          # Native Bibliotheken (optional)
│       └── libplugin.so
└── res/                     # Ressourcen (optional)
```

## Plugin-Manifest

Jedes Plugin benötigt eine `plugin-manifest.json` im `assets/`-Verzeichnis:

```json
{
  "pluginId": "com.example.myplugin",
  "pluginName": "My Plugin",
  "version": "1.0.0",
  "author": "Developer Name",
  "description": "Plugin description",
  "category": "UTILITY",
  "minApiLevel": 33,
  "maxApiLevel": 36,
  "minAppVersion": "1.0",
  "fragmentClassName": "com.example.myplugin.MyPlugin",
  "permissions": [],
  "dependencies": [],
  "nativeLibraries": []
}
```

### Kategorien
- `UTILITY` - Werkzeuge
- `MEDIA` - Medien
- `NETWORK` - Netzwerk
- `SECURITY` - Sicherheit
- `SYSTEM` - System

## Plugin-Entwicklung

### 1. Plugin-Interface implementieren

```kotlin
package com.example.myplugin

import com.ble1st.connectias.plugin.sdk.IPlugin
import com.ble1st.connectias.plugin.sdk.PluginContext
import com.ble1st.connectias.plugin.sdk.PluginMetadata

class MyPlugin : IPlugin {
    
    private var context: PluginContext? = null
    
    override fun getMetadata(): PluginMetadata {
        return PluginMetadata(
            pluginId = "com.example.myplugin",
            pluginName = "My Plugin",
            version = "1.0.0",
            author = "Developer",
            description = "My awesome plugin",
            category = PluginCategory.UTILITY,
            minApiLevel = 33,
            maxApiLevel = 36,
            minAppVersion = "1.0",
            fragmentClassName = "com.example.myplugin.MyPlugin",
            permissions = emptyList(),
            dependencies = emptyList(),
            nativeLibraries = emptyList()
        )
    }
    
    override fun onLoad(context: PluginContext): Boolean {
        this.context = context
        context.logDebug("Plugin loaded")
        return true
    }
    
    override fun onEnable(): Boolean {
        context?.logDebug("Plugin enabled")
        return true
    }
    
    override fun onDisable(): Boolean {
        context?.logDebug("Plugin disabled")
        return true
    }
    
    override fun onUnload(): Boolean {
        context?.logDebug("Plugin unloaded")
        context = null
        return true
    }
}
```

### 2. build.gradle.kts konfigurieren

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.myplugin"
    compileSdk = 35
    
    defaultConfig {
        applicationId = "com.example.myplugin"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // Plugin SDK (provided by host app)
    compileOnly(files("libs/connectias-plugin-sdk.jar"))
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
}
```

### 3. Plugin kompilieren

```bash
./gradlew assembleDebug
```

Die APK befindet sich dann in: `build/outputs/apk/debug/plugin-debug.apk`

## Plugin-Installation

### Methode 1: Über Plugin Management UI
1. App öffnen
2. FAB → Extensions → Plugin Management
3. Plus-Button (+) drücken
4. Plugin-APK auswählen

### Methode 2: Programmatisch
```kotlin
val pluginFile = File("/path/to/plugin.apk")
pluginManager.importPlugin(Uri.fromFile(pluginFile))
```

### Methode 3: Assets (für eingebaute Plugins)
1. Plugin-APK in `app/src/main/assets/plugins/` kopieren
2. App neu kompilieren
3. Plugin wird beim Start automatisch geladen

## Sicherheitsanforderungen

### Android 10+ Anforderungen
- Plugin-Dateien müssen **read-only** sein
- Keine beschreibbaren DEX-Dateien erlaubt
- Das System setzt automatisch die korrekten Permissions

### Permissions
Plugins können folgende Permissions anfordern:
- Standard Android-Permissions
- Host-App muss diese Permissions bereits haben

## Plugin-Lifecycle

```
App Start
    ↓
PluginManager.initialize()
    ↓
Plugin aus Verzeichnis laden
    ↓
IPlugin.onLoad(context) → Boolean
    ↓
[Benutzer aktiviert Plugin]
    ↓
IPlugin.onEnable() → Boolean
    ↓
[Plugin ist aktiv]
    ↓
[Benutzer deaktiviert Plugin]
    ↓
IPlugin.onDisable() → Boolean
    ↓
[App wird beendet]
    ↓
IPlugin.onUnload() → Boolean
```

## PluginContext API

Das `PluginContext`-Interface bietet Zugriff auf Host-App-Funktionen:

```kotlin
interface PluginContext {
    // App-Context
    fun getApplicationContext(): Context
    
    // Plugin-Datenverzeichnis
    fun getPluginDirectory(): File
    
    // Service-Registry
    fun registerService(name: String, service: Any)
    fun getService(name: String): Any?
    
    // Logging
    fun logDebug(message: String)
    fun logError(message: String, throwable: Throwable? = null)
}
```

## Best Practices

### 1. Fehlerbehandlung
```kotlin
override fun onLoad(context: PluginContext): Boolean {
    return try {
        // Initialisierung
        true
    } catch (e: Exception) {
        context.logError("Failed to load", e)
        false
    }
}
```

### 2. Ressourcen-Cleanup
```kotlin
override fun onUnload(): Boolean {
    // Alle Ressourcen freigeben
    services.clear()
    listeners.clear()
    context = null
    return true
}
```

### 3. Thread-Safety
```kotlin
private val data = ConcurrentHashMap<String, Any>()
```

### 4. Timeout-Beachtung
- `onLoad`: 10 Sekunden
- `onEnable/onDisable/onUnload`: 5 Sekunden
- Lange Operationen in Background-Threads ausführen

## Debugging

### Logcat filtern
```bash
adb logcat | grep PluginManager
adb logcat | grep "com.example.myplugin"
```

### Plugin-Verzeichnis prüfen
```bash
adb shell ls -la /data/data/com.ble1st.connectias/files/plugins/
```

### Plugin-Permissions prüfen
```bash
adb shell stat /data/data/com.ble1st.connectias/files/plugins/plugin.apk
```

## Bekannte Einschränkungen

1. **AAR-Dateien werden nicht unterstützt** - Nur APK/JAR mit DEX-Code
2. **Keine dynamischen Features** - Plugins müssen eigenständig sein
3. **Host-App-Abhängigkeiten** - Plugin SDK muss kompatibel sein
4. **Keine UI-Navigation** - Plugins können keine eigenen Activities starten

## Troubleshooting

### "Writable dex file is not allowed"
**Lösung**: Datei auf read-only setzen
```bash
chmod 444 plugin.apk
```

### "Failed to find entry 'classes.dex'"
**Lösung**: Plugin als Application (APK) statt Library (AAR) kompilieren

### "ClassNotFoundException"
**Lösung**: 
- FragmentClassName im Manifest prüfen
- Package-Name korrekt?
- DEX-Datei enthält Klasse?

## Support

Bei Fragen oder Problemen:
- GitHub Issues: https://github.com/Ble1st/Connectias/issues
- Dokumentation: https://connectias.dev/docs/plugins
