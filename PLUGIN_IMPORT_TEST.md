# Plugin-Import Testanleitung

## Voraussetzungen

1. **Plugin erstellen:**
   ```bash
   cd /home/gerd/PluginTest
   ./gradlew clean build
   ```
   
   Das erstellt: `/home/gerd/PluginTest/build/plugin/hello-plugin-1.0.0.zip`

2. **App bauen:**
   ```bash
   cd /home/gerd/AndroidStudioProjects/Connectias
   ./gradlew :app:assembleDebug
   ```

## Plugin auf Gerät kopieren

```bash
adb push /home/gerd/PluginTest/build/plugin/hello-plugin-1.0.0.zip /sdcard/Download/
```

## Test durchführen

1. **App starten**
2. **Plugins-Tab öffnen** (untere Navigation)
3. **Plus-Button (+)** drücken
4. **Datei-Picker öffnet sich**
5. **hello-plugin-1.0.0.zip** aus Downloads auswählen
6. **Logs überwachen:**

```bash
adb logcat -s "Timber:*" "*:E"
```

## Erwartete Log-Ausgabe (Erfolg)

```
D/MainActivity: Plugin file selected: content://...
D/PluginInstallationViewModel: Starting plugin installation
D/PluginInstallationViewModel: URI = content://...
D/PluginInstallationViewModel: Calling pluginManager.installPlugin()
D/PluginManager: === PLUGIN IMPORT START ===
D/PluginManager: URI: content://...
D/PluginManager: Step 1: Copying plugin file from URI
D/copyPluginFile: Starting file copy from URI: content://...
D/copyPluginFile: Input stream opened successfully
D/copyPluginFile: Plugin directory: /data/user/0/.../files/plugins
D/copyPluginFile: Target file: /data/user/0/.../files/plugins/plugin_XXX.zip
D/copyPluginFile: Starting file copy...
D/copyPluginFile: Copied XXXX bytes
D/copyPluginFile: File copy completed. Final size: XXXX bytes
D/PluginManager: Plugin file copied to: /data/user/0/.../files/plugins/plugin_XXX.zip
D/PluginManager: Plugin file exists: true
D/PluginManager: Plugin file size: XXXX bytes
D/PluginManager: Step 2: Extracting plugin manifest
D/extractPluginInfo: Opening ZIP file: ...
D/extractPluginInfo: Looking for plugin.json in ZIP
D/extractPluginInfo: Found plugin.json, size: 327 bytes
D/extractPluginInfo: Raw JSON content:
D/extractPluginInfo: {
  "id": "com.example.hello",
  "name": "Hello World",
  ...
}
D/extractPluginInfo: Parsing JSON fields...
D/extractPluginInfo: Basic fields parsed:
D/extractPluginInfo:   - ID: com.example.hello
D/extractPluginInfo:   - Name: Hello World
D/extractPluginInfo:   - Version: 1.0.0
D/extractPluginInfo:   - Description: Simple demo plugin...
D/extractPluginInfo:   - Author: Test Developer
D/extractPluginInfo: Parsing permissions...
D/extractPluginInfo: Found 1 permissions
D/extractPluginInfo: Processing permission: SYSTEM_INFO
D/extractPluginInfo: Permission converted successfully: SYSTEM_INFO
D/extractPluginInfo: Optional fields:
D/extractPluginInfo:   - Min Core Version: 1.0.0
D/extractPluginInfo:   - Max Core Version: 2.0.0
D/extractPluginInfo:   - Entry Point: com.example.helloplugin.SimplePlugin
D/extractPluginInfo: PluginInfo object created successfully
D/PluginManager: Plugin manifest extracted successfully:
D/PluginManager:   - ID: com.example.hello
D/PluginManager:   - Name: Hello World
D/PluginManager:   - Version: 1.0.0
D/PluginManager:   - Author: Test Developer
D/PluginManager:   - Permissions: [SYSTEM_INFO]
D/PluginManager:   - Entry Point: com.example.helloplugin.SimplePlugin
D/PluginManager: Step 3: Saving plugin to database
I/PluginDatabaseManager: Saving plugin: Hello World (com.example.hello)
I/PluginManager: === PLUGIN IMPORT SUCCESS ===
I/PluginManager: Plugin installed successfully: Hello World
I/PluginInstallationViewModel: Plugin installation successful
I/PluginInstallationViewModel: Plugin ID = com.example.hello
I/PluginInstallationViewModel: Plugin Name = Hello World
I/MainActivity: Plugin installation result: Success
```

## Bei Fehler

Suchen Sie nach:
- `E/PluginManager: === PLUGIN IMPORT FAILED ===`
- Exception-Stack-Traces
- Fehlermeldungen in `extractPluginInfo`

## Plugin-Struktur überprüfen

```bash
unzip -l /home/gerd/PluginTest/build/plugin/hello-plugin-1.0.0.zip
```

Sollte zeigen:
```
plugin.json
hello-plugin.jar
```

## plugin.json Inhalt

```json
{
  "id": "com.example.hello",
  "name": "Hello World",
  "version": "1.0.0",
  "description": "Simple demo plugin that shows basic functionality",
  "author": "Test Developer",
  "permissions": ["SYSTEM_INFO"],
  "minCoreVersion": "1.0.0",
  "maxCoreVersion": "2.0.0",
  "entryPoint": "com.example.helloplugin.SimplePlugin"
}
```

