# Modulare Connectias Architektur - Implementierungsplan

## Architektur-Übersicht

**Kernprinzipien:**
- Alles neu aufbauen (keine Migration)
- Module zur Build-Zeit konfigurierbar (gradle.properties)
- Module zur Laufzeit nachladbar (für zukünftiges Plugin-System)
- Hilt Dependency Injection ab Phase 2
- MVP-Ansatz: Zuerst Core + 1 Feature, dann erweitern

## Phase 1: Projekt-Grundstruktur und Konfiguration

### 1.1 Gradle-Konfiguration erstellen

**gradle.properties erweitern:**
- Feature-Flags für Module:
  - `core.enabled=true` (immer)
  - `feature.security.enabled=true` (Core - immer)
  - `feature.settings.enabled=true` (Core - immer)
  - `feature.device.info.enabled=true` (optional)
  - `feature.network.enabled=false` (optional)
  - `feature.utilities.enabled=false` (optional)
  - `feature.backup.enabled=false` (optional)
- Build-Varianten: `build.variant=full|minimal|custom`
- Runtime-Module-Loading: `runtime.module.loading.enabled=true`

**settings.gradle.kts erweitern:**
- Dynamisches Include basierend auf gradle.properties
- Core-Module immer inkludiert
- Optionale Module nur wenn enabled=true
- Include-Logik für Build-Zeit-Konfiguration

**build.gradle.kts (root) erweitern:**
- Build-Varianten-Konfiguration
- Gemeinsame Konfiguration für alle Module
- Dependency-Graph-Validierung (einfache Version)

### 1.2 libs.versions.toml erweitern

**Hinzufügen:**
- Hilt (Android + Compiler)
- Room Database + SQLCipher
- Navigation Component (Fragment + UI)
- Coroutines (Android + Core)
- Timber (Logging)
- Security Crypto
- Kotlin Serialization
- KSP (Kotlin Symbol Processing)
- Fragment KTX
- Lifecycle (ViewModel + LiveData)
- RecyclerView, CardView
- Material Design Components

## Phase 2: Core-Module und Hilt Setup

### 2.1 :common Modul erstellen

**Struktur:**
```
common/
├── build.gradle.kts
└── src/main/java/com/ble1st/connectias/common/
    ├── utils/ (Shared Utilities)
    ├── models/ (Shared Data Models)
    ├── extensions/ (Kotlin Extensions)
    └── resources/ (Shared Strings, Colors)
```

**build.gradle.kts:**
- Android Library Plugin
- Minimale Dependencies (nur Android Core KTX)
- Keine weiteren Abhängigkeiten

### 2.2 :core Modul erstellen

**Struktur:**
```
core/
├── build.gradle.kts
└── src/main/java/com/ble1st/connectias/core/
    ├── database/
    │   ├── AppDatabase.kt (Room + SQLCipher)
    │   ├── dao/ (Data Access Objects)
    │   └── entities/ (Room Entities)
    ├── security/
    │   ├── RaspManager.kt
    │   ├── root/ (RootDetector)
    │   ├── debug/ (DebuggerDetector)
    │   ├── emulator/ (EmulatorDetector)
    │   └── tamper/ (TamperDetector)
    ├── services/
    │   ├── NetworkService.kt
    │   ├── SystemService.kt
    │   └── SecurityService.kt
    ├── di/
    │   ├── CoreModule.kt (Hilt Module)
    │   ├── DatabaseModule.kt (Hilt Module)
    │   └── ServiceModule.kt (Hilt Module)
    ├── eventbus/
    │   ├── EventBus.kt (Kotlin Flow/SharedFlow basiert)
    │   └── events/ (Event Models)
    ├── models/ (Core Models)
    └── utils/ (Core Utilities)
```

**build.gradle.kts:**
- Android Library Plugin
- Hilt, Room, SQLCipher, Security Crypto
- Coroutines, Navigation
- Timber, Kotlin Serialization
- KSP für Room und Hilt
- Abhängigkeit zu :common

### 2.3 :feature-security Modul erstellen (Core - immer aktiv)

**Struktur:**
```
feature-security/
├── build.gradle.kts
└── src/main/java/com/ble1st/connectias/feature/security/
    ├── ui/
    │   ├── SecurityDashboardFragment.kt
    │   └── SecurityDashboardViewModel.kt
    ├── di/
    │   └── SecurityModule.kt (Hilt Module)
    └── res/ (Layouts, Strings, Drawables)
```

**build.gradle.kts:**
- Android Library Plugin
- Hilt, Navigation, Material
- Abhängigkeit zu :core, :common
- ViewBinding aktiviert

### 2.4 :feature-settings Modul erstellen (Core - immer aktiv)

**Struktur:**
```
feature-settings/
├── build.gradle.kts
└── src/main/java/com/ble1st/connectias/feature/settings/
    ├── ui/
    │   ├── SettingsFragment.kt
    │   └── SettingsViewModel.kt
    ├── repository/
    │   └── SettingsRepository.kt
    ├── di/
    │   └── SettingsModule.kt (Hilt Module)
    └── res/
```

**build.gradle.kts:**
- Android Library Plugin
- Hilt, Navigation, Material, Preferences
- Abhängigkeit zu :core, :common

### 2.5 Hilt Application Class

**app/src/main/java/.../ConnectiasApplication.kt:**
- @HiltAndroidApp Annotation
- Application Class für Hilt
- Initialisierung von Core-Services

**AndroidManifest.xml:**
- Application Class registrieren

## Phase 3: App-Modul Setup mit dynamischer Navigation

### 3.1 MainActivity erweitern

**MainActivity.kt:**
- Hilt Integration (@AndroidEntryPoint)
- Navigation Component Setup
- Dynamische Navigation basierend auf aktiven Modulen
- Bottom Navigation + Navigation Drawer
- FAB für Feature-Zugriff

### 3.2 Navigation-Graph

**res/navigation/nav_graph.xml:**
- Core-Navigation (Security, Settings) immer vorhanden
- Dynamische Routes für optionale Features
- Deep Links für Module

### 3.3 app/build.gradle.kts

**Dependencies:**
- Core-Module (immer): :core, :common, :feature-security, :feature-settings
- Bedingte Dependencies zu optionalen Features
- Hilt, Navigation, Material
- ViewBinding, DataBinding

### 3.4 Module-Discovery zur Build-Zeit

**ModuleRegistry.kt (in :core):**
- Build-Zeit-Discovery: Welche Module sind kompiliert?
- Runtime-Discovery-Vorbereitung: Module-Metadaten
- Navigation-Routes für aktive Module

## Phase 4: Erstes optionales Feature (Proof of Concept)

### 4.1 :feature-device-info Modul

**Struktur:**
```
feature-device-info/
├── build.gradle.kts
└── src/main/java/com/ble1st/connectias/feature/deviceinfo/
    ├── ui/
    │   ├── DeviceInfoFragment.kt
    │   └── DeviceInfoViewModel.kt
    ├── provider/
    │   └── DeviceInfoProvider.kt
    ├── di/
    │   └── DeviceInfoModule.kt (Hilt Module)
    └── res/
```

**build.gradle.kts:**
- Android Library Plugin
- Hilt, Navigation, Material
- Abhängigkeit zu :core, :common
- Eigene Version: 1.0.0

**Integration:**
- In gradle.properties aktivieren
- Navigation-Route hinzufügen
- Hilt-Modul wird automatisch geladen

## Phase 5: Runtime-Module-Loading Vorbereitung

### 5.1 Module-Loader Interface

**core/src/main/java/.../core/module/ModuleLoader.kt:**
- Interface für Runtime-Module-Loading
- Module-Metadaten (ID, Version, Dependencies)
- Load/Unload-Funktionalität

### 5.2 Module-Registry erweitern

**ModuleRegistry.kt erweitern:**
- Build-Zeit-Module (kompiliert)
- Runtime-Module (nachgeladen)
- Module-Status-Tracking
- Dependency-Resolution

### 5.3 Event-Bus für Module-Kommunikation

**EventBus.kt (in :core):**
- Kotlin Flow/SharedFlow Implementation
- Event-Types für Module-Events
- Subscriber-Management
- Thread-Safe Event-Handling

## Phase 6: Weitere optionale Features

### 6.1 :feature-network Modul

- WLAN Scanner
- LAN Scanner
- Network Info
- Navigation-Integration
- Hilt-Modul

### 6.2 :feature-utilities Modul

- Hash Generator
- Encoding Converter
- QR Code Generator
- Navigation-Integration
- Hilt-Modul

### 6.3 :feature-backup Modul

- Backup Manager
- Restore Functionality
- Export/Import
- Navigation-Integration
- Hilt-Modul

## Phase 7: Build-Varianten und Optimierung

### 7.1 Build-Varianten konfigurieren

**build.gradle.kts (root):**
- full: Alle Module aktivieren
- minimal: Nur Core-Module
- custom: Benutzerdefinierte Auswahl

**Gradle-Tasks:**
- `./gradlew assembleFull`
- `./gradlew assembleMinimal`
- `./gradlew assembleCustom`

### 7.2 Build-Optimierung

- Gradle Build Cache aktivieren
- Parallele Kompilierung
- Incremental Builds
- Dependency-Caching

### 7.3 Dependency-Graph-Validierung

- Zirkuläre Abhängigkeiten prüfen
- Fehlende Dependencies erkennen
- Build-Zeit-Validierung

## Phase 8: Testing Setup

### 8.1 Test-Struktur

**Jedes Modul:**
- `src/test/` - Unit Tests
- `src/androidTest/` - Instrumented Tests
- Hilt-Test-Setup

### 8.2 Test-Strategie

- Unit Tests für Business Logic
- Integration Tests für Module-Interaktion
- UI Tests für kritische Flows
- Alle Tests ausführen (auch für inaktive Module)

### 8.3 Test-Dependencies

- JUnit, MockK
- Hilt Testing
- Espresso (UI Tests)
- Coroutines Test

## Phase 9: Dokumentation

### 9.1 README aktualisieren

- Architektur-Übersicht
- Modul-Struktur
- Feature-Aktivierung
- Build-Varianten
- Runtime-Module-Loading

### 9.2 Module-Dokumentation

- Jedes Modul dokumentieren
- Dependency-Graph
- API-Dokumentation
- Hilt-Module-Dokumentation

### 9.3 Entwickler-Guide

- Neues Feature hinzufügen
- Module konfigurieren
- Hilt-Modul erstellen
- Navigation integrieren

## Technische Details

### Wichtige Dateien:

1. **gradle.properties** - Feature-Flags und Build-Konfiguration
2. **settings.gradle.kts** - Dynamisches Module-Include
3. **core/src/main/java/.../core/di/** - Hilt-Module (ab Phase 2)
4. **core/src/main/java/.../core/module/ModuleRegistry.kt** - Module-Discovery
5. **core/src/main/java/.../core/eventbus/EventBus.kt** - Event-Bus
6. **app/src/main/java/.../ConnectiasApplication.kt** - Hilt Application
7. **app/src/main/java/.../MainActivity.kt** - Dynamische Navigation

### Dependency-Graph:

```
:app
  ├── :core (immer) [Hilt ab Phase 2]
  ├── :common (immer)
  ├── :feature-security (immer) [Hilt ab Phase 2]
  ├── :feature-settings (immer) [Hilt ab Phase 2]
  └── [optionale Features basierend auf gradle.properties]

:core
  ├── :common
  └── [Hilt, Room, Security, Event-Bus]

:feature-*
  ├── :core
  └── :common
```

### Versioning:

- Jedes Modul hat eigene Version in build.gradle.kts
- Core-Module: 1.0.0
- Feature-Module: 1.0.0 (unabhängig versionierbar)

### Runtime-Module-Loading (Vorbereitung):

- Interface definiert in Phase 5
- Implementierung später (Plugin-System Phase)
- Module-Metadaten für Discovery
- Event-Bus für Module-Kommunikation

