# Now in Android Features - Implementierungsguide

Dieses Dokument beschreibt die neu hinzugefügten Features aus der Now in Android (NiA) Referenz-App.

## 📋 Übersicht

Basierend auf der Analyse von Now in Android wurden folgende Features zu Connectias hinzugefügt:

### ✅ Implementiert

1. **Roborazzi Screenshot-Testing** - Visuelle Regressionstests für UIs
2. **Navigation3 mit Adaptive Layouts** - Optimierte Navigation für Tablets/Foldables
3. **Dependency Guard** - Schutz vor unerwarteten Dependency-Änderungen

### ✅ Bereits vorhanden (vor dieser Implementation)

- Baseline Profiles (vollständig konfiguriert)
- Convention Plugins (7 Plugins aktiv)
- Dynamic Color/Theming (mit Transition-Animationen)
- WorkManager Integration

---

## 🎨 1. Roborazzi Screenshot-Testing

### Was ist Roborazzi?

Roborazzi ist ein Screenshot-Testing-Framework für Jetpack Compose, das es ermöglicht:
- Visuelle Regressionstests ohne echtes Gerät
- Automatische Vergleiche zwischen Screenshots
- CI/CD Integration

### Konfiguration

**gradle/libs.versions.toml:**
```toml
roborazzi = "1.51.0"
robolectric = "4.16"
```

**gradle.properties:**
```properties
roborazzi.test.verify=true
roborazzi.test.record=false
```

### Verwendung

#### 1. Screenshots aufnehmen
```bash
./gradlew recordRoborazziDebug
```

#### 2. Screenshots verifizieren
```bash
./gradlew verifyRoborazziDebug
```

#### 3. Vergleichsbilder erstellen (bei Fehlern)
```bash
./gradlew compareRoborazziDebug
```

### Beispiel-Test

Siehe: `app/src/test/java/com/ble1st/connectias/ui/PluginListScreenshotTest.kt`

```kotlin
@Test
fun pluginList_lightTheme() {
    composeTestRule.setContent {
        ConnectiasTheme(darkTheme = false) {
            PluginListScreen()
        }
    }

    composeTestRule.onRoot()
        .captureRoboImage("screenshots/plugin_list_light.png")
}
```

### Screenshot-Speicherort

Screenshots werden hier gespeichert:
```
app/src/test/screenshots/
├── plugin_list_light.png
├── plugin_list_dark.png
├── plugin_detail_light.png
└── ...
```

### Best Practices

1. **Screenshots committen**: Screenshots ins Git einchecken für CI/CD
2. **Konsistente Namen**: Beschreibende Namen für Screenshots verwenden
3. **Multiple Themes**: Teste Light/Dark/Dynamic Themes
4. **CI Integration**: In GitHub Actions einbinden

### CI/CD Integration

```yaml
# .github/workflows/test.yml
- name: Run Screenshot Tests
  run: ./gradlew verifyRoborazziDebug

- name: Upload Screenshot Diffs
  if: failure()
  uses: actions/upload-artifact@v4
  with:
    name: screenshot-diffs
    path: '**/build/outputs/roborazzi/'
```

---

## 📱 2. Navigation3 mit Adaptive Layouts

### Was sind Adaptive Layouts?

Navigation3 mit Material 3 Adaptive ermöglicht:
- **List-Detail-Pattern** für Tablets/Foldables
- **Automatische Anpassung** an Bildschirmgröße
- **Verschiedene Postures** (Laptop, Tablet, Handheld)

### Dependencies

```kotlin
implementation(libs.androidx.navigation3.runtime)
implementation(libs.androidx.navigation3.ui)
implementation(libs.androidx.compose.material3.adaptive)
implementation(libs.androidx.compose.material3.adaptive.layout)
implementation(libs.androidx.compose.material3.adaptive.navigation)
implementation(libs.androidx.compose.material3.adaptive.navigation3)
```

### Verwendung

#### Beispiel: Plugin-Liste mit Detail-Ansicht

Siehe: `app/src/main/java/com/ble1st/connectias/ui/adaptive/PluginAdaptiveNavigation.kt`

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PluginListDetailScreen(
    plugins: List<PluginItem>,
    selectedPluginId: String?,
    onPluginSelected: (String) -> Unit
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = { PluginListPane(...) },
        detailPane = { PluginDetailPane(...) }
    )
}
```

### Verhalten nach Bildschirmgröße

| Gerät | Verhalten |
|-------|-----------|
| **Handy (compact)** | Liste und Detail nacheinander |
| **Tablet (medium)** | Liste + Detail nebeneinander |
| **Foldable (expanded)** | Liste + Detail nebeneinander |

### Integration in bestehende Navigation

```kotlin
// In MainActivity oder NavHost
PluginListDetailScreen(
    plugins = pluginViewModel.availablePlugins,
    selectedPluginId = pluginViewModel.selectedPluginId,
    onPluginSelected = { id -> 
        pluginViewModel.selectPlugin(id)
    }
)
```

---

## 🛡️ 3. Dependency Guard

### Was ist Dependency Guard?

Dependency Guard schützt vor:
- Unerwarteten Dependency-Upgrades
- Transitive Dependency-Änderungen
- Breaking Changes in Dependencies

### Konfiguration

**app/build.gradle.kts:**
```kotlin
plugins {
    alias(libs.plugins.dependency.guard)
}
```

### Verwendung

#### 1. Baseline erstellen
```bash
./gradlew :app:dependencyGuard
```

Dies generiert: `app/dependencies/dependencies.txt`

#### 2. Änderungen prüfen
```bash
./gradlew :app:dependencyGuard
```

Bei Änderungen schlägt der Build fehl!

#### 3. Baseline aktualisieren (nach Review)
```bash
./gradlew :app:dependencyGuardBaseline
```

### CI/CD Integration

```yaml
# .github/workflows/android-checks.yml
- name: Check Dependencies
  run: ./gradlew dependencyGuard
```

### Best Practices

1. **Baseline committen**: `dependencies.txt` ins Git
2. **Review Process**: Dependency-Änderungen im PR reviewen
3. **Regelmäßig aktualisieren**: Monatliche Dependency-Updates

---

## 🎨 4. Compose Compiler Metrics

### Was sind Compose Compiler Metrics?

Detaillierte Analyse der Compose-Compiler-Performance:
- **Stability Reports** - Welche Composables sind stabil/instabil
- **Composition Metrics** - Performance-Zahlen für @Composable Funktionen
- **Module Metrics** - Gesamt-Statistiken pro Modul

### Konfiguration

**app/build.gradle.kts:**
```kotlin
composeCompiler {
    if (project.findProperty("enableComposeCompilerReports") == "true") {
        val metricsDir = layout.buildDirectory.dir("compose-metrics").get().asFile
        metricsOutputDirectory.set(metricsDir)
        
        val reportsDir = layout.buildDirectory.dir("compose-reports").get().asFile
        reportsOutputDirectory.set(reportsDir)
    }
}
```

### Verwendung

```bash
# Metrics generieren
./gradlew assembleDebug -PenableComposeCompilerReports=true

# Reports anschauen
ls -la app/build/compose-reports/
ls -la app/build/compose-metrics/
```

### Reports-Typen

1. **`*-classes.txt`** - Liste aller Composables mit Stabilität
2. **`*-composables.txt`** - Detaillierte Composable-Informationen
3. **`*-composables.csv`** - CSV für Analyse-Tools
4. **`*-module.json`** - Modul-Statistiken

### Optimierung

Instabile Composables optimieren:
```kotlin
// Vorher (instabil)
@Composable
fun MyComposable(data: MutableList<String>) { ... }

// Nachher (stabil)
@Composable
fun MyComposable(data: List<String>) { ... }
```

---

## 🚀 Quick Start

### 1. Roborazzi Screenshots erstellen

```bash
# 1. Ersten Screenshot aufnehmen
./gradlew recordRoborazziDebug

# 2. Code ändern

# 3. Screenshots verifizieren
./gradlew verifyRoborazziDebug
```

### 2. Adaptive Navigation testen

```bash
# Auf verschiedenen Bildschirmgrößen testen
# - Handy: Pixel 6
# - Tablet: Pixel Tablet
# - Foldable: Pixel Fold
```

### 3. Dependencies schützen

```bash
# Baseline erstellen (bereits ausgeführt!)
./gradlew :app:dependencyGuard

# Prüfen ob Änderungen vorliegen
./gradlew :app:dependencyGuard

# Bei beabsichtigten Änderungen: Baseline aktualisieren
./gradlew :app:dependencyGuardBaseline

# Baselines committen
git add app/dependencies/*.txt
git commit -m "chore: Update dependency guard baseline"
```

### 4. Compose Metrics analysieren

```bash
# Metrics generieren
./gradlew assembleDebug -PenableComposeCompilerReports=true

# Reports öffnen
cat app/build/compose-reports/*-composables.txt
```

---

## 📊 Vergleich zu Now in Android

| Feature | Connectias | Now in Android | Status |
|---------|------------|----------------|--------|
| **Roborazzi** | ✅ NEU | ✅ | Implementiert |
| **Navigation3** | ✅ NEU | ✅ | Implementiert |
| **Adaptive Layouts** | ✅ NEU | ✅ | Implementiert |
| **Dependency Guard** | ✅ NEU | ✅ | Implementiert |
| **Baseline Profiles** | ✅ | ✅ | Bereits vorhanden |
| **Convention Plugins** | ✅ 7 Plugins | ✅ 9+ Plugins | Bereits vorhanden |
| **Dynamic Color** | ✅ | ✅ | Bereits vorhanden |
| **WorkManager** | ✅ | ✅ | Bereits vorhanden |

---

## 🎯 Nächste Schritte

### Kurzfristig (1-2 Wochen)

1. **Screenshot-Tests erweitern**
   - Tests für alle Plugin-UIs erstellen
   - Hardware Bridge UI testen
   - Settings-Screens testen

2. **Adaptive Navigation ausbauen**
   - Security-Checks mit List-Detail
   - Log-Viewer mit Adaptive Layout
   - Settings mit Two-Pane

### Mittelfristig (1 Monat)

1. **Test-Coverage erhöhen**
   - Ziel: 80% Coverage für UI-Module
   - Integration mit Jacoco

2. **CI/CD optimieren**
   - Screenshot-Tests in CI
   - Dependency Guard automatisch prüfen

### Langfristig (3 Monate)

1. **Performance-Optimierung**
   - Baseline Profiles für alle kritischen Pfade
   - Compose Compiler Metrics analysieren

2. **Accessibility**
   - Roborazzi Accessibility-Checks
   - TalkBack-Kompatibilität testen

---

## 📚 Ressourcen

### Dokumentation

- [Roborazzi GitHub](https://github.com/takahirom/roborazzi)
- [Material 3 Adaptive](https://developer.android.com/develop/ui/compose/layouts/adaptive)
- [Navigation3](https://developer.android.com/guide/navigation/navigation3)
- [Dependency Guard](https://github.com/dropbox/dependency-guard)

### Now in Android Referenzen

- [NiA GitHub Repository](https://github.com/android/nowinandroid)
- [Architecture Learning Journey](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md)
- [Modularization Guide](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md)

---

## 🐛 Troubleshooting

### Roborazzi-Tests schlagen fehl

**Problem**: Screenshots unterscheiden sich auf verschiedenen Plattformen

**Lösung**:
```bash
# Linux-Screenshots als Baseline verwenden (wie NiA)
# Auf main-Branch:
./gradlew recordRoborazziDebug

# Dann committen
git add app/src/test/screenshots/
```

### Navigation3 Build-Fehler

**Problem**: "Cannot find androidx.navigation3"

**Lösung**: Gradle Sync durchführen:
```bash
./gradlew --refresh-dependencies
```

### Dependency Guard schlägt fehl

**Problem**: "Dependencies have changed"

**Lösung**:
1. Änderungen reviewen in `app/dependencies/dependencies.txt`
2. Falls beabsichtigt: `./gradlew :app:dependencyGuardBaseline`
3. Falls unbeabsichtigt: Dependencies in `build.gradle.kts` korrigieren

---

**Erstellt**: Januar 2025  
**Basierend auf**: Now in Android v1.0.0  
**Connectias Version**: 1.0.0
