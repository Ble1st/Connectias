# Connectias MVP - Minimal Viable Product Plan

## Übersicht

Dieses Dokument beschreibt den MVP (Minimum Viable Product) für Connectias - eine modulare Android-App mit konfigurierbaren Feature-Modulen. Der MVP fokussiert sich auf die Kernfunktionalität und dient als Proof of Concept für die modulare Architektur.

## MVP Scope

### Inkludiert im MVP

**Phase 1-3: Core-Funktionalität**
- Projekt-Grundstruktur mit Gradle-Konfiguration
- Core-Module (:common, :core)
- Hilt Dependency Injection Setup
- Zwei Core-Features (:feature-security, :feature-settings)
- App-Modul mit dynamischer Navigation
- Basis-Module-Discovery

**Phase 4: Ein optionales Feature als Proof of Concept**
- :feature-device-info Modul

### Nicht im MVP (später)

- Weitere optionale Features (Network, Utilities, Backup)
- Runtime-Module-Loading (nur Interface-Vorbereitung)
- Build-Varianten (full, minimal, custom)
- Umfangreiches Testing
- Vollständige Dokumentation

## MVP Architektur

### Modulstruktur

```
Connectias/
├── :app (Main Application)
│   ├── ConnectiasApplication.kt (@HiltAndroidApp)
│   └── MainActivity.kt (Dynamische Navigation)
├── :common (Shared Code)
│   ├── utils/
│   ├── models/
│   ├── extensions/
│   └── resources/
├── :core (Core-Funktionalität)
│   ├── database/ (Room + SQLCipher)
│   ├── security/ (RASP Manager)
│   ├── services/ (Network, System, Security)
│   ├── di/ (Hilt Modules)
│   ├── eventbus/ (Kotlin Flow basiert)
│   └── module/ (ModuleRegistry)
├── :feature-security (Core - immer aktiv)
│   ├── ui/ (SecurityDashboardFragment/ViewModel)
│   └── di/ (SecurityModule)
├── :feature-settings (Core - immer aktiv)
│   ├── ui/ (SettingsFragment/ViewModel)
│   ├── repository/ (SettingsRepository)
│   └── di/ (SettingsModule)
└── :feature-device-info (Optional - MVP Proof of Concept)
    ├── ui/ (DeviceInfoFragment/ViewModel)
    ├── provider/ (DeviceInfoProvider)
    └── di/ (DeviceInfoModule)
```

### Dependency-Graph (MVP)

```
:app
  ├── :core (immer)
  ├── :common (immer)
  ├── :feature-security (immer)
  ├── :feature-settings (immer)
  └── :feature-device-info (optional - MVP)

:core
  └── :common

:feature-*
  ├── :core
  └── :common
```

## MVP Implementierungsphasen

### Phase 1: Projekt-Grundstruktur (MVP Foundation)

**Ziele:**
- Gradle-Konfiguration für modulare Architektur
- Feature-Flags in gradle.properties
- Dynamisches Module-Include in settings.gradle.kts
- Dependencies in libs.versions.toml

**Deliverables:**
- `gradle.properties` mit Feature-Flags
- `settings.gradle.kts` mit dynamischem Include
- `build.gradle.kts` (root) erweitert
- `libs.versions.toml` mit allen benötigten Dependencies

**Feature-Flags (MVP):**
```properties
# Core Modules (immer aktiv)
core.enabled=true
feature.security.enabled=true
feature.settings.enabled=true

# Optional Module (MVP)
feature.device.info.enabled=true

# Nicht im MVP
feature.network.enabled=false
feature.utilities.enabled=false
feature.backup.enabled=false
```

### Phase 2: Core-Module und Hilt (MVP Core)

**Ziele:**
- :common Modul erstellen
- :core Modul mit Database, Security, Services
- Hilt Dependency Injection Setup
- :feature-security und :feature-settings Module
- Hilt Application Class

**Deliverables:**
- :common Modul mit Shared Code
- :core Modul mit:
  - Room Database + SQLCipher
  - RASP Security (Root, Debugger, Emulator, Tamper Detection)
  - Services (Network, System, Security)
  - Hilt Modules (CoreModule, DatabaseModule, ServiceModule)
  - Event-Bus (Kotlin Flow basiert)
  - ModuleRegistry (Build-Zeit-Discovery)
- :feature-security Modul mit Security Dashboard
- :feature-settings Modul mit Settings UI
- ConnectiasApplication.kt mit @HiltAndroidApp

**Kernfunktionalität:**
- Verschlüsselte Datenbank (SQLCipher)
- Security Dashboard mit RASP-Status
- Settings mit Theme, Preferences
- Hilt für Dependency Injection

### Phase 3: App-Modul und Navigation (MVP UI)

**Ziele:**
- MainActivity mit Hilt Integration
- Dynamische Navigation basierend auf aktiven Modulen
- Navigation-Graph mit Core-Routes
- Module-Discovery Integration

**Deliverables:**
- MainActivity.kt mit:
  - @AndroidEntryPoint
  - Navigation Component Setup
  - Bottom Navigation
  - Navigation Drawer (optional)
- Navigation-Graph (nav_graph.xml)
- ModuleRegistry Integration
- UI für Security Dashboard
- UI für Settings

**Navigation-Struktur (MVP):**
- Home (optional)
- Security Dashboard (Core)
- Settings (Core)
- Device Info (optional - MVP)

### Phase 4: Optionales Feature - Proof of Concept (MVP Feature)

**Ziele:**
- :feature-device-info Modul erstellen
- Integration in Navigation
- Hilt-Modul Integration
- Proof of Concept für modulare Architektur

**Deliverables:**
- :feature-device-info Modul mit:
  - DeviceInfoFragment/ViewModel
  - DeviceInfoProvider
  - DeviceInfoModule (Hilt)
  - UI für Device Information
- Navigation-Route hinzufügen
- Feature-Flag aktivieren

**Funktionalität:**
- System Info (OS, CPU, RAM, Storage)
- Network Info (IP, MAC, WiFi)
- Battery Status
- Hardware Sensors (optional)

## MVP Technische Anforderungen

### Dependencies (MVP)

**Core Dependencies:**
- Hilt (Android + Compiler)
- Room Database + SQLCipher
- Navigation Component
- Coroutines
- Timber (Logging)
- Security Crypto
- Material Design
- ViewBinding

**Optional (später):**
- Kotlin Serialization
- WorkManager
- Biometric
- PDF Export

### Build-Konfiguration (MVP)

**Minimal:**
- Java 17
- Kotlin 2.0.21
- AGP 8.13.1
- minSdk 33
- targetSdk 36
- compileSdk 36

**Build-Varianten (MVP):**
- Debug (Standard)
- Release (Standard)
- Build-Varianten (full, minimal, custom) - später

## MVP Features

### Security Dashboard (Core)

**Funktionen:**
- RASP Status Anzeige
  - Root Detection Status
  - Debugger Detection Status
  - Emulator Detection Status
  - Tamper Detection Status
- Security Check History
- Threat Alerts
- Security Score

**UI:**
- Dashboard mit Status-Cards
- Detail-Ansicht für jeden Check
- History-Liste

### Settings (Core)

**Funktionen:**
- Theme (Light/Dark/Auto)
- App Preferences
- Security Settings
- About Screen

**UI:**
- Settings-Liste
- Preference-Fragments
- Theme-Switcher

### Device Info (Optional - MVP)

**Funktionen:**
- System Information
- Network Information
- Battery Status
- Hardware Info

**UI:**
- Info-Cards
- Real-time Updates (optional)
- Export (später)

## MVP Erfolgskriterien

### Funktionale Anforderungen

- [x] App startet ohne Fehler
- [x] Hilt Dependency Injection funktioniert
- [x] Security Dashboard zeigt RASP-Status
- [x] Settings können geändert werden
- [x] Device Info Feature funktioniert (wenn aktiviert)
- [x] Navigation zwischen Features funktioniert
- [x] Module können über gradle.properties aktiviert/deaktiviert werden

### Technische Anforderungen

- [x] Modulare Architektur implementiert
- [x] Hilt für DI verwendet
- [x] Room Database mit SQLCipher funktioniert
- [x] RASP Security Checks funktionieren
- [x] Dynamische Navigation basierend auf aktiven Modulen
- [x] Module-Discovery zur Build-Zeit

### Code-Qualität

- [x] Saubere Modul-Trennung
- [x] Keine zirkulären Abhängigkeiten
- [x] Hilt-Module korrekt konfiguriert
- [x] Navigation korrekt integriert

## MVP Nächste Schritte (Post-MVP)

Nach erfolgreichem MVP können folgende Features hinzugefügt werden:

1. **Weitere optionale Features:**
   - :feature-network (WLAN/LAN Scanner)
   - :feature-utilities (Hash, Encoding, QR)
   - :feature-backup (Backup/Restore)

2. **Build-Varianten:**
   - full (alle Module)
   - minimal (nur Core)
   - custom (benutzerdefiniert)

3. **Runtime-Module-Loading:**
   - Module zur Laufzeit nachladen
   - Plugin-System Integration

4. **Testing:**
   - Unit Tests
   - Integration Tests
   - UI Tests

5. **Optimierung:**
   - Build-Performance
   - App-Performance
   - Code-Optimierung

## MVP Risiken und Mitigation

### Risiken

1. **Gradle-Komplexität:**
   - Risiko: Dynamisches Include kann fehleranfällig sein
   - Mitigation: Einfacher Ansatz zuerst, schrittweise erweitern

2. **Hilt Conditional Loading:**
   - Risiko: Runtime-Fehler bei fehlenden Modulen
   - Mitigation: Explizite Module-Registrierung, keine Conditional Loading im MVP

3. **Module-Discovery:**
   - Risiko: Komplexe Discovery-Logik
   - Mitigation: Einfache Build-Zeit-Discovery im MVP

### Best Practices für MVP

1. **KISS-Prinzip:** Keep It Simple, Stupid
2. **MVP-First:** Nur Core-Funktionalität
3. **Iterativ:** Schrittweise erweitern
4. **Testbar:** Code sollte testbar sein (Tests später)

## MVP Zeitplan (Schätzung)

- **Phase 1:** 1-2 Tage (Gradle-Konfiguration)
- **Phase 2:** 3-5 Tage (Core-Module + Hilt)
- **Phase 3:** 2-3 Tage (App-Modul + Navigation)
- **Phase 4:** 2-3 Tage (Device Info Feature)

**Gesamt MVP:** ~8-13 Tage

## MVP Checkliste

### Vor Start

- [ ] Projekt-Struktur verstanden
- [ ] Dependencies recherchiert
- [ ] Architektur-Plan gelesen
- [ ] Entwicklungsumgebung vorbereitet

### Phase 1

- [ ] gradle.properties erweitert
- [ ] settings.gradle.kts angepasst
- [ ] libs.versions.toml erweitert
- [ ] Build funktioniert

### Phase 2

- [ ] :common Modul erstellt
- [ ] :core Modul erstellt
- [ ] Hilt Setup funktioniert
- [ ] :feature-security erstellt
- [ ] :feature-settings erstellt
- [ ] ConnectiasApplication erstellt

### Phase 3

- [ ] MainActivity mit Hilt
- [ ] Navigation funktioniert
- [ ] Security Dashboard UI
- [ ] Settings UI
- [ ] Module-Discovery funktioniert

### Phase 4

- [ ] :feature-device-info erstellt
- [ ] Navigation integriert
- [ ] Feature-Flag funktioniert
- [ ] MVP komplett

## Zusammenfassung

Der MVP fokussiert sich auf:
1. **Modulare Architektur** - Beweis, dass Module funktionieren
2. **Hilt Integration** - Dependency Injection Setup
3. **Core-Features** - Security Dashboard und Settings
4. **Ein optionales Feature** - Proof of Concept für modulare Features
5. **Dynamische Navigation** - Basierend auf aktiven Modulen

Nach erfolgreichem MVP kann die Architektur schrittweise erweitert werden mit weiteren Features, Build-Varianten, Runtime-Loading und umfangreichem Testing.

