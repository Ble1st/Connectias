# Migration Guide: Feature-Module zu Plugins

Dieses Dokument beschreibt die Migration der Feature-Module aus dem Haupt-Repository in das Plugin-Repository.

## Migrierte Module

- ✅ `feature-barcode` → `connectias-plugin-barcode`
- ⏳ `feature-bluetooth` → `connectias-plugin-bluetooth`
- ⏳ `feature-calendar` → `connectias-plugin-calendar`
- ⏳ `feature-deviceinfo` → `connectias-plugin-deviceinfo`
- ⏳ `feature-dnstools` → `connectias-plugin-dnstools`
- ⏳ `feature-dvd` → `connectias-plugin-dvd`
- ⏳ `feature-network` → `connectias-plugin-network`
- ⏳ `feature-ntp` → `connectias-plugin-ntp`
- ⏳ `feature-password` → `connectias-plugin-password`
- ⏳ `feature-scanner` → `connectias-plugin-scanner`
- ⏳ `feature-secure-notes` → `connectias-plugin-secure-notes`

## Migration-Schritte

### 1. Ordner umbenennen

```bash
feature-{name} → connectias-plugin-{name}
```

### 2. Build-Datei anpassen

**Vorher:**
```kotlin
namespace = "com.ble1st.connectias.feature.{name}"
dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
}
```

**Nachher:**
```kotlin
namespace = "com.ble1st.connectias.plugin.{name}"
dependencies {
    implementation(project(":connectias-plugin-sdk"))
    // KEINE Dependencies zu :core oder :common!
}
```

### 3. Package-Struktur anpassen

Alle Dateien müssen umbenannt werden:
- `com.ble1st.connectias.feature.{name}` → `com.ble1st.connectias.plugin.{name}`

### 4. Plugin-Implementierung erstellen

Neue Datei: `{Name}Plugin.kt`

```kotlin
@ConnectiasPlugin(
    id = "{plugin_id}",
    name = "{Plugin Name}",
    version = "1.0.0",
    author = "Ble1st",
    category = "UTILITY"
)
class {Name}Plugin : IPlugin {
    // Implementierung
}
```

### 5. Plugin-Manifest erstellen

`src/main/resources/plugin-manifest.json`

```json
{
  "pluginId": "{plugin_id}",
  "pluginName": "{Plugin Name}",
  "version": "1.0.0",
  "fragmentClassName": "com.ble1st.connectias.plugin.{name}.ui.{Name}Fragment",
  "..."
}
```

## Breaking Changes

- **Keine direkten Dependencies** zu `:core` oder `:common`
- **Runtime Loading** - Plugins werden zur Laufzeit geladen
- **Package-Namen** müssen geändert werden
- **Fragment-Klassen** müssen vollständig qualifizierte Namen haben

## Native Libraries

Für Plugins mit Rust/C++ Code:
- Native Libraries müssen zur Laufzeit geladen werden
- Verwende `INativeLibraryManager` aus dem Plugin SDK
- Libraries werden in `src/main/jniLibs/` oder zur Laufzeit kopiert
