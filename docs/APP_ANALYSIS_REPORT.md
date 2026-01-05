# App-Analyse & Feature-Dokumentation: Connectias

Dieses Dokument bietet eine umfassende Analyse der Connectias-App, basierend auf einer Untersuchung der Codebasis. Die App ist modular aufgebaut, nutzt moderne Android-Architekturen (MVVM, Hilt, Jetpack Compose) und legt einen starken Fokus auf Sicherheit und Netzwerk-Tools.

## 1. Modulübersicht

Die Anwendung ist in mehrere Gradle-Module unterteilt, um eine klare Trennung der Zuständigkeiten zu gewährleisten.

### **app** (Host-Modul)
*   **Kernfunktion:** Dient als Einstiegspunkt und Host für die Anwendung. Es verwaltet die Navigation (`nav_graph.xml`), initialisiert Hilt (`ConnectiasApplication`), und lädt Feature-Module dynamisch.
*   **Benutzerschnittstelle:** Beinhaltet die `MainActivity`, die als Container für Fragmente und Compose-Views dient.
*   **Wichtige Komponenten:**
    *   `MainActivity`: Haupt-Activity.
    *   `SecurityService`: Führt Sicherheitsprüfungen beim Start aus.
    *   `DashboardFragment`: Die zentrale Startseite der App.

### **common** (Shared Library)
*   **Kernfunktion:** Enthält gemeinsam genutzte UI-Komponenten, Themes, Utility-Funktionen und Datenmodelle, die von Feature-Modulen verwendet werden.
*   **Benutzerschnittstelle:**
    *   `DashboardScreen`: Eine Übersicht aller Features (Compose).
    *   Themes (`Theme.kt`, `Color.kt`, `Shape.kt`, `Type.kt`): Definiert das Corporate Design (Material 3 Expressive).
*   **Abhängigkeiten:** Keine Abhängigkeiten zu Feature-Modulen (vermeidet Zirkelbezüge).

### **core** (Core Functionality)
*   **Kernfunktion:** Beinhaltet zentrale Geschäftslogik und Infrastruktur, die app-weit benötigt wird.
*   **Cross-Cutting Concerns:**
    *   **Logging:** `ConnectiasLoggingTree` für strukturiertes Logging.
    *   **Einstellungen:** `SettingsRepository` zur Verwaltung von App-Einstellungen.
    *   **Sicherheit:** Implementierung von Sicherheitsmechanismen (Details weiter unten).

### **feature-network** (Netzwerk-Tools)
*   **Kernfunktion:** Eine Suite von Werkzeugen zur Netzwerkanalyse.
*   **Benutzerschnittstelle:**
    *   `NetworkToolsScreen`: Tab-basiertes UI für WLAN, LAN, Ports, Traceroute und SSL.
    *   `NetworkToolsViewModel`: Steuert die Logik und hält den State (`NetworkUiState`).
*   **Funktionen:**
    *   **WLAN Scanner:** Listet verfügbare WLANs auf (SSID, RSSI, Sicherheit).
    *   **LAN Scanner:** Scannt das lokale Netzwerk nach aktiven Hosts (ARP/Ping).
    *   **Port Scanner:** Prüft offene Ports auf Ziel-IPs/Domains (Presets oder Custom Range).
    *   **Traceroute:** Verfolgt die Route von Paketen zu einem Ziel.
    *   **SSL Scanner:** Analysiert SSL-Zertifikate von Domains.
*   **Externe Schnittstellen:** Nutzt Android Network APIs (`WifiManager`, `ConnectivityManager`) und Socket-Verbindungen.

### **feature-bluetooth** (Bluetooth Scanner)
*   **Kernfunktion:** Scannen und Anzeigen von Bluetooth Low Energy (BLE) und klassischen Bluetooth-Geräten.
*   **Benutzerschnittstelle:**
    *   `BluetoothScannerScreen`: Liste gefundener Geräte mit Signalstärke.
    *   `BluetoothScannerViewModel`: Verwaltet den Scan-Vorgang und Ergebnisse.
*   **Funktionen:**
    *   **Scan:** Findet Geräte in der Nähe.
    *   **Details:** Zeigt RSSI (Signalstärke), MAC-Adresse und Gerätenamen an.
    *   **Radar:** Visuelle Darstellung der Annäherung (basierend auf RSSI).
*   **Externe Schnittstellen:** Android Bluetooth API (`BluetoothAdapter`, `BluetoothLeScanner`).

### **feature-secure-notes** (Sichere Notizen)
*   **Kernfunktion:** Verschlüsselte Speicherung von sensiblen Notizen.
*   **Benutzerschnittstelle:**
    *   `SecureNotesScreen`: Liste von Notizen, Editor, biometrischer Login.
    *   `SecureNotesViewModel`: Handhabt Verschlüsselung und CRUD-Operationen.
*   **Funktionen:**
    *   **Verschlüsselung:** AES-256-GCM Verschlüsselung aller Inhalte.
    *   **Authentifizierung:** Zugriffsschutz via Biometrie (Fingerprint/FaceID).
    *   **Organisation:** Kategorien, Tags und Pinnen von Notizen.
    *   **Suche:** Durchsuchbare Notizen (nach Entschlüsselung im Speicher).
*   **Datenbank:** Speichert verschlüsselte Blobs (wahrscheinlich Room/SQLite).

### **feature-usb** (USB Tools)
*   **Kernfunktion:** Verwaltung und Analyse angeschlossener USB-Geräte.
*   **Benutzerschnittstelle:**
    *   `UsbDashboardScreen`: Liste angeschlossener Geräte.
    *   `UsbDashboardViewModel`: Überwacht USB-Verbindungen.
*   **Funktionen:**
    *   **Erkennung:** Automatisches Erkennen von USB-Geräten (`UsbDeviceDetector`).
    *   **Details:** Anzeige von Vendor-ID, Product-ID, Hersteller etc.
    *   **Mass Storage:** Zugriff auf USB-Speichermedien (via `UsbStorageBrowserFragment`).
*   **Externe Schnittstellen:** Android USB Host API.

### **feature-dvd** (Media Player)
*   **Kernfunktion:** Wiedergabe von Medien (DVD/CD) - *Hinweis: Spezifische Hardware-Unterstützung (externes Laufwerk via USB) wahrscheinlich.*
*   **Benutzerschnittstelle:** `DvdPlayerFragment`.
*   **Status:** In der Analyse als "sehr anfällig" markiert (User-Anweisung: "Keine Änderungen in feature-dvd").

### **feature-settings** (Einstellungen)
*   **Kernfunktion:** Konfiguration der App.
*   **Benutzerschnittstelle:** `SettingsScreen`.
*   **Funktionen:**
    *   Theme-Auswahl (Hell/Dunkel/System, Standard/Adeptus Mechanicus).
    *   Sicherheitseinstellungen (Auto-Lock, RASP Logging).
    *   Logging-Level.
    *   Reset-Funktionen.

---

## 2. Detaillierte Funktionsbeschreibung

### Input/Output
*   **Input:** Touch-Interaktionen (Compose), Kamera (Dokumente), Sensoren (Bluetooth/Wifi/USB), Biometrie.
*   **Output:** Visuelle Darstellung (Material 3 UI), PDF-Dateien (Export), Log-Dateien.

### Verarbeitungslogik
*   **Asynchronität:** Intensive Nutzung von **Kotlin Coroutines** (`viewModelScope`, `suspend functions`) für Netzwerk-, Datenbank- und Hardware-Operationen, um den UI-Thread nicht zu blockieren.
*   **State Management:** **StateFlow** in ViewModels dient als "Single Source of Truth" für die UI.
*   **Dependency Injection:** **Hilt** wird verwendet, um Komponenten (Scanner, Repositories, Services) zu entkoppeln und Testbarkeit zu erhöhen.

### Abhängigkeiten
*   **Android Jetpack:** Core-KTX, Lifecycle, Navigation, Room, CameraX, Biometric.
*   **UI:** Jetpack Compose, Material 3.
*   **Netzwerk:** OkHttp, dnsjava (für DNS/Netzwerk-Tools).
*   **Hardware:** LibUSBCamera/LibAums (USB), ZXing/MLKit (Barcode/OCR).
*   **Sicherheit:** RootBeer (Root Detection), Android Keystore System.

---

## 3. Nicht-funktionale Anforderungen

### Performanceziele
*   **Reaktive UI:** Nutzung von Compose und asynchronen Datenströmen für flüssige Bedienung.
*   **Effiziente Scans:** Netzwerk- und Bluetooth-Scans laufen im Hintergrund.
*   **Schnelle Startzeit:** Optimierung durch Lazy Loading von Modulen (wo möglich) und Hilt.

### Sicherheitsaspekte
*   **Verschlüsselung:** Starke Verschlüsselung (AES-256) für `feature-secure-notes`.
*   **Zugriffskontrolle:** Biometrische Authentifizierung für sensible Bereiche.
*   **RASP (Runtime Application Self-Protection):** Erkennung von Rooting, Debugging und Hooking-Frameworks (Hinweise im Code auf `RootBeer` und `SecurityService`).
*   **Sichere Kommunikation:** SSL/TLS-Prüfung in Netzwerk-Tools.

### Skalierbarkeit & Wartbarkeit
*   **Modularisierung:** Klare Trennung in Feature-Module ermöglicht parallele Entwicklung und einfache Erweiterung.
*   **Moderner Tech-Stack:** Einsatz aktueller Standards (Kotlin, Compose) sichert Zukunftsfähigkeit.

---

## 4. Testabdeckung (Status Quo)

Basierend auf der Struktur:
*   **Unit-Tests:** Vorhanden für Kernlogik in ViewModels (z.B. `NetworkToolsViewModelTest`). Ordnerstruktur `src/test` in Modulen vorhanden.
*   **Integrationstests:** Infrastruktur (`androidTest` Ordner) ist in den meisten Modulen angelegt.
*   **End-to-End-Tests:** Keine expliziten Hinweise auf ein umfangreiches E2E-Framework (wie Appium oder Maestro) im Repository-Root gefunden, aber UI-Tests via Compose Test Rules sind möglich.

**Empfehlung:** Ausbau der Unit-Tests für alle ViewModels und Implementierung von UI-Tests für die kritischen Pfade (Login, Notiz erstellen, Scan starten).
