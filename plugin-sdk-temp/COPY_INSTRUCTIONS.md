# Anleitung: Dateien nach Connectias-Plugins kopieren

Alle erstellten Dateien befinden sich im Ordner `/home/gerd/Schreibtisch/Connectias/plugin-sdk-temp/`

## 1. Root-Dateien für Plugin-Repository

Kopiere diese Dateien nach `/home/gerd/Schreibtisch/Connectias-Plugins/`:

```bash
# Root Build-Dateien
cp plugin-sdk-temp/build.gradle.kts Connectias-Plugins/
cp plugin-sdk-temp/settings.gradle.kts Connectias-Plugins/
cp plugin-sdk-temp/gradle.properties Connectias-Plugins/

# Version Catalog (falls noch nicht vorhanden)
cp plugin-sdk-temp/gradle/libs.versions.toml Connectias-Plugins/gradle/ 2>/dev/null || echo "libs.versions.toml bereits vorhanden"
```

## 2. Plugin SDK

Kopiere das komplette Plugin SDK:

```bash
# Plugin SDK Modul
cp -r plugin-sdk-temp/connectias-plugin-sdk Connectias-Plugins/
```

## 3. GitHub Workflows

```bash
mkdir -p Connectias-Plugins/.github/workflows
cp plugin-sdk-temp/github-workflows/*.yml Connectias-Plugins/.github/workflows/
```

## 4. Dokumentation

```bash
cp plugin-sdk-temp/README.md Connectias-Plugins/
cp plugin-sdk-temp/MIGRATION.md Connectias-Plugins/
```

## 5. Core Plugin-Service (Haupt-Repository)

Kopiere die Plugin-Service-Dateien nach `/home/gerd/Schreibtisch/Connectias/core/src/main/java/com/ble1st/connectias/core/plugin/`:

```bash
# Plugin-Service Dateien
mkdir -p Connectias/core/src/main/java/com/ble1st/connectias/core/plugin/models
cp plugin-sdk-temp/core-plugin-service/*.kt Connectias/core/src/main/java/com/ble1st/connectias/core/plugin/
cp plugin-sdk-temp/core-plugin-service/models/*.kt Connectias/core/src/main/java/com/ble1st/connectias/core/plugin/models/

# PluginModule für Dependency Injection
cp plugin-sdk-temp/core-plugin-service/PluginModule.kt Connectias/core/src/main/java/com/ble1st/connectias/core/di/
```

## 6. Haupt-Repository Anpassungen

Ersetze diese Dateien im Haupt-Repository:

```bash
# settings.gradle.kts
cp plugin-sdk-temp/main-repo-changes/settings.gradle.kts Connectias/settings.gradle.kts

# app/build.gradle.kts - manuell die Dependencies-Sektion ersetzen
# Siehe: plugin-sdk-temp/main-repo-changes/app-build.gradle.kts
```

## 7. MainActivity Integration

Füge den Code aus `plugin-sdk-temp/main-repo-changes/MainActivity-integration.kt` zur MainActivity hinzu.

## Wichtige Hinweise

- **Plugin SDK** muss zuerst kopiert werden, bevor Plugins gebaut werden können
- **Core Plugin-Service** benötigt die Interfaces aus dem Plugin SDK (kann als Kopie erstellt werden)
- **Native Libraries** müssen zur Laufzeit geladen werden - AAB muss DEX-Dateien enthalten
- **Fragment-Klassen** müssen vollständig qualifizierte Namen haben für Runtime-Loading

## Nächste Schritte

1. Alle Dateien kopieren (siehe oben)
2. Plugin SDK bauen: `./gradlew :connectias-plugin-sdk:build`
3. Erstes Plugin migrieren (z.B. barcode)
4. Plugin-Service im Core testen
5. Weitere Plugins migrieren
