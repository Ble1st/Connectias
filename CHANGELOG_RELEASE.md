# Changelog - Release Build

**Datum:** 2026-01-27  
**Seit letztem Release:** ba51345 (releasebuild)

## 🎯 Hauptfunktionen

### Three-Process UI Architecture
- ✅ Vollständige Implementierung der Drei-Prozess-UI-Architektur
  - Main Process: Orchestriert Plugin-Lifecycle
  - Sandbox Process: Isolierte Plugin-Business-Logik
  - UI Process: UI-Rendering mit Jetpack Compose
- ✅ Neue AIDL-Interfaces für UI-Kommunikation:
  - `IPluginUIController` (Sandbox → UI Process)
  - `IPluginUIBridge` (UI → Sandbox Process)
  - `IPluginUIHost` (Main → UI Process)
- ✅ Parcelable-Klassen für UI-State-Transfer:
  - `UIStateParcel`, `UIComponentParcel`, `UserActionParcel`, `UIEventParcel`, `MotionEventParcel`
- ✅ `PluginUIService` im UI-Process (`:plugin_ui`)
- ✅ `PluginUIComposable` für Compose-basiertes UI-Rendering
- ✅ `UIStateDiffer` für Performance-Optimierung (60-80% IPC-Reduktion)

### Plugin SDK Erweiterungen
- ✅ `PluginUIBuilder` DSL für deklaratives UI-Building
- ✅ Erweiterte `IPlugin` Interface mit UI-Methoden:
  - `onRenderUI()` - Generiert UI-State für Rendering
  - `onUserAction()` - Verarbeitet User-Interaktionen
  - `onUILifecycle()` - Behandelt UI-Lifecycle-Events
- ✅ `PluginUIController` Interface für Plugin-Entwickler

### Inter-Plugin Messaging
- ✅ `PluginMessageBroker` für Nachrichten-Routing zwischen Plugins
- ✅ Request/Response-Message-System
- ✅ Rate Limiting: 100 Nachrichten/Sekunde pro Plugin
- ✅ Payload-Größenlimit: 1MB pro Nachricht
- ✅ Timeout-Handling: 5 Sekunden Standard-Timeout

### API Rate Limiting
- ✅ `IPCRateLimiter` mit Token-Bucket-Algorithmus
- ✅ Per-Method und Per-Plugin Rate Limits
- ✅ Konfigurierbare Limits (per-second, per-minute, burst)
- ✅ Rate-Limit-Exceptions mit Retry-After-Informationen

## 🔧 Verbesserungen

### Plugin System
- ✅ Legacy-Plugin-Unterstützung in `PluginManagerSandbox`
- ✅ Verbesserte Fragment-Erstellung mit UI-Isolation
- ✅ Plugin-Integritätsprüfung mit Checksum-Validierung
- ✅ Plugin-Logging und Analytics-Features

### Performance
- ✅ UI-State-Diffing reduziert IPC-Overhead um 60-80%
- ✅ Optimierte Rate-Limiter-Tests (von 8+ Minuten auf <1 Sekunde)
- ✅ Parallele Message-Verarbeitung

### Build & Release
- ✅ ProGuard/R8-Regeln für Three-Process-UI-Architektur
  - AIDL-Interfaces und Parcelables geschützt
  - Compose-Komponenten erhalten
  - Service-Klassen für IPC-Kommunikation geschützt
- ✅ Consumer-ProGuard-Regeln für Plugin-SDK aktualisiert

## 📚 Dokumentation

- ✅ `THREE_PROCESS_UI_PLAN.md` - Vollständiger Implementierungsplan
- ✅ `THREE_PROCESS_UI_PERFORMANCE.md` - Performance-Dokumentation
- ✅ `PLUGIN_MESSAGING.md` - Inter-Plugin-Messaging-Dokumentation
- ✅ `API_RATE_LIMITING.md` - Rate-Limiting-Dokumentation

## 🐛 Bugfixes

- ✅ Rate-Limiter-Tests optimiert (verhindert 5+ Minuten Timeouts)
- ✅ Plugin-Message-Broker-Tests mit korrekter Response-Verarbeitung
- ✅ Token-Count-Tests korrigiert für korrekte Initialisierung

## 📦 Dependencies

- ✅ Hilt Navigation Compose hinzugefügt
- ✅ Plugin-SDK-Struktur refactored

## 🔒 Sicherheit

- ✅ Plugin-Integritätsprüfung mit Checksum-Validierung
- ✅ Rate-Limiting für alle IPC-Methoden
- ✅ Message-Payload-Größenlimits

---

**Commits seit letztem Release:**
- b89eaaa - Add ProGuard Rules for Three-Process UI Architecture and Update Rate Limiter Tests
- c00a861 - Refactor Plugin SDK Structure and Update Documentation
- fee290d - Enhance Declarative Plugin System with Node Registry and Flow Engine Improvements
- 7d13043 - Enhance Plugin System with Fullscreen UI and Declarative Features
- 99e3a57 - Implement Plugin Logging and Analytics Features
- cfa0462 - Enhance MainActivity and Plugin UI Management for Improved User Experience
- 8892ef7 - Enhance PluginSandboxService and introduce new UI components for Three-Process Architecture
- 324fa43 - Refactor PluginManagerSandbox to support legacy plugins and improve fragment creation
- d019d9a - Add Three-Process UI Architecture and enhance plugin UI management
- 74f64e9 - Add inter-plugin messaging and API rate limiting features
- ead948b - Add feature documentation and implementation plan for Connectias Plugin System
- 7e49065 - Add Hilt Navigation Compose dependency and enhance plugin integrity verification with checksum validation
