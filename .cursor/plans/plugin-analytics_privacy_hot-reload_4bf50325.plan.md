---
name: Plugin-Analytics Privacy Hot-Reload
overview: Plan zur Erweiterung von Plugin-Analytics (Nutzungsstatistiken), zur DSGVO-konformen Ausrichtung des Privacy Dashboards und zur Einführung eines Hot-Reload für Plugins ohne App-Neustart.
todos: []
isProject: false
---

# Plan: Plugin-Analytics ausbauen, Privacy Dashboard (DSGVO), Hot-Reload

## 1. Ausgangslage

**Plugin-Analytics (teilweise vorhanden):**

- [PluginAnalyticsStore](app/src/main/java/com/ble1st/connectias/analytics/store/PluginAnalyticsStore.kt): JSONL-Speicher für Perf-Samples, UI-Actions, Security-Events
- [PluginAnalyticsCollector](app/src/main/java/com/ble1st/connectias/analytics/collector/PluginAnalyticsCollector.kt): sammelt CPU/RAM/Disk/Net aus `EnhancedPluginResourceLimiter` und Security-Events
- [PluginUiActionLogger](app/src/main/java/com/ble1st/connectias/analytics/ui/PluginUiActionLogger.kt): wird in [PluginUIFragment](app/src/main/java/com/ble1st/connectias/core/plugin/ui/PluginUIFragment.kt) bei Plugin-Öffnung/Interaktion aufgerufen
- [PluginAnalyticsRepository](app/src/main/java/com/ble1st/connectias/analytics/repo/PluginAnalyticsRepository.kt): Aggregation zu `PluginPerfStats` (avg/peak CPU, Mem, Net, uiActions, rateLimitHits)
- [PluginAnalyticsDashboardScreen](app/src/main/java/com/ble1st/connectias/ui/plugin/analytics/PluginAnalyticsDashboardScreen.kt): Zeitfenster 24h/7d/30d, Liste pro Plugin, verschlüsselter Export

**Privacy Dashboard (vorhanden, DSGVO-Lücken):**

- [PrivacyDashboardScreen](app/src/main/java/com/ble1st/connectias/ui/plugin/privacy/PrivacyDashboardScreen.kt): Zeitfenster, [PrivacyAggregator](app/src/main/java/com/ble1st/connectias/privacy/PrivacyAggregator.kt) (Audit, Permission, Network, Data-Leakage), verschlüsselter Export, Plugin-Drilldown
- Fehlt: explizite Art. 15 „Auskunft“-Kennzeichnung, Übersicht der Datenkategorien pro Verarbeitung

**Hot-Reload:**

- [PluginManagerSandbox](app/src/main/java/com/ble1st/connectias/plugin/PluginManagerSandbox.kt): `loadPlugin(File)`, `unloadPlugin(pluginId)`; `PluginInfo` enthält `pluginFile`
- [PluginSandboxService](app/src/main/java/com/ble1st/connectias/core/plugin/PluginSandboxService.kt): `performUnload` räumt Sandbox vollständig auf
- **Problem:** `unloadPlugin` löscht aktuell die Plugin-Datei und das Plugin-Datenverzeichnis (Zeilen 837–864), daher ist Reload aus derselben Datei so nicht möglich

---

## 2. Plugin-Analytics ausbauen (Hohe Priorität)

**Ziel:** Vollständige Nutzungsstatistiken (Session-Dauer, Lifecycle-Events, optional Darstellung).

**2.1 Session-/Nutzungsdauer pro Plugin**

- **Modell:** Neue Events „plugin_foreground_start“ / „plugin_foreground_end“ (oder ein `PluginSessionEvent` mit start/end timestamp) in [PluginAnalyticsModels](app/src/main/java/com/ble1st/connectias/analytics/model/PluginAnalyticsModels.kt)
- **Speicher:** Neues JSONL in `PluginAnalyticsStore` (z. B. `sessions.jsonl`) oder bestehende `ui_actions.jsonl` um Session-Typ erweitern
- **Erfassung:** In [PluginUIFragment](app/src/main/java/com/ble1st/connectias/core/plugin/ui/PluginUIFragment.kt) bei Fragment-onResume „foreground_start“, bei onPause „foreground_end“ an [PluginUiActionLogger](app/src/main/java/com/ble1st/connectias/analytics/ui/PluginUiActionLogger.kt) (oder dedizierten SessionLogger) senden
- **Repository:** In [PluginAnalyticsRepository](app/src/main/java/com/ble1st/connectias/analytics/repo/PluginAnalyticsRepository.kt) Session-Dauer pro Plugin im Zeitfenster aggregieren (Summe, Anzahl Sessions) und in `PluginPerfStats` oder ein neues `PluginUsageStats` aufnehmen
- **UI:** In [PluginAnalyticsDashboardScreen](app/src/main/java/com/ble1st/connectias/ui/plugin/analytics/PluginAnalyticsDashboardScreen.kt) Session-Dauer (z. B. „X min genutzt“) und ggf. Session-Anzahl anzeigen

**2.2 Lifecycle-Events (Enable/Disable/Install)**

- **Modell:** Events z. B. `plugin_enabled`, `plugin_disabled`, `plugin_installed`, `plugin_uninstalled` (timestamp, pluginId)
- **Erfassung:** In [PluginManagerSandbox](app/src/main/java/com/ble1st/connectias/plugin/PluginManagerSandbox.kt) nach erfolgreichem enable/disable/load/unload [PluginUiActionLogger](app/src/main/java/com/ble1st/connectias/analytics/ui/PluginUiActionLogger.kt) oder Store aufrufen (Hilt-EntryPoint, da ggf. aus anderem Modul)
- **Repository/UI:** Optional in Aggregation und Dashboard (z. B. „Aktivierungen im Zeitraum“)

**2.3 Retention und Export**

- `PluginAnalyticsStore.compactRetention` und Collector bereits vorhanden; Session-Datei in Compaction einbeziehen
- Analytics-Export ([EncryptedAnalyticsExportWriter](app/src/main/java/com/ble1st/connectias/analytics/export/EncryptedAnalyticsExportWriter.kt)) um Session-/Lifecycle-Daten erweitern, falls neue Dateien/Modelle hinzugekommen sind

**Betroffene Dateien (Kern):**

- `app/src/main/java/com/ble1st/connectias/analytics/model/PluginAnalyticsModels.kt`
- `app/src/main/java/com/ble1st/connectias/analytics/store/PluginAnalyticsStore.kt`
- `app/src/main/java/com/ble1st/connectias/analytics/repo/PluginAnalyticsRepository.kt`
- `app/src/main/java/com/ble1st/connectias/core/plugin/ui/PluginUIFragment.kt`
- `app/src/main/java/com/ble1st/connectias/plugin/PluginManagerSandbox.kt` (Lifecycle-Events)
- `app/src/main/java/com/ble1st/connectias/ui/plugin/analytics/PluginAnalyticsDashboardScreen.kt`

---

## 3. Privacy Dashboard / DSGVO-Übersicht (Hohe Priorität)

**Ziel:** Klare Art. 15 Auskunft und Übersicht der verarbeiteten Datenkategorien.

**3.1 Art. 15 Auskunft kennzeichnen**

- In [PrivacyDashboardScreen](app/src/main/java/com/ble1st/connectias/ui/plugin/privacy/PrivacyDashboardScreen.kt): Überschrift/Sektion „Auskunft nach Art. 15 DSGVO“ bzw. „Ihre Daten (Art. 15 DSGVO)“ und kurzer Hinweistext, dass der Export die gespeicherten personenbezogenen/plugin-bezogenen Daten enthält
- Optional: Link/Hinweis auf Löschung (Art. 17) z. B. „Plugin deinstallieren entfernt zugehörige Daten“

**3.2 Datenkategorien-Übersicht**

- **Inhalt:** Pro Datenkategorie (Audit-Ereignisse, Berechtigungsnutzung, Netzwerk-Nutzung, Data-Leakage-Ereignisse) kurze Beschreibung: was gespeichert wird, Zweck, Speicherdauer/Relevanz für Zeitfenster
- **Umsetzung:** Neue Composable-Sektion „Datenkategorien“ auf dem gleichen Screen (oder eigene Sektion „Was wir speichern“) mit Liste: Kategorie | Beschreibung | Quelle (z. B. SecurityAuditManager, PluginPermissionMonitor, …)
- Daten können aus [PrivacyAggregator](app/src/main/java/com/ble1st/connectias/privacy/PrivacyAggregator.kt) und Export-Modellen abgeleitet werden; keine neue Backend-Logik nötig, nur Texte und Struktur

**3.3 Export-Label**

- Button „Verschlüsselter Export“ um Formulierung ergänzen, z. B. „Auskunft exportieren (Art. 15 DSGVO)“ oder Tooltip/Subtitle mit diesem Hinweis

**Betroffene Dateien:**

- `app/src/main/java/com/ble1st/connectias/ui/plugin/privacy/PrivacyDashboardScreen.kt`

---

## 4. Hot-Reload (Mittlere Priorität)

**Ziel:** Plugin ohne App-Neustart neu laden (z. B. nach APK-Ersetzung im Plugin-Verzeichnis oder für Entwickler).

**4.1 Unload ohne Dateilöschung**

- [PluginManagerSandbox.unloadPlugin](app/src/main/java/com/ble1st/connectias/plugin/PluginManagerSandbox.kt) um Parameter erweitern: `deletePluginFile: Boolean = true` (und ggf. `deletePluginDataDir: Boolean = true`)
- Wenn `deletePluginFile == false`: Plugin-Datei nicht löschen; wenn `deletePluginDataDir == false`: Plugin-Datenverzeichnis nicht löschen (optional, für „clean reload“ kann beides true bleiben)
- Sandbox-Unload und restliche Aufräumarbeiten (Resource-Limiter, ModuleRegistry, native libs, dex) unverändert; nur die beiden Delete-Blöcke (Plugin-Datei, Datenverzeichnis) von dem Flag abhängig machen

**4.2 Reload-API**

- Neue Methode in [PluginManagerSandbox](app/src/main/java/com/ble1st/connectias/plugin/PluginManagerSandbox.kt):  
`suspend fun reloadPlugin(pluginId: String): Result<PluginMetadata>`
  - `val info = getPlugin(pluginId) ?: return Result.failure(NoSuchElementException(...))`
  - `val file = info.pluginFile` (File-Referenz sichern)
  - `unloadPlugin(pluginId, deletePluginFile = false, deletePluginDataDir = false)` (damit Datei für Reload erhalten bleibt)
  - `loadPlugin(file)` und ggf. je nach gewünschtem Default-Zustand `enablePlugin(metadata.pluginId)` aufrufen
  - Ergebnis: `Result.success(metadata)` bzw. Fehler von load/enable

**4.3 UI-Zugang (optional)**

- In [PluginManagementScreen](app/src/main/java/com/ble1st/connectias/ui/plugin/PluginManagementScreen.kt) oder Plugin-Detail: Button „Neu laden“ (z. B. nur im Debug-Build oder für Nutzer sichtbar), der `pluginManager.reloadPlugin(pluginId)` aufruft und Snackbar/Fehlerbehandlung anzeigt
- Nach Reload: `pluginsFlow` aktualisiert sich über `updateFlow()` in `loadPlugin`, UI zeigt aktualisierten Status

**4.4 Rate-Limiting**

- [IPCRateLimiter](app/src/main/java/com/ble1st/connectias/plugin/security/IPCRateLimiter.kt): Prüfen, ob „reload“ als eigene Methode geloggt werden soll oder als load+unload (bereits limitiert); ggf. `reloadPlugin` nur im Main Process, kein neuer AIDL-Call nötig

**Betroffene Dateien:**

- `app/src/main/java/com/ble1st/connectias/plugin/PluginManagerSandbox.kt` (unloadPlugin-Parameter, reloadPlugin)
- `app/src/main/java/com/ble1st/connectias/ui/plugin/PluginManagementScreen.kt` (optional: Reload-Button)

---

## 5. Abhängigkeiten und Reihenfolge

- **Plugin-Analytics** und **Privacy Dashboard** sind unabhängig voneinander umsetzbar (parallele Arbeiten möglich).
- **Hot-Reload** hängt nur von der Änderung `unloadPlugin(..., deletePluginFile = false)` ab und kann nach oder parallel zu den anderen erfolgen.

Empfohlene Reihenfolge:

1. Hot-Reload (kleiner, abgeschlossener Block: Unload-Parameter + reloadPlugin + optional UI).
2. Plugin-Analytics (Session + Lifecycle + Repository + UI).
3. Privacy Dashboard (Texte, Datenkategorien, Art. 15 Kennzeichnung).

---

## 6. Risiken und Tests

- **Analytics:** Neue Events/Session-Datei: Compaction und Export prüfen; UI in verschiedenen Zeitfenstern testen.
- **Privacy:** Nur UI/Texte; keine Änderung an Export-Inhalt oder Aggregator-Logik (außer ggf. Metadaten für Kategorien-Beschreibung).
- **Hot-Reload:** Bestehende Aufrufer von `unloadPlugin(pluginId)` müssen weiterhin ohne zweites Argument aufrufbar bleiben (Default `deletePluginFile = true`); Integrationstest: Plugin laden, Reload, erneut nutzen. Bei geöffneter Plugin-UI: Reload kann zu Fehler/Neubindung führen – akzeptabel, ggf. Hinweis „Plugin wird neu geladen, Ansicht schließt sich“.

---

## 7. Kurzüberblick


| Thema            | Kernänderung                                                                                              |
| ---------------- | --------------------------------------------------------------------------------------------------------- |
| Plugin-Analytics | Session-Dauer (foreground start/end), Lifecycle-Events (enable/disable/install), Anzeige + Export         |
| Privacy/DSGVO    | Art. 15 Label, Datenkategorien-Übersicht, Export-Button-Beschriftung                                      |
| Hot-Reload       | `unloadPlugin(..., deletePluginFile = false)`, `reloadPlugin(pluginId)`, optional Reload-Button in der UI |


