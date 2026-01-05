# Modern UI Design – Material 3 Expressive

## 1. Zweck und Zielgruppe
Dieses Dokument definiert verbindliche Richtlinien für ein modernes UI auf Basis von Google Material 3 (M3) in der „Expressive“-Variante. Es richtet sich an Designer:innen, Android-Entwickler:innen und Reviewer:innen, die konsistente, zugängliche und markenkonforme Oberflächen erstellen wollen.

## 2. Leitprinzipien von Material 3 Expressive
- **Expressive Boldness:** Farben und Typografie dürfen mutig sein, solange Lesbarkeit und Kontrast gewahrt bleiben. Nutze großflächige Color Blocks und klare Typohierarchien.
- **Adaptive Design:** Dynamische Farbpaletten (Material You) passen sich dem Nutzergerät an, ohne die Markenidentität aufzugeben.
- **Human-Centered Motion:** Animationen erklären Zustände und unterstützen Orientierung, niemals als Dekoration.
- **Sicherheit & Vertrauen:** Sichtbare Hinweise auf Datenschutz, Sicherheit und Systemstatus sind Pflicht, insbesondere in Security-relevanten Screens.

## 3. Design Foundations

### 3.1 Farb-System
| Ebene | Empfehlung |
| --- | --- |
| Core Palette | Erzeuge Kernpalette aus Markenprimärfarbe (z.B. `#0041C4`) + Tertiärfarbe (Akzent). Verwende Material Theme Builder für Tones (0–100). |
| Dynamic Color | Aktiviere `dynamicColor` auf Android 12+; fallback = statisches Markenschema. |
| Surface Layering | Verwende `surfaceContainer`-Tokens für Karten/Listen, `surfaceContainerHighest` für Overlays. Transparentes Elevation-Overlay erst ab Level 3. |
| Semantic Colors | Definiere Tokens für Zustände (`success`, `warning`, `error`, `info`) mit ausreichendem Kontrast (mind. 4.5:1). |

### 3.2 Typografie
- Nutze Material 3 *Type Scale*: Display (H1/H2), Headline (H3/H4), Title, Body, Label.
- Empfohlenes Font Pairing: `Google Sans` / `Roboto` oder projektspezifische Alternative mit voller Glyphenabdeckung.
- Passe Tracking/Leading für deutschsprachige Texte an (häufig +2 Tracking bei All-Caps Labels).
- Headlines höchstens zwei Zeilen; Body-Text 16 sp (mindestens 14 sp).

### 3.3 Layout, Raster und Spacing
- Basisraster 8 dp; für feinere Komponenten 4 dp.
- Maximalbreite für Textspalten: 680 dp; darüber hinaus Split View oder mehrspaltige Layouts.
- Verwende Material Adaptive Layouts: Compact (≤600 dp), Medium (600–840 dp), Expanded (≥840 dp). Passe Navigation (Bottom/NavRail) entsprechend an.

## 4. Komponentenrichtlinien (Auswahl)

### Buttons
- Verwende primär M3-Buttons: `Filled`, `Filled Tonal`, `Outlined`, `Text`.
- Expressive Stil: `Filled Tonal` mit kräftigem Akzent für sekundäre Aktionen.
- State Handling: Default, Hover, Focus, Pressed, Disabled. Stelle sicher, dass Disabled-State ≥3:1 Kontrast behält.

### Cards & Surfaces
- Nutze `surfaceContainer`-Tokens; Elevation über `shadowColor` + Tonverschiebung.
- Large Cards dürfen heroische Imagery + Type kombinieren; Text nicht über mehr als 60% der Card-Breite.

### Navigation
- Bis 600 dp: Bottom Navigation mit 3–5 Punkten.  
- 600–840 dp: Navigation Rail.  
- ≥840 dp: Permanent Drawer oder kombinierte Rail + Drawer.
- Verwende Badges sparsam; Animation nur bei Statuswechsel.

### Dialoge & Sheets
- Bottom Sheets bevorzugt für sekundäre Aufgaben.  
- Dialoge nur für kritische Entscheidungen; Buttons klar benennen (kein „Ok/Cancel“ ohne Kontext).

## 5. Motion & Interaktion
- **Easing:** Verwende M3-Standardkurven (`Standard`, `Decelerate`, `Emphasized`). Expressive Patterns nutzen `Emphasized` für Eintrittsanimationen.
- **Duration:** Micro-Interactions 150–200 ms; Screen-Transitions 300–450 ms.
- **State Transitions:** Animierte Farb- oder Größenwechsel nur bei Fokus/Selection, nicht für reine Hover-Effekte (Android Touch).
- **Gesture Guidance:** Visualisiere Drag- oder Swipe-Richtungen (z.B. durch Container-Shift oder Icon Rotation).

## 6. Accessibility & Dark Mode
- Kontraste: Mindestens 4.5:1 (Text), 3:1 (Icons); überprüfe Dark Mode separat.
- Dynamic Type: Unterstütze bis mindestens `1.3x` (Large Text); Layout darf nicht brechen.
- Color Blind Safe: Nutze zusätzliche Indikatoren (Icons, Pattern) bei Statusfarben.
- Dark Mode: Verwende `inverseOnSurface`/`inverseSurface`-Tokens; reduziere Sättigung bei großen Flächen.
- Screen Reader: Beschreibe Icon-only Buttons (ContentDescription), kombiniere Semantik in Compose/Views.

## 7. Implementierungshinweise (Android)

### 7.1 Compose Theme Setup (Beispiel)

```kotlin

```

### 7.2 XML/View-System
- Verwende Material Components `Theme.Material3.*`.
- Aktiviere `android:forceDarkAllowed="false"` in kritischen Activities und liefere dedizierte Dark-Paletten.
- Style-Dateien nach Tokens benennen (`ColorPrimary`, `ColorSurfaceContainer`, etc.).

### 7.3 Assets & Illustrationen
- Nutze `Material Symbols` (Outlined/Rounded). Exportiere SVGs mit 24 dp Canvas.
- Illustrationen sollten variable Farben unterstützen (Dynamic Color).

## 8. QA-Checkliste
1. Stimmen Farbtöne mit Core/Dynamic Palette überein?  
2. Sind Typografie-Styles aus der M3-Type-Scale abgeleitet?  
3. Navigation adaptiert je Breakpoint?  
4. Animationen nutzen M3-Easings und angemessene Dauer?  
5. Kontraste & Screen-Reader-Texte geprüft?  
6. Dark Mode & Dynamic Color getestet (mind. 2 Geräte/Emulatoren)?  

## 9. Referenzen
- [Material 3 Guidelines](https://m3.material.io/)  
- [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/)  
- [Material Design Motion](https://m3.material.io/styles/motion/overview)  
- [Material Design Accessibility](https://m3.material.io/foundations/accessibility/overview)
- [Building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)

## 10. M3 Expressive Highlights (Addendum)
Die offiziellen Material-Texte betonen mehrere neue Patterns, die wir ergänzend berücksichtigen müssen:

### 10.1 Expressive Color Stories
- Plane Layouts in „Color Stories“ (Sections, die sich klar durch Primär-/Tertiärflächen unterscheiden) und nutze dynamische Gradienten nur dort, wo sie eine semantische Grenze markieren.  
- Kombiniere neutrale `surfaceContainer`-Layer mit kontrastreichen Akzentflächen, um wichtige Aufgaben (Security Alerts, Scan-Status) hervorzuheben.  
- Farbwechsel sollen konsequent über Theme-Tokens erfolgen, damit Dynamic Color weiterhin funktioniert.  
(vgl. [Building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive))

### 10.2 Variable Typography & Scale
- Nutze variable Fonts (z. B. Google Sans Text) für feinere Gewichtsanpassungen. Headlines können mit optischem Gewicht „Bold+“ gestaltet werden, solange Barrierefreiheit eingehalten wird.  
- Richte Typo-Cluster visuell an Card-Breite aus: Display/Text-Kombinationen dürfen die gesamte Card-Höhe einnehmen, während Body-Text maximal 60 % belegen darf.  
(vgl. [Building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive))

### 10.3 Shape Language & Adaptive Corners
- Arbeite mit Shape-Token-Serien (`extraSmall` bis `extraLarge`). M3 Expressive schlägt vor, Eckenradien als Teil der Story zu nutzen: heroische Cards → extraLarge, Toolpanels → medium.  
- Für zusammengesetzte Layouts (z. B. Split Views) dürfen angrenzende Module unterschiedliche Radien haben, solange die horizontale Achse konsistent bleibt.  
(vgl. [Building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive))

### 10.4 Motion Patterns
- Verwende „Emphasized Spring“-Bewegungen für Schlüsselinteraktionen (z. B. Scans starten/stoppen).  
- Führe Content-Transitions mit überlappenden Crossfades + Scale (max +4 dp Elevation) aus, um emotionale Highlights zu setzen.  
- Mikroanimationen (Icons, FAB) sollen in 120–160 ms abgeschlossen sein, damit Security-Flows reaktionsschnell bleiben.  
(vgl. [Building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive))

### 10.5 Layout Modules & Storyboards
- Material 3 Expressive empfiehlt „Storyboards“: Sequenzen von Screens, die gemeinsam eine visuelle Erzählung bilden. Dokumentiere pro Feature (z. B. Network Scanner) mindestens drei States (Empty, In Progress, Success/Alert).  
- Nutze modulare Layout-Blöcke (Hero, Grid, Detail, Utility). In Compose lassen sich diese als Slot-APIs modellieren (z. B. `HeroScaffold` mit `headlineSlot`, `actionSlot`).  
(vgl. [Building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive))

---
*Stand: November 2025. Bitte Änderungen über PR einreichen und hier dokumentieren.*

