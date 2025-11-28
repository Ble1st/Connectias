# Connectias

Eine umfassende FOSS (Free and Open Source Software) Android-App für Netzwerk-Analyse, Sicherheit, Privacy und System-Utilities - ohne Google-Abhängigkeiten.

## Features

### 🔒 Security Features
- **Security Dashboard** - RASP (Runtime Application Self-Protection) Monitoring
- **Certificate Analyzer** - SSL/TLS Zertifikatsanalyse
- **Password Strength Checker** - Passwort-Analyse und Generator
- **Encryption Tools** - AES-256-GCM Verschlüsselung/Entschlüsselung
- **Firewall Analyzer** - App-Netzwerkberechtigungen Analyse

### 🌐 Network Features
- **Network Dashboard** - Übersicht über Netzwerk-Status
- **Port Scanner** - Port-Scanning für lokale Geräte
- **DNS Lookup & Diagnostics** - DNS-Auflösung und Server-Tests
- **Network Monitor** - Traffic-Monitoring (Rx/Tx Bytes)
- **WiFi Analyzer** - WiFi-Kanal-Analyse und Signal-Stärke

### 🔐 Privacy Features
- **Privacy Dashboard** - Privacy-Status Übersicht
- **Tracker Detection** - Erkennung bekannter Tracker-Domains
- **Permissions Analyzer** - Erweiterte Berechtigungsanalyse mit Risiko-Bewertung
- **Data Leakage Scanner** - Clipboard-Monitoring und Sensitivitäts-Analyse

### 📱 Device Info Features
- **Device Info** - System- und Geräteinformationen
- **Battery Analyzer** - Batterie-Analyse und Monitoring
- **Storage Analyzer** - Speicher-Analyse und große Dateien Finder
- **Process Monitor** - Laufende Prozesse mit Speicherverbrauch
- **Sensor Monitor** - Real-time Sensor-Daten

### 🛠️ Utilities Features
- **Hash & Checksum Tools** - MD5, SHA-1, SHA-256, SHA-512 Hash Generator
- **Encoding/Decoding Tools** - Base64, URL, Hex Encoding/Decoding
- **QR Code Generator/Scanner** - QR Code Erstellung und Scanner
- **Text Tools** - Case Converter, Word Counter, JSON Formatter
- **API Tester** - REST API Client mit Request History
- **Log Viewer** - System- und App-Log Viewer
- **Color Tools** - Color Converter und Contrast Checker

### 💾 Backup Features
- **Backup & Restore** - App-Daten Backup und Restore
- **Export Tools** - JSON, CSV, PDF Export
- **Import Tools** - Backup-Import mit Validierung

## Architektur

### Modulare Struktur

Die App verwendet eine modulare Architektur mit dynamischer Module-Discovery:

- **Core Modules** (immer aktiv):
  - `:core` - Kern-Funktionalität
  - `:common` - Gemeinsame Utilities
  - `:feature-security` - Security Features

- **Optional Modules** (konfigurierbar):
  - `:feature-network` - Network Features
  - `:feature-privacy` - Privacy Features
  - `:feature-device-info` - Device Info Features
  - `:feature-utilities` - Utility Tools
  

### Module-Konfiguration

Module können über `gradle.properties` aktiviert/deaktiviert werden:

```properties
feature.network.enabled=true
feature.privacy.enabled=true
feature.device.info.enabled=true
feature.utilities.enabled=true
feature.backup.enabled=true
```

### Dependency Injection

Die App verwendet **Hilt** für Dependency Injection. Alle Module haben ihre eigenen Hilt-Module für Dependency-Management.

### Navigation

Dynamische Navigation basierend auf verfügbaren Modulen. Navigation-Routes werden automatisch aus dem `ModuleCatalog` generiert.

## Technologie-Stack

### FOSS-Bibliotheken

- **ZXing** 4.1.0 - QR Code Generation/Scanning
- **BouncyCastle** 1.78 - Security/Cryptography
- **dnsjava** 3.6.2 - DNS Operations
- **OkHttp** 4.12.0 - HTTP Client
- **iText** 8.0.2 - PDF Export
- **MPAndroidChart** 3.1.0 - Chart Visualization

### Android Libraries

- **Material 3 Expressive** - Modern UI Design
- **Hilt** - Dependency Injection
- **Navigation Component** - In-App Navigation
- **Room** - Local Database
- **SQLCipher** - Encrypted Database
- **Coroutines** - Asynchronous Operations
- **Timber** - Logging

## Build & Installation

### Voraussetzungen

- Android Studio Hedgehog oder neuer
- JDK 17
- Android SDK 33+ (minSdk: 33, targetSdk: 36)

### Build

```bash
# Alle Module aktivieren
./gradlew assembleDebug

# Spezifisches Modul bauen
./gradlew :feature-utilities:assembleDebug

# Tests ausführen
./gradlew test
./gradlew connectedAndroidTest
```

### Module aktivieren/deaktivieren

Bearbeite `gradle.properties`:

```properties
feature.utilities.enabled=true
feature.backup.enabled=true
```

## UI Design

Die App verwendet **Material 3 Expressive** Design:

- **Expressive Color Scheme** - Mutige Farben mit Dynamic Color Support
- **Expressive Typography** - Größere, boldere Headlines
- **Expressive Shapes** - Größere Corner Radii (bis 28dp für heroische Cards)
- **Dynamic Color** - Material You Support (Android 12+)

Siehe [MATERIAL3_EXPRESSIVE_IMPLEMENTATION.md](docs/MATERIAL3_EXPRESSIVE_IMPLEMENTATION.md) für Details.

## Privacy & Security

- ✅ **Keine Google-Dienste** - Vollständig FOSS, keine Google Play Services
- ✅ **Lokale Datenverarbeitung** - Alle Daten bleiben lokal
- ✅ **Verschlüsselung** - SQLCipher für lokale Datenbank
- ✅ **Keine Telemetrie** - Keine Tracking oder Analytics
- ✅ **RASP Protection** - Runtime Application Self-Protection

## Entwicklung

### Neues Feature hinzufügen

1. **Modul erstellen** (falls neues Modul):
   ```bash
   mkdir -p feature-new-module/src/main/java/com/ble1st/connectias/feature/newmodule
   ```

2. **build.gradle.kts** erstellen mit Dependencies

3. **Hilt Module** erstellen:
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   object NewModuleModule
   ```

4. **Module Catalog** erweitern:
   ```kotlin
   ModuleMetadata(
       id = "new-module",
       name = "New Module",
       fragmentClassName = "...",
       category = ModuleCategory.UTILITY
   )
   ```

5. **Navigation** hinzufügen in `nav_graph.xml`

### Testing

```bash
# Unit Tests
./gradlew test

# Instrumented Tests
./gradlew connectedAndroidTest

# Alle Tests
./gradlew check
```

## Dokumentation

- [Architektur Plan](docs/ARCHITECTURE_PLAN.md)
- [MVP Plan](docs/MVP_PLAN.md)
- [Material 3 Expressive Design](docs/MODERN_UI_DESIGN_M3.md)
- [Material 3 Expressive Implementation](docs/MATERIAL3_EXPRESSIVE_IMPLEMENTATION.md)
- [FOSS Features Implementation Plan](foss-features-implementation-plan.plan.md)

## Lizenz

[Lizenz-Informationen hier einfügen]

## Contributing

[Contributing Guidelines hier einfügen]

## Changelog

### Version 1.0.0
- Initial Release
- Alle FOSS Features implementiert
- Material 3 Expressive UI
- Modulare Architektur mit dynamischer Module-Discovery

