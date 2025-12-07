---
name: connectias-sicherheitsanalyse
overview: Plan für vollständige sicherheits- und funktionsanalyse der Connectias-Android-App inklusive Feature-Inventur, Sicherheitsprüfung (Kotlin/NDK), Architektur- und Konsolidierungsbewertung ohne Codeänderungen.
todos:
  - id: manifeste-permissions
    content: Manifeste/Permissions/Exported prüfen
    status: pending
  - id: build-config-security
    content: Build-/Netzwerk-/Obfuskation-Config sichten
    status: pending
  - id: core-security
    content: Core-Security/Pinning/RASP prüfen
    status: pending
  - id: feature-security-crypto
    content: Security-Feature (Crypto/Analyzer) prüfen
    status: pending
  - id: network-module
    content: Network-Feature Eingaben/Timeouts prüfen
    status: pending
  - id: other-features
    content: Device-Info/Secure-Notes/Reporting/Settings prüfen
    status: pending
  - id: wasm-plugin
    content: WASM-Sandbox/Security analysieren
    status: pending
  - id: ndk-usb-dvd
    content: NDK USB/DVD Boundaries/Checks analysieren
    status: pending
  - id: consolidation
    content: Feature-Inventur & Konsolidierung (ohne USB/DVD) aufstellen
    status: pending
  - id: findings-report
    content: Findings priorisieren & Bericht strukturieren
    status: pending
---

# Plan: Vollständige Analyse Connectias (Security & Features)

## 1. Übersicht

Ziel: Umfassende Bestandsaufnahme aller Features und Sicherheitsbewertung (Kotlin/NDK/Compose/Hilt) inkl. Konsolidierungsoptionen (USB/DVD ausgeschlossen), mit priorisierten Empfehlungen.

## 2. Analyse-Phase

### Aktueller Zustand

- Multi-Modul-App (Compose, Hilt, Room+SQLCipher, OkHttp, dnsjava, BouncyCastle, Timber); native DVD/USB via NDK.
- RASP/Pinning-Infra im `core/security/*`, Netzwerk-Config in `core/src/main/res/xml/network_security_config.xml`.

### Identifizierte Probleme/Anforderungen

- Vollständige Feature-Inventur + Bewertung (Value, Maintainability, Security Risk, Konsolidierung; USB/DVD ausgenommen).
- Security-Review (Permissions, exported Components, TLS/Pinning, Crypto, Secrets, Input-Validation, Logging, NDK Boundaries).
- Architektur-/Codequalität-Check (MVVM/Clean, DI, Duplication, State/Coroutines).

### Abhängigkeiten

- Module: `:app`, `:core`, `:common`, `:feature-security`, `:feature-network`, `:feature-device-info`, `:feature-usb`, `:feature-dvd`, `:feature-wasm`, `:feature-secure-notes`, `:feature-reporting`, `:feature-settings` (+ evtl. weitere optionale laut README/features_analysis).
- Build-Toggles in `gradle.properties`; ProGuard/R8 in `app/proguard-rules.pro`.

### Risiken

- Mittel: Umfang groß, Zeitbedarf hoch.
- Hoch: Falsch-positive/negative bei Security-Review ohne Laufzeit-Kontext; NDK-Sicherheitsbewertung erfordert Sorgfalt.

## 3. Lösungsansatz

- Statisch lesen/klassifizieren der Module, Manifeste, Gradle-Configs, Security-Komponenten, NDK-Code; Befunde nach Severity priorisieren.
- Fokus auf OWASP Mobile Top 10, RASP, TLS/Pinning, Keystore, Path Traversal, SQLi, Logging-Leaks, exported Components.
- Konsolidierung nur außerhalb USB/DVD.

## 4. Detaillierte Schritte

### Schritt 1: Manifest- & Permission-Scan (Risiko: Mittel)

- **Datei(n):** `app/src/main/AndroidManifest.xml`, Modul-Manifeste (z.B. `feature-*/src/main/AndroidManifest.xml`)
- **Was:** Permissions, exported Activities/Services/Providers, intent-filter, allowBackup, cleartextTraffic prüfen.
- **Warum:** Frühzeitige Security/Oberflächen-Bewertung.
- **Erwartetes Ergebnis:** Liste sicherheitsrelevanter Komponenten & potenzieller Schwachstellen.

### Schritt 2: Build-/Security-Konfig prüfen (Risiko: Mittel)

- **Datei(n):** `gradle.properties`, Modul-`build.gradle.kts`, `app/proguard-rules.pro`, `core/consumer-rules.pro`, `core/src/main/res/xml/network_security_config.xml`.
- **Was:** Feature-Toggles, Min/Target SDK, R8/ProGuard, Minify/Obfuscation, Netzwerk-Security-Config, Pinning-Hooks.
- **Warum:** Rahmenbedingungen für Security/Obfuskation/TLS.
- **Erwartetes Ergebnis:** Klarheit über aktiven Schutz (Obfuskation, Pinning, Cleartext-Regeln).

### Schritt 3: Core-Security & Services prüfen (Risiko: Hoch)

- **Datei(n):** `core/src/main/java/com/ble1st/connectias/core/security/**`, `core/services/**`, `core/logging/**`.
- **Was:** RASP (root/emu/tamper/debugger), Pinning (`PinningInterceptor`, `SslPinningManager`), Logging-Sanitization, Error-Handling.
- **Warum:** Kern-Sicherheitsniveau bestimmen.
- **Erwartetes Ergebnis:** Befunde zu RASP-Wirksamkeit, Pinning-Implementierung, Logging-Leaks.

### Schritt 4: Feature-Security-Modul prüfen (Risiko: Mittel)

- **Datei(n):** `feature-security/src/main/java/**` (+ Tests).
- **Was:** Crypto (AES-GCM, PBKDF2, Keystore), Passwort-Checker, Zertifikats-Analyzer, Firewall-Analyzer; Input-Validierung, Fehler-/Log-Handling.
- **Warum:** Korrekte und sichere Kryptonutzung sicherstellen.
- **Erwartetes Ergebnis:** Liste sicherer/unsicherer Crypto-Pfade, Empfehlungen.

### Schritt 5: Netzwerk-Features prüfen (Risiko: Mittel)

- **Datei(n):** `feature-network/src/main/java/**`.
- **Was:** dnsjava/OkHttp-Nutzung, Timeouts, Input-Validation (Hosts/Ports), Threading/Coroutines, Logging.
- **Warum:** Netzwerk-Angriffflächen bewerten (MITM, DoS, Injection via Inputs).
- **Erwartetes Ergebnis:** Risiken/Empfehlungen zu Netzwerkpfaden.

### Schritt 6: Device-Info & andere Features (Risiko: Niedrig-Mittel)

- **Datei(n):** `feature-device-info/**`, `feature-secure-notes/**`, `feature-reporting/**`, `feature-settings/**`.
- **Was:** Sensitive-Daten-Handhabung, Storage (SQLCipher/Prefs/Files), Berechtigungen, Logging, DI/Architektur.
- **Warum:** Data-at-rest & Privatsphäre prüfen.
- **Erwartetes Ergebnis:** Datenschutz-/Storage-Befunde, Architektursauberkeit.

### Schritt 7: WASM-Plugin-System prüfen (Risiko: Hoch)

- **Datei(n):** `feature-wasm/**`.
- **Was:** Sandbox/Isolation, Ressourcenlimits, File/Network-Zugriff, Input-Validation, Logging, Tests.
- **Warum:** Potenziell hohe Angriffsfläche durch Plugins.
- **Erwartetes Ergebnis:** Risiken & Härtungsempfehlungen (Sandboxing, Policy).

### Schritt 8: Native USB/DVD (NDK) prüfen (Risiko: Hoch)

- **Datei(n):** `feature-dvd/src/main/cpp/**`, `feature-usb/src/main/java/**`.
- **Was:** JNI-Boundaries, Buffer/Length-Checks, Error-Handling, Path Traversal, Logging sensibler Daten, Thread-Safety; Trennung USB/DVD einhalten.
- **Warum:** Speicher-/Boundary-Risiken minimieren.
- **Erwartetes Ergebnis:** NDK-spezifische Schwachstellen/Empfehlungen.

### Schritt 9: Feature-Inventur & Konsolidierung (Risiko: Niedrig)

- **Datei(n):** README, `docs/features_analysis.md`, Modulquelltexte.
- **Was:** Zweck/Value/Maintainability/Security-Risk je Feature; Konsolidierungsvorschläge außer USB/DVD.
- **Warum:** Wartbarkeit verbessern, Komplexität senken.
- **Erwartetes Ergebnis:** Tabelle je Feature + Konsolidierungsempfehlungen.

### Schritt 10: Befunde strukturieren & priorisieren (Risiko: Niedrig)

- **Was:** Findings nach Severity (Critical/High/Medium/Low) mit Pfad/Zeile, Impact, Fix.
- **Warum:** Klare Handlungspriorisierung.
- **Erwartetes Ergebnis:** Executive Summary, Security Findings, Code-Verbesserungen, Konsolidierungsvorschläge.

## 5. Test-Strategie

- Statisch: Nur Read-only Review; keine Code-Ausführung. 
- Validierung über Quervergleich von Implementierungen (z.B. Pinning-Interceptor vs. network_security_config) und Tests in Repo (`feature-security`/`feature-wasm` Tests) nur lesen.
- Plausibilitätschecks gegen OWASP M10 & Android Security Best Practices.

## 6. Rollback-Plan

- Nicht erforderlich (keine Änderungen). Falls Empfehlungen übernommen werden, Änderungen via Branch/PR mit Revert-Möglichkeit dokumentieren.

## 7. Annahmen

- Build-Varianten/Flavors nutzen Standard-Konfiguration aus `gradle.properties`.
- Minify/Obfuskation-Status wird aus `build.gradle.kts`/ProGuard abgeleitet.
- Keine externen, nicht eingecheckten Secrets.

## 8. Offene Fragen

- Keine blockierenden Fragen; falls spezielle Fokusbereiche existieren (z.B. bestimmter Angriffsvektor), bitte nennen.