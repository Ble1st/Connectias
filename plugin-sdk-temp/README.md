# Connectias Plugins Repository

Dieses Repository enthält alle Plugins für die Connectias App. Plugins werden zur Laufzeit geladen und können über GitHub Releases verteilt werden.

## Struktur

```
Connectias-Plugins/
├─ connectias-plugin-sdk/          # Plugin SDK (Interfaces, Annotations)
├─ connectias-plugin-barcode/       # Barcode/QR Code Plugin
├─ connectias-plugin-bluetooth/     # Bluetooth Scanner Plugin
├─ connectias-plugin-network/       # Network Tools Plugin
└─ ... (weitere Plugins)
```

## Plugin SDK

Das Plugin SDK (`connectias-plugin-sdk`) definiert die Basis-Interfaces für alle Plugins:

- `IPlugin` - Haupt-Interface für Plugins
- `PluginMetadata` - Metadaten-Struktur
- `PluginContext` - Context für Plugin-Zugriff (inkl. Messaging-APIs)
- `INativeLibraryManager` - Native Library Loading

### Inter-Plugin Messaging

Plugins können über das Messaging-System miteinander kommunizieren:

```kotlin
class MyPlugin : IPlugin {
    override fun onLoad(context: PluginContext): Boolean {
        // Register message handler
        context.registerMessageHandler("DATA_REQUEST") { message ->
            // Process request and return response
            val processed = processData(message.payload)
            MessageResponse.success(message.requestId, processed)
        }
        return true
    }
    
    override fun onEnable(): Boolean {
        // Send message to another plugin
        lifecycleScope.launch {
            val response = context.sendMessageToPlugin(
                receiverId = "other-plugin",
                messageType = "DATA_REQUEST",
                payload = "Request data".toByteArray()
            )
            response.onSuccess { 
                // Handle response
            }
        }
        return true
    }
}
```

### API Rate Limiting

Alle IPC-Methoden sind durch Rate-Limiting geschützt:
- `loadPlugin`: 1/sec, 10/min
- `enablePlugin`: 2/sec, 20/min
- `ping`: 60/sec, 600/min
- `getLoadedPlugins`: 10/sec, 100/min

Bei Überschreitung wird eine `RateLimitException` geworfen.

## Plugin erstellen

1. Neues Modul erstellen: `connectias-plugin-{name}/`
2. `build.gradle.kts` mit Plugin SDK Dependency erstellen
3. `IPlugin` implementieren
4. `plugin-manifest.json` erstellen
5. Fragment-Klasse für UI erstellen

## Build

```bash
./gradlew :connectias-plugin-{name}:bundleRelease
```

Output: `build/outputs/bundle/release/connectias-plugin-{name}-release.aab`

## Distribution

Plugins werden über GitHub Releases verteilt:
- Release Tag: `v{version}-{plugin-name}`
- Assets: `.aab` Datei, `plugin-manifest.json`, `sha256sum.txt`

## Migration von Feature-Modulen

Feature-Module aus dem Haupt-Repository müssen migriert werden:

1. Package-Struktur ändern: `feature.{name}` → `plugin.{name}`
2. Namespace ändern: `com.ble1st.connectias.feature.{name}` → `com.ble1st.connectias.plugin.{name}`
3. Dependencies zu `:core` und `:common` entfernen (werden zur Laufzeit geladen)
4. Plugin SDK als Dependency hinzufügen
5. `IPlugin` Implementierung erstellen
6. `plugin-manifest.json` erstellen
