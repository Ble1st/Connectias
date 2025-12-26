# Adeptus Mechanicus Theme - Design Guidelines und Implementierung

**Version:** 1.0  
**Erstellt:** Dezember 2025  
**Status:** Design-Phase

---

## Inhaltsverzeichnis

1. [Überblick](#überblick)
2. [Design-Konzept](#design-konzept)
3. [Farbpalette](#farbpalette)
4. [Typografie](#typografie)
5. [Terminologie-Mapping](#terminologie-mapping)
6. [UI-Elemente und Komponenten](#ui-elemente-und-komponenten)
7. [Technische Umsetzung](#technische-umsetzung)
8. [Implementierungs-Schritte](#implementierungs-schritte)
9. [Beispiele](#beispiele)
10. [Best Practices](#best-practices)

---

## Überblick

Das **Adeptus Mechanicus Theme** ist ein optionales visuelles und terminologisches Theme für die Connectias-App, das die Benutzeroberfläche im Stil des Adeptus Mechanicus aus Warhammer 40.000 gestaltet. Das Theme kann über die Einstellungen aktiviert oder deaktiviert werden.

### Kernprinzipien

- **Optional**: Standardmäßig ist die App im normalen Modus
- **Umschaltbar**: Aktivierung über Einstellungen jederzeit möglich
- **Vollständig**: Betrifft Farben, Typografie, Terminologie und UI-Elemente
- **Konsistent**: Einheitliches Design-System für alle Screens
- **Funktional**: Keine Änderung der Funktionalität, nur Präsentation

### Zielgruppe

Das Theme richtet sich an:
- Warhammer 40.000 Enthusiasten
- Nutzer, die eine technisch-futuristische Ästhetik bevorzugen
- Power-User, die eine immersive Erfahrung suchen

---

## Design-Konzept

### Visuelle Identität

Das Adeptus Mechanicus Theme vermittelt:
- **Technokratische Mystik**: Verbindung von Technologie und Ritual
- **Industrielle Ästhetik**: Dunkle, metallische Farben
- **Gotische Technologie**: Futuristisch, aber mit historischen Elementen
- **Maschinelle Präzision**: Klare Strukturen, technische Terminologie

### Design-Philosophie

1. **Maschinenseele als Metapher**: Alle technischen Komponenten werden als "Machine Spirits" bezeichnet
2. **Riten statt Aktionen**: Funktionen werden zu "Rites" (Riten)
3. **Heiligkeit der Technologie**: Technische Begriffe werden sakralisiert
4. **Korruption als Bedrohung**: Fehler und Sicherheitsprobleme werden als "Heresy" (Ketzerei) dargestellt

---

## Farbpalette

### Primärfarben

#### Dunkles Rot (Mars-Rot)
- **Hex**: `#8B0000`
- **RGB**: `rgb(139, 0, 0)`
- **Verwendung**: Primäre Akzentfarbe, Überschriften, wichtige UI-Elemente
- **Bedeutung**: Mars, der heilige Planet des Adeptus Mechanicus

#### Bronze/Messing
- **Hex**: `#CD7F32`
- **RGB**: `rgb(205, 127, 50)`
- **Verwendung**: Sekundäre Akzentfarbe, Borders, Icons
- **Bedeutung**: Metallische Technologie, Rituale

#### Warnrot/Orange
- **Hex**: `#FF4500`
- **RGB**: `rgb(255, 69, 0)`
- **Verwendung**: Fehler, Warnungen, kritische Zustände
- **Bedeutung**: Gefahr, Korruption, Heresy

### Hintergrundfarben

#### Tiefes Schwarz
- **Hex**: `#0A0A0A`
- **RGB**: `rgb(10, 10, 10)`
- **Verwendung**: Haupt-Hintergrund
- **Bedeutung**: Leere des Weltraums, Technologie-Dunkelheit

#### Dunkles Rot-Schwarz
- **Hex**: `#1A0A0A`
- **RGB**: `rgb(26, 10, 10)`
- **Verwendung**: Gradient-Hintergründe, Cards
- **Bedeutung**: Subtile Mars-Tönung

#### Dunkles Grau-Schwarz
- **Hex**: `#1A1A1A`
- **RGB**: `rgb(26, 26, 26)`
- **Verwendung**: Card-Hintergründe, Container
- **Bedeutung**: Industrielle Oberflächen

#### Rot-getöntes Grau
- **Hex**: `#2A1A1A`
- **RGB**: `rgb(42, 26, 26)`
- **Verwendung**: Hover-States, aktive Elemente
- **Bedeutung**: Erwärmte Metalloberflächen

### Textfarben

#### Altweiß/Beige
- **Hex**: `#F5F5DC`
- **RGB**: `rgb(245, 245, 220)`
- **Verwendung**: Primärer Text, Hauptinhalt
- **Bedeutung**: Alte Dokumente, Pergament

#### Gedämpftes Grau
- **Hex**: `#B8B8A0`
- **RGB**: `rgb(184, 184, 160)`
- **Verwendung**: Sekundärer Text, Beschreibungen
- **Bedeutung**: Abgenutzte Oberflächen

### Statusfarben

#### Erfolg/Verifiziert (Grün)
- **Hex**: `#10B981`
- **RGB**: `rgb(16, 185, 129)`
- **Verwendung**: Erfolgreiche Operationen, verifizierte Zustände
- **Bedeutung**: Maschinenseele zufrieden, Ritus erfolgreich

#### Warnung (Amber)
- **Hex**: `#F59E0B`
- **RGB**: `rgb(245, 158, 11)`
- **Verwendung**: Warnungen, mittlere Bedrohungen
- **Bedeutung**: Aufmerksamkeit erforderlich

#### Fehler/Kritisch (Rot)
- **Hex**: `#DC2626`
- **RGB**: `rgb(220, 38, 38)`
- **Verwendung**: Kritische Fehler, Heresy erkannt
- **Bedeutung**: Maschinenseele verärgert, Korruption

---

## Typografie

### Schriftarten

#### Primärschrift: Orbitron
- **Verwendung**: Überschriften, Titel, wichtige Labels
- **Stil**: Futuristisch, technisch, geometrisch
- **Gewichte**: Regular (400), Bold (700), Black (900)
- **Charakteristik**: Vermittelt technologische Präzision

#### Sekundärschrift: Rajdhani
- **Verwendung**: Body-Text, Beschreibungen, normale Inhalte
- **Stil**: Technisch, leicht gotisch, gut lesbar
- **Gewichte**: Light (300), Regular (400), SemiBold (600), Bold (700)
- **Charakteristik**: Lesbar, aber mit technischem Charakter

#### Alternative: Monospace (Fallback)
- **Verwendung**: Code, technische Daten, wenn Custom Fonts nicht verfügbar
- **Stil**: System-Monospace-Schriftart
- **Charakteristik**: Technisch, präzise

### Typografie-Regeln

#### Überschriften
- **H1**: Orbitron, 2.5em, Bold (900), UPPERCASE, Letter-Spacing: 3px
- **H2**: Orbitron, 2em, Bold (900), UPPERCASE, Letter-Spacing: 3px
- **H3**: Orbitron, 1.5em, Bold (700), UPPERCASE, Letter-Spacing: 2px
- **H4**: Orbitron, 1.2em, Bold (700), UPPERCASE, Letter-Spacing: 1px

#### Body-Text
- **Groß**: Rajdhani, 1.1em, Regular (400), Normal Case
- **Normal**: Rajdhani, 1em, Regular (400), Normal Case
- **Klein**: Rajdhani, 0.9em, Regular (400), Normal Case

#### Labels und Buttons
- **Button-Text**: Orbitron, 1em, Bold (700), UPPERCASE, Letter-Spacing: 1.5px
- **Label-Text**: Orbitron, 0.9em, SemiBold (600), UPPERCASE, Letter-Spacing: 1px

#### Spezielle Texte
- **Status-Badges**: Orbitron, 0.85em, Bold (700), UPPERCASE, Letter-Spacing: 1px
- **Technische Werte**: Orbitron, 1.5em, Bold (700), Normal Case
- **Beschreibungen**: Rajdhani, 0.95em, Regular (400), Italic, Normal Case

### Text-Effekte

#### Schatten
- **Überschriften**: Text-Shadow mit Primärfarbe (z.B. `0 0 20px rgba(139, 0, 0, 0.5)`)
- **Wichtige Werte**: Text-Shadow mit entsprechender Statusfarbe

#### Glühen
- **Aktive Elemente**: Subtiles Glühen in Primärfarbe
- **Status-Indikatoren**: Glühen in entsprechender Statusfarbe

---

## Terminologie-Mapping

### Allgemeine Begriffe

| Standard | Adeptus Mechanicus |
|----------|-------------------|
| App | Machine Spirit Interface |
| System | Machine Spirit |
| Gerät | Machine Spirit |
| Funktion | Rite (Ritus) |
| Scan | Rite of Scanning |
| Start | Initiate |
| Stopp | Terminate |
| Status | Machine Spirit Status |
| Erfolg | Rite Completed / Machine Spirit Pleased |
| Fehler | Heresy Detected / Corruption Found |
| Warnung | Attention Required |
| Einstellungen | Rites of Configuration |
| Dashboard | Communion Interface |

### Sicherheit

| Standard | Adeptus Mechanicus |
|----------|-------------------|
| Security | Purity Seals |
| Security Check | Purity Seal Verification |
| Root Detection | Root Detection Seal |
| Debugger Protection | Debugger Protection Seal |
| Tamper Detection | Tamper Protection Seal |
| Threat | Heresy / Corruption |
| Secure | Verified / Intact |
| Vulnerable | Corrupted / Heretical |

### Netzwerk

| Standard | Adeptus Mechanicus |
|----------|-------------------|
| Network | Network Communion |
| Network Scan | Rite of Network Scanning |
| Device | Machine Spirit |
| Connection | Communion |
| Disconnected | Communion Lost |
| Online | Online / Active |
| Offline | Offline / Inactive |
| Port | Port |
| Service | Service |
| IP Address | Sacred Address |

### Hardware

| Standard | Adeptus Mechanicus |
|----------|-------------------|
| Battery | Power Cell / Vitality |
| Battery Status | Machine Spirit Vitality |
| Charging | Rite of Recharging |
| Voltage | Sacred Current |
| Temperature | Thermal Rite Status |
| Capacity | Power Cell Capacity |
| Current | Energy Flow Rate |
| Health | Spirit Integrity |
| USB Device | USB Machine Spirit |
| Storage | Sacred Storage |

### Authentifizierung

| Standard | Adeptus Mechanicus |
|----------|-------------------|
| Login | Rite of Authentication |
| Password | Sacred Passphrase |
| Biometric | Biometric Verification Rite |
| Unlock | Initiate Access Protocol |
| Access Granted | Machine Spirit Pleased / Access Granted |
| Access Denied | Machine Spirit Refuses / Access Denied |
| Authentication | Authentication Rite |

### Datenschutz

| Standard | Adeptus Mechanicus |
|----------|-------------------|
| Privacy | Privacy Seals |
| Privacy Score | Privacy Sanctity Score |
| Tracker | Tracker |
| Permission | Permission |
| Data | Sacred Data |
| Encrypted | Sanctified |
| Leak | Corruption / Heresy |

### Allgemeine Aktionen

| Standard | Adeptus Mechanicus |
|----------|-------------------|
| Start | INITIATE |
| Stop | TERMINATE |
| Refresh | REFRESH COMMUNION |
| Scan | INITIATE SCAN |
| Analyze | ANALYZE |
| View | VIEW |
| Export | EXPORT MANIFEST |
| Save | SAVE CONFIGURATION RITES |
| Delete | PURGE |
| Cancel | ABORT RITE |

### Status-Meldungen

| Standard | Adeptus Mechanicus |
|----------|-------------------|
| Loading... | Communing with Machine Spirit... |
| Scanning... | Rite of Scanning in progress... |
| Complete | Rite Completed |
| Success | Machine Spirit Pleased |
| Error | Heresy Detected |
| Warning | Attention Required |
| No data | No communion established |
| Ready | Ready for communion |

---

## UI-Elemente und Komponenten

### Buttons

#### Primär-Button
- **Hintergrund**: Gradient von `#8B0000` zu `#A00000`
- **Border**: `1px solid #CD7F32`
- **Text**: Orbitron, Bold, UPPERCASE, Letter-Spacing: 1.5px
- **Hover**: Hellerer Gradient, Box-Shadow mit Primärfarbe
- **Beispiel**: "INITIATE SCAN", "VERIFY SEALS"

#### Sekundär-Button
- **Hintergrund**: Gradient von `#2A1A1A` zu `#3A2A1A`
- **Border**: `1px solid #CD7F32`
- **Text**: Orbitron, Bold, UPPERCASE, Letter-Spacing: 1.5px
- **Hover**: Hellerer Gradient, Border-Farbe zu `#FF4500`
- **Beispiel**: "VIEW LOGS", "EXPORT REPORT"

#### Disabled-Button
- **Opacity**: 0.5
- **Cursor**: not-allowed
- **Keine Hover-Effekte**

### Cards

#### Standard-Card
- **Hintergrund**: Gradient von `#1A1A1A` zu `#2A1A1A`
- **Border**: `1px solid #CD7F32`
- **Border-Radius**: `8px`
- **Padding**: `20px` oder `25px`
- **Hover**: Border-Farbe zu `#FF4500`, Box-Shadow, leichte Transformation

#### Status-Card
- **Border-Left**: `4px solid` in entsprechender Statusfarbe
- **Status-Badge**: Oben rechts mit entsprechender Farbe
- **Beispiel**: Verifiziert (Grün), Warnung (Amber), Fehler (Rot)

### Input-Felder

#### Text-Input
- **Hintergrund**: `#0A0A0A`
- **Border**: `1px solid #CD7F32`
- **Text**: Rajdhani, Regular
- **Placeholder**: Gedämpftes Grau (`#B8B8A0`)
- **Focus**: Border zu `#FF4500`, Box-Shadow mit Orange

#### Label
- **Text**: Orbitron, SemiBold, UPPERCASE, Letter-Spacing: 1px
- **Farbe**: `#B8B8A0`
- **Position**: Über dem Input-Feld

### Status-Badges

#### Verifiziert/Secured
- **Hintergrund**: `#0A4A00`
- **Text**: `#10B981`
- **Border**: `1px solid #10B981`
- **Text**: "VERIFIED", "SECURED", "INTACT"

#### Warnung
- **Hintergrund**: `#4A2A00`
- **Text**: `#F59E0B`
- **Border**: `1px solid #F59E0B`
- **Text**: "WARNING", "MONITORING", "ATTENTION REQUIRED"

#### Fehler/Heresy
- **Hintergrund**: `#4A0000`
- **Text**: `#FF4500` oder `#DC2626`
- **Border**: `1px solid #FF4500`
- **Text**: "HERESY DETECTED", "CORRUPTION FOUND", "ERROR"

### Progress Bars

#### Standard-Progress
- **Hintergrund**: `#0A0A0A`
- **Border**: `2px solid #CD7F32`
- **Fill**: Gradient von `#8B0000` zu `#FF4500`
- **Höhe**: `30px`
- **Border-Radius**: `15px`
- **Box-Shadow**: Auf Fill mit Primärfarbe

### Icons

#### Icon-Stil
- **Farbe**: Primärfarbe (`#8B0000`) oder Statusfarbe
- **Größe**: Kontextabhängig (meist `24dp` bis `48dp`)
- **Hover**: Leichtes Glühen in Primärfarbe

### Dividers

#### Standard-Divider
- **Farbe**: `#CD7F32`
- **Dicke**: `1px` oder `2px`
- **Stil**: Solid

### Loading-Indikatoren

#### Spinner
- **Farbe**: Primärfarbe (`#8B0000`)
- **Stil**: Circular Progress Indicator
- **Text**: "COMMUNING WITH MACHINE SPIRIT..."

#### Progress-Text
- **Font**: Orbitron, Regular
- **Farbe**: `#B8B8A0`
- **Stil**: Italic
- **Beispiele**: "Scanning for heresy...", "Analyzing machine spirits..."

---

## Technische Umsetzung

### Theme-System-Architektur

#### 1. Theme-State-Management

**Speicherung der Theme-Auswahl:**
- **Ort**: `SharedPreferences` oder `DataStore`
- **Schlüssel**: `"app_theme_style"` oder `"adeptus_mechanicus_enabled"`
- **Werte**: `"standard"` / `"adeptus_mechanicus"` oder `boolean`

**State-Management:**
- **ViewModel**: `SettingsViewModel` verwaltet Theme-State
- **CompositionLocal**: `LocalThemeStyle` für Compose-Komponenten
- **Flow**: `StateFlow<ThemeStyle>` für reaktive Updates

#### 2. Theme-Configuration

**Theme-Definition:**

```kotlin
 
```

**Color-Scheme:**
- Separate `ColorScheme` für jedes Theme
- `AdeptusMechanicusColorScheme` mit Mars-Rot, Bronze, etc.
- `StandardColorScheme` bleibt unverändert

**Typography:**
- Separate `Typography` für jedes Theme
- `AdeptusMechanicusTypography` mit Orbitron/Rajdhani
- `StandardTypography` bleibt unverändert

#### 3. String-Ressourcen

**Ansatz:**
- **Option A**: Separate String-Ressourcen-Dateien
  - `res/values/strings.xml` (Standard)
  - `res/values-adeptus/strings.xml` (Adeptus Mechanicus)
- **Option B**: String-Provider-Pattern
  - `ThemeStringProvider` liefert Theme-abhängige Strings
  - Zentraler Zugriffspunkt für alle Texte

**Empfehlung**: Option B (String-Provider-Pattern) für bessere Wartbarkeit

#### 4. UI-Komponenten

**Compose-Komponenten:**
- Alle UI-Komponenten prüfen Theme-State
- Verwenden Theme-abhängige Farben, Typografie, Strings
- Keine hardcodierten Werte

**Beispiel-Struktur:**

```kotlin

```

---

## Implementierungs-Schritte

### Phase 1: Grundlagen

1. **Theme-State-Management implementieren**
   - `ThemeStyle` sealed class erstellen
   - `ThemeManager` oder `ThemeRepository` erstellen
   - `SharedPreferences`/`DataStore` Integration
   - `SettingsViewModel` erweitern

2. **Color-Schemes definieren**
   - `AdeptusMechanicusColorScheme` erstellen
   - Alle Farben aus Design-Guidelines implementieren
   - `StandardColorScheme` dokumentieren (bleibt unverändert)

3. **Typography-System erweitern**
   - Orbitron und Rajdhani Fonts hinzufügen
   - `AdeptusMechanicusTypography` definieren
   - Font-Loading implementieren

### Phase 2: String-System

1. **String-Provider implementieren**
   - `ThemeStringProvider` erstellen
   - Terminologie-Mapping implementieren
   - Alle Standard-Strings durch Provider ersetzen

2. **String-Ressourcen migrieren**
   - Bestehende Strings identifizieren
   - Adeptus-Mechanicus-Varianten erstellen
   - Provider-Integration testen

### Phase 3: UI-Komponenten

1. **Basis-Komponenten anpassen**
   - Buttons, Cards, Inputs anpassen
   - Theme-State-Integration
   - Hover- und Focus-States

2. **Screens migrieren**
   - Screen für Screen migrieren
   - Batterie-Dashboard als Pilot
   - Security-Dashboard
   - Network-Scanner
   - Weitere Screens

### Phase 4: Einstellungen

1. **Settings-Screen erweitern**
   - Theme-Auswahl UI hinzufügen
   - Toggle oder Radio-Buttons
   - Vorschau (optional)
   - Speicherung der Auswahl

2. **Theme-Wechsel implementieren**
   - Recomposition bei Theme-Wechsel
   - Smooth Transitions (optional)
   - State-Persistierung

### Phase 5: Testing und Polish

1. **Testing**
   - Alle Screens im Adeptus-Mechanicus-Modus testen
   - Theme-Wechsel testen
   - Edge Cases prüfen
   - Performance testen

2. **Polish**
   - Animationen verfeinern
   - Konsistenz prüfen
   - Dokumentation aktualisieren

---

## Beispiele

### Beispiel 1: Button-Komponente

**Standard-Modus:**
- Text: "Start Scan"
- Farbe: Material Design Primary
- Schrift: Roboto

**Adeptus-Mechanicus-Modus:**
- Text: "INITIATE RITE OF SCANNING"
- Farbe: Mars-Rot (`#8B0000`)
- Schrift: Orbitron, Bold, UPPERCASE
- Letter-Spacing: 1.5px

### Beispiel 2: Batterie-Dashboard

**Standard-Modus:**
- Titel: "Battery Analyzer"
- Status: "Battery Status: 75%"
- Labels: "Voltage", "Temperature", "Capacity"

**Adeptus-Mechanicus-Modus:**
- Titel: "VITAL SIGNS MONITOR"
- Status: "MACHINE SPIRIT VITALITY: 75%"
- Labels: "SACRED CURRENT", "THERMAL RITE STATUS", "POWER CELL CAPACITY"

### Beispiel 3: Security-Dashboard

**Standard-Modus:**
- Titel: "Security Dashboard"
- Status: "Security Check Passed"
- Badges: "Secure", "Warning", "Error"

**Adeptus-Mechanicus-Modus:**
- Titel: "PURITY SEALS"
- Status: "PURITY SEAL VERIFIED - MACHINE SPIRIT PLEASED"
- Badges: "VERIFIED", "MONITORING", "HERESY DETECTED"

### Beispiel 4: Error-Message

**Standard-Modus:**
- Text: "Error: Connection failed"
- Farbe: Material Design Error
- Icon: Standard Error Icon

**Adeptus-Mechanicus-Modus:**
- Text: "HERESY DETECTED: Machine Spirit refuses connection"
- Farbe: Warnrot (`#FF4500`)
- Icon: Custom oder Standard mit Glühen

---

## Best Practices

### Konsistenz

1. **Terminologie**: Immer konsistent verwenden
   - "Rite" für Aktionen
   - "Machine Spirit" für Geräte/Systeme
   - "Heresy" für Fehler
   - "Pleased" für Erfolg

2. **Farben**: Strikte Einhaltung der Farbpalette
   - Keine Abweichungen ohne Begründung
   - Statusfarben konsistent verwenden

3. **Typografie**: Einheitliche Schriftarten
   - Orbitron für Überschriften und Buttons
   - Rajdhani für Body-Text
   - Keine Mischung ohne Grund

### Performance

1. **Font-Loading**: Fonts effizient laden
   - Nur benötigte Gewichte laden
   - Preloading für bessere Performance

2. **Theme-Wechsel**: Optimiert implementieren
   - Keine unnötigen Recompositionen
   - State effizient verwalten

3. **String-Provider**: Caching implementieren
   - Strings cachen, nicht bei jedem Zugriff neu berechnen
   - Effiziente Lookup-Struktur

### Wartbarkeit

1. **Zentrale Konfiguration**: Alles an einem Ort
   - Farben in `AdeptusMechanicusColors`
   - Strings in `ThemeStringProvider`
   - Typografie in `AdeptusMechanicusTypography`

2. **Dokumentation**: Alles dokumentieren
   - Neue Terminologie dokumentieren
   - Design-Entscheidungen begründen
   - Beispiele bereitstellen

3. **Testing**: Umfassend testen
   - Theme-Wechsel testen
   - Alle Screens testen
   - Edge Cases abdecken

### Benutzerfreundlichkeit

1. **Klarheit**: Trotz Theme verständlich bleiben
   - Icons unterstützen Text
   - Tooltips für unklare Begriffe (optional)
   - Hilfe-Sektion (optional)

2. **Zugänglichkeit**: Accessibility nicht vernachlässigen
   - Kontraste prüfen
   - Screen-Reader-Unterstützung
   - Tastatur-Navigation

3. **Performance**: Keine spürbaren Verzögerungen
   - Theme-Wechsel sollte schnell sein
   - Keine Ladezeiten durch Theme

### Erweiterbarkeit

1. **Modularität**: Einfach erweiterbar
   - Neue Screens einfach hinzufügen
   - Neue Terminologie einfach ergänzen
   - Neue Farben einfach hinzufügen

2. **Flexibilität**: Anpassbar für Zukunft
   - Weitere Themes möglich
   - Customization-Optionen denkbar
   - A/B-Testing möglich

---

## Checkliste für Implementierung

### Vorbereitung
- [ ] Design-Guidelines gelesen und verstanden
- [ ] Farbpalette definiert und dokumentiert
- [ ] Fonts beschafft und lizenziert
- [ ] Terminologie-Mapping vollständig

### Grundlagen
- [ ] Theme-State-Management implementiert
- [ ] Color-Schemes definiert
- [ ] Typography-System erweitert
- [ ] Font-Loading implementiert

### String-System
- [ ] String-Provider implementiert
- [ ] Terminologie-Mapping implementiert
- [ ] Alle Strings migriert
- [ ] Testing durchgeführt

### UI-Komponenten
- [ ] Basis-Komponenten angepasst
- [ ] Buttons angepasst
- [ ] Cards angepasst
- [ ] Inputs angepasst
- [ ] Status-Badges angepasst

### Screens
- [ ] Main Dashboard migriert
- [ ] Battery Analyzer migriert
- [ ] Security Dashboard migriert
- [ ] Network Scanner migriert
- [ ] Authentication Screen migriert
- [ ] USB Manager migriert
- [ ] Privacy Dashboard migriert
- [ ] Settings Screen erweitert
- [ ] Vulnerability Scanner migriert
- [ ] Device Info migriert
- [ ] Alle weiteren Screens migriert

### Einstellungen
- [ ] Settings-Screen erweitert
- [ ] Theme-Auswahl UI implementiert
- [ ] Theme-Wechsel implementiert
- [ ] State-Persistierung implementiert

### Testing
- [ ] Alle Screens im Adeptus-Mechanicus-Modus getestet
- [ ] Theme-Wechsel getestet
- [ ] Edge Cases getestet
- [ ] Performance getestet
- [ ] Accessibility getestet

### Dokumentation
- [ ] Code dokumentiert
- [ ] Design-Entscheidungen dokumentiert
- [ ] Beispiele dokumentiert
- [ ] README aktualisiert

---

## Anhang

### Referenzen

- **Warhammer 40.000**: Games Workshop
- **Adeptus Mechanicus**: Technokratischer Kult aus Warhammer 40.000
- **Material Design 3**: Google's Design System
- **Jetpack Compose**: Android UI Framework

### Font-Lizenzen

- **Orbitron**: SIL Open Font License (OFL)
- **Rajdhani**: SIL Open Font License (OFL)

### Kontakt

Bei Fragen zur Implementierung oder Design-Entscheidungen:
- Design-Team konsultieren
- Code-Reviews durchführen
- User-Feedback einholen

---

**Dokument-Version**: 1.0  
**Letzte Aktualisierung**: Dezember 2025  
**Status**: Design-Phase, Implementierung ausstehend

