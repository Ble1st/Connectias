<!-- 8f0838cf-3170-432f-8dfa-cb72e5a8e44f 71d08b37-fdb2-4d99-9d6a-5e5eb96b81fe -->
# Plan: Verbesserungen für Permissions, LAN-Scanner, Tests & Module-Discovery

## 1. Übersicht

Ziel ist, vier Optimierungsbereiche des Network-Features systematisch zu adressieren: (1) klarere Permission-/Error-Flows, (2) Performance & Konfigurierbarkeit des LAN-Scanners, (3) Tests/Mocks für Netzwerkkomponenten, (4) strukturierte Module-Discovery inklusive Metadaten. Ergebnis ist ein stabileres und besser wartbares Feature mit klarer UX.

## 2. Analyse-Phase

- Aktuell wirft `WlanScanner.ensurePermissions()` Exceptions, die nur indirekt im UI landen. LAN-Scans liefern leise leere Listen, wenn Gateway/Subnet fehlen.
- `NetworkDiscoveryService` sweep’t immer /24 seriell; Performance und Abbruchkriterien fehlen.
- Tests für Scanner/Repository existieren nicht; Abdeckung nur über manuelle Prüfungen.
- Module-Registrierung liegt hart im `MainActivity`, Metadaten (Name/Icon/Route) sind verstreut.

## 3. Lösungsansatz

- Einführung strukturierter `NetworkScanStatus`-/`PermissionState`-Objekte, die Repository/ViewModel abbilden kann.
- LAN-Scanner mit konfigurierbaren Parametern (max Hosts, Timeout, Parallelität) und aussagekräftigen Ergebnissen.
- Infrastruktur für Unit-Tests: Interfaces oder Fake-Implementierungen von WifiManager/Discovery, plus Test-Cases für Repository.
- Zentralisierte Module-Metadaten (z. B. sealed class oder JSON) + Discovery-Service, den `MainActivity` nur noch konsumiert.

## 4. Detaillierte Schritte

### Schritt 1: Permission- & Error-State-Modell

- **Dateien:** `feature-network/models/NetworkResult.kt`, `NetworkRepository.kt`, `NetworkDashboardViewModel.kt`, `NetworkDashboardFragment.kt`
- **Was:** Neue Typen (z. B. `PermissionState`, `ScanStatus`) einführen, Repository liefert differenzierte Error Codes statt Exceptions; Fragment zeigt gezielte Hinweise.
- **Risiko:** Mittel – UI/State-Änderungen.

### Schritt 2: LAN-Scanner konfigurierbar machen

- **Dateien:** `NetworkDiscoveryService.kt`, `LanScannerProvider.kt`, ggf. neue Konfigurationsklasse.
- **Was:** Parameter (Subnet Limit, Parallel Jobs, Timeout) einführbar, Logging erweitern, optional Abbruch nach X Geräten.
- **Risiko:** Mittel – Performance/Threading.

### Schritt 3: Tests & Mocks

- **Dateien:** `feature-network/src/test/...` (neu), Interfaces oder Adapter in `WlanScanner`/`NetworkDiscoveryService`.
- **Was:** Testbare Schnittstellen schaffen (z. B. `WifiClient`), Unit-Tests für Repository (Success/Error/Permission), Mocks für Scanner.
- **Risiko:** Niedrig.

### Schritt 4: Module-Discovery mit Metadaten

- **Dateien:** `core/module/ModuleRegistry.kt`, `MainActivity.kt`, neue Datei `ModuleCatalog.kt`.
- **Was:** Module-Definition zentralisieren (ID, Name, Icon, Navigation Route). Discovery lädt Liste und registriert + UI (BottomNav, FAB) konsumiert daraus.
- **Risiko:** Mittel – Navigation-Anpassungen nötig.

## 5. Test-Strategie

- Unit-Tests für neue State-Modelle und Repository.
- Instrumentation-/manuelle Tests für Permission-Flows (Grant/Deny) und LAN-Scan-Konfiguration.
- UI Smoke-Test, dass Navigation/Module weiterhin angezeigt werden.

## 6. Rollback-Plan

- Jede Änderung in eigenem Branch; bei Problemen per Git revert.
- Für Module-Discovery alte `MainActivity`-Registrierung wiederherstellen.

## 7. Annahmen

- Feature-Nutzer akzeptieren ggf. zusätzliche Dialoge/Hinweise.
- Build-Performance bleibt trotz zusätzlicher Tests stabil.

## 8. Offene Fragen

- Soll Module-Metadaten-Quelle konfigurierbar (z. B. remote) sein oder reicht lokale Definition?
- Gibt es Anforderungen an maximale Scan-Dauer (UX)?

### To-dos

- [ ] Codebasis Connectias analysieren (Features/Implementation)