# Fullscreen Plugin UI Implementation

**Datum:** 2026-01-25
**Status:** ✅ IMPLEMENTIERT
**Basierend auf:** THREE_PROCESS_UI_PLAN.md

---

## Übersicht

Die Plugin-UI wurde gemäß dem Three-Process UI Plan für **echten Vollbild-Modus** angepasst. Plugins können nun den gesamten Bildschirm nutzen, ohne TopAppBar, Padding oder andere UI-Elemente der Host-App.

## Architektur

```
┌─────────────────────────────────────────┐
│      MAIN PROCESS (MainActivity)         │
│  - PluginUIContainerFragment            │
│  - SurfaceView (zeigt UI vom UI Process)│
│  - Touch-Event-Weiterleitung            │
└─────────────────────────────────────────┘
        ↕ Surface + Touch Events
┌─────────────────────────────────────────┐
│   UI PROCESS (PluginUIActivity)         │
│   - PluginUIFragment                    │
│   - PluginUIComposable (Jetpack Compose)│
│   - VirtualDisplay Rendering            │
│   - VOLLBILD: Keine TopAppBar, kein     │
│     Padding, System-Bars versteckt      │
└─────────────────────────────────────────┘
        ↕ IPC (UIStateParcel)
┌─────────────────────────────────────────┐
│   SANDBOX PROCESS                       │
│   - Plugin Business-Logik               │
│   - UI State Management                 │
│   - Sendet UIStateParcel an UI Process  │
└─────────────────────────────────────────┘
```

## Implementierte Änderungen

### 1. PluginUIComposable.kt

**Vorher:**
```kotlin
Scaffold(
    topBar = {
        TopAppBar(title = { Text(uiState.title) })
    }
) { paddingValues ->
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp)  // Extra Padding!
    ) {
        // UI Components
    }
}
```

**Nachher:**
```kotlin
// VOLLBILD: Kein Scaffold, keine TopAppBar, kein Padding
Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // UI Components direkt ohne Container
    }
}
```

**Vorteile:**
- ✅ Plugins kontrollieren den gesamten Bildschirm
- ✅ Kein erzwungenes UI-Layout von der Host-App
- ✅ Maximale Flexibilität für Plugin-Entwickler

### 2. PluginUIActivity.kt

**Hinzugefügt:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // VOLLBILD-MODUS aktivieren
    enableEdgeToEdge()

    // System-Bars verstecken für immersive Vollbild-Erfahrung
    val windowInsetsController = ViewCompat.getWindowInsetsController(window.decorView)
    windowInsetsController?.let {
        it.hide(WindowInsetsCompat.Type.systemBars())
        it.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Screen ON während Plugin aktiv ist
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
```

**Features:**
- ✅ Edge-to-Edge Rendering (UI unter System-Bars)
- ✅ System-Bars automatisch versteckt
- ✅ Wisch-Geste zeigt System-Bars temporär
- ✅ Screen bleibt eingeschaltet

### 3. Component Renderer (Column, Row)

**Flexibles Spacing:**
```kotlin
// Plugins können Spacing via properties steuern
val spacing = component.properties.getInt("spacing", 0).dp

Column(
    verticalArrangement = if (spacing > 0)
        Arrangement.spacedBy(spacing)
    else
        Arrangement.Top
) {
    // Children
}
```

**Größen-Kontrolle:**
```kotlin
val fillMaxWidth = component.properties.getBoolean("fillMaxWidth", true)
val fillMaxHeight = component.properties.getBoolean("fillMaxHeight", false)

val modifier = when {
    fillMaxWidth && fillMaxHeight -> Modifier.fillMaxSize()
    fillMaxWidth -> Modifier.fillMaxWidth()
    fillMaxHeight -> Modifier.fillMaxHeight()
    else -> Modifier
}
```

## Plugin SDK Integration

### Vollbild-Layout erstellen

```kotlin
class MyFullscreenPlugin : IPlugin {
    override fun onRenderUI(screenId: String): UIStateParcel {
        return buildPluginUI("main") {
            // KEINE title() - kein TopAppBar

            // Root-Column für Vollbild-Layout
            column(fillMaxWidth = true, fillMaxHeight = true, spacing = 0) {
                // Header (z.B. eigene AppBar)
                row(fillMaxWidth = true, spacing = 16) {
                    text("Mein Plugin", style = TextStyle.HEADLINE)
                }

                // Content-Bereich (nimmt restlichen Platz)
                column(fillMaxWidth = true, fillMaxHeight = true, spacing = 8) {
                    // Plugin-Inhalt
                    text("Vollbild-Content")
                    button("btn1", "Action")
                }
            }
        }
    }
}
```

### Properties für Layout-Kontrolle

```kotlin
// Column mit Spacing
column(spacing = 16) { ... }  // 16dp Spacing zwischen Elementen
column(spacing = 0) { ... }   // Kein Spacing (Standard)

// Größen-Kontrolle
column(fillMaxWidth = true, fillMaxHeight = true) { ... }  // Vollbild
column(fillMaxWidth = true, fillMaxHeight = false) { ... } // Nur Breite
```

## Migration für bestehende Plugins

### Alte Plugins (mit title)

**Vorher:**
```kotlin
buildPluginUI("main") {
    title("Mein Plugin")  // TopAppBar erscheint
    button("btn1", "Click me")
}
```

**Problem:** TopAppBar wird nicht mehr gerendert!

**Migration:**
```kotlin
buildPluginUI("main") {
    // Keine title() mehr!

    // Stattdessen: Eigene Header-Row erstellen
    row(fillMaxWidth = true, spacing = 16) {
        text("Mein Plugin", style = TextStyle.HEADLINE)
    }

    column(spacing = 8) {
        button("btn1", "Click me")
    }
}
```

### Plugins ohne title

**Vorher:**
```kotlin
buildPluginUI("main") {
    button("btn1", "Click me")
}
```

**Nachher:**
```kotlin
// Funktioniert weiterhin, aber ohne automatisches Padding
buildPluginUI("main") {
    // TIPP: Root-Column für Spacing hinzufügen
    column(spacing = 8) {
        button("btn1", "Click me")
    }
}
```

## Vorteile der Vollbild-Implementierung

1. **Immersive Erfahrung**
   - Plugins nutzen den gesamten Bildschirm
   - Keine ablenkenden UI-Elemente der Host-App

2. **Maximale Flexibilität**
   - Plugins kontrollieren ihr eigenes Layout vollständig
   - Keine erzwungenen Layouts oder Abstände

3. **Bessere Performance**
   - Weniger UI-Container = weniger Compose-Overhead
   - Direktes Rendering ohne Scaffold-Wrapper

4. **Konsistenz mit Plan**
   - Implementierung folgt THREE_PROCESS_UI_PLAN.md
   - Echte Prozess-Isolation bleibt erhalten

## Bekannte Einschränkungen

### System-Bars bei Gesten

- System-Bars sind versteckt, erscheinen aber bei Wisch-Geste
- Verhalten: `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`
- Bars verschwinden automatisch nach 3 Sekunden

### FAB-Overlay

- Das FAB (Floating Action Button) der MainActivity bleibt über der Plugin-UI
- Z-Order wird kontinuierlich erzwungen (alle 100ms)
- Plugin-UI rendert darunter im SurfaceView

## Testing

### Manueller Test

1. Plugin mit Vollbild-UI erstellen
2. Plugin in MainActivity öffnen
3. Prüfen:
   - ✅ Keine TopAppBar
   - ✅ Kein Padding
   - ✅ System-Bars versteckt
   - ✅ UI nutzt gesamten Bildschirm
   - ✅ FAB bleibt sichtbar

### Test-Plugin

```kotlin
class FullscreenTestPlugin : IPlugin {
    override fun onRenderUI(screenId: String): UIStateParcel {
        return buildPluginUI("main") {
            column(fillMaxWidth = true, fillMaxHeight = true, spacing = 0) {
                // Header mit Farbe für visuellen Test
                row(
                    fillMaxWidth = true,
                    backgroundColor = "#FF0000",  // Rot
                    padding = 16
                ) {
                    text("Vollbild Test", style = TextStyle.HEADLINE)
                }

                // Mittlerer Bereich
                column(
                    fillMaxWidth = true,
                    fillMaxHeight = true,
                    spacing = 16,
                    padding = 16
                ) {
                    text("Dieser Bereich sollte den gesamten Bildschirm nutzen.")
                    button("test", "Test Button")
                }

                // Footer
                row(
                    fillMaxWidth = true,
                    backgroundColor = "#0000FF",  // Blau
                    padding = 16
                ) {
                    text("Footer", style = TextStyle.CAPTION)
                }
            }
        }
    }
}
```

## Nächste Schritte

### Optional: Konfigurierbare Modi

Plugins könnten optional zwischen Vollbild und normalem Modus wechseln:

```kotlin
// In PluginMetadata
data class PluginMetadata(
    // ...
    val fullscreenMode: Boolean = true  // Standard: Vollbild
)
```

### Optional: Toolbar-Support

Für Plugins, die eine TopAppBar benötigen:

```kotlin
// Neue Component-Type: TOOLBAR
toolbar(
    title = "Mein Plugin",
    showBackButton = true,
    actions = listOf(...)
)
```

---

**Zusammenfassung:**
Die Plugin-UI ist nun vollständig für Vollbild-Modus konfiguriert. Plugins haben maximale Kontrolle über ihr Layout und können den gesamten Bildschirm nutzen. Die Implementierung folgt dem THREE_PROCESS_UI_PLAN.md und erhält die Prozess-Isolation bei.
