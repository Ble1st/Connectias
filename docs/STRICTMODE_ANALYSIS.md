# StrictMode Violations - Analyse & Identifikation

**Datum:** 2026-01-12  
**Phase:** 1 - Analyse & Identifikation  
**Status:** ✅ Abgeschlossen

---

## 📊 Zusammenfassung

**Gefundene Violations:**
- **SharedPreferences-Zugriffe:** 1 kritische Stelle
- **File-Operationen:** ✅ Bereits asynchron (RootDetector, EmulatorDetector)
- **Network-Operationen:** ✅ Bereits asynchron (Plugin-Downloads)

**Priorität:** P1 (Hoch) - SettingsViewModel verwendet synchrone Zugriffe

---

## 🔍 Detaillierte Analyse

### 1. SharedPreferences-Zugriffe (Kritisch)

#### ❌ **SettingsViewModel.kt** - `loadSettings()`

**Datei:** `feature-settings/src/main/java/com/ble1st/connectias/feature/settings/ui/SettingsViewModel.kt`  
**Zeilen:** 35-54

**Problem:**
```kotlin
private fun loadSettings() {
    viewModelScope.launch {
        try {
            _uiState.update { currentState ->
                currentState.copy(
                    theme = settingsRepository.getTheme(),              // ❌ Synchron
                    themeStyle = settingsRepository.getThemeStyle(),    // ❌ Synchron
                    dynamicColor = settingsRepository.getDynamicColor(), // ❌ Synchron
                    autoLockEnabled = settingsRepository.getAutoLockEnabled(), // ❌ Synchron
                    raspLoggingEnabled = settingsRepository.getRaspLoggingEnabled(), // ❌ Synchron
                    loggingLevel = settingsRepository.getLoggingLevel(), // ❌ Synchron
                    clipboardAutoClear = settingsRepository.getClipboardAutoClear() // ❌ Synchron
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load settings")
        }
    }
}
```

**Warum problematisch:**
- `viewModelScope.launch` startet standardmäßig auf `Dispatchers.Main`
- Die synchronen `get*()`-Methoden greifen direkt auf SharedPreferences zu
- Dies kann StrictMode-Violations verursachen, wenn auf dem Main Thread ausgeführt

**Lösung:**
- Umstellen auf Flow-basierte `observe*()`-Methoden
- Oder explizit `Dispatchers.IO` verwenden

**Betroffene Methoden:**
- `getTheme()` → `observeTheme()`
- `getThemeStyle()` → `observeThemeStyle()`
- `getDynamicColor()` → `observeDynamicColor()`
- `getAutoLockEnabled()` → `observeAutoLockEnabled()` (neu erstellen)
- `getRaspLoggingEnabled()` → `observeRaspLoggingEnabled()` (neu erstellen)
- `getLoggingLevel()` → `observeLoggingLevel()` (bereits vorhanden)
- `getClipboardAutoClear()` → `observeClipboardAutoClear()` (neu erstellen)

---

### 2. File-Operationen (✅ Bereits asynchron)

#### ✅ **RootDetector.kt**
- **Status:** ✅ Bereits asynchron
- **Zeilen:** 45-116
- **Implementierung:** `suspend fun detectRoot() = withContext(Dispatchers.IO)`
- **File-Operationen:** Alle in `withContext(Dispatchers.IO)` Block

#### ✅ **EmulatorDetector.kt**
- **Status:** ✅ Bereits asynchron
- **Zeilen:** 37-107
- **Implementierung:** `suspend fun detectEmulator() = withContext(Dispatchers.IO)`
- **File-Operationen:** Alle in `withContext(Dispatchers.IO)` Block

#### ✅ **PluginImportHandler.kt**
- **Status:** ✅ Bereits asynchron
- **Implementierung:** `suspend fun importPlugin() = withContext(Dispatchers.IO)`

#### ✅ **PluginManager.kt**
- **Status:** ✅ Bereits asynchron
- **Implementierung:** `suspend fun loadPlugin() = withContext(Dispatchers.IO)`

---

### 3. Network-Operationen (✅ Bereits asynchron)

#### ✅ **GitHubPluginDownloadManager.kt**
- **Status:** ✅ Bereits asynchron
- **Implementierung:** Läuft auf `Dispatchers.IO`

---

## 📋 Kategorisierung nach Violation-Typ

### Kategorie 1: SharedPreferences Disk I/O (P1 - Hoch)

| Datei | Methode | Zeilen | Status | Priorität |
|-------|---------|--------|--------|-----------|
| `SettingsViewModel.kt` | `loadSettings()` | 35-54 | ❌ Synchron | P1 |

**Betroffene Zugriffe:**
- `getTheme()` - Plain SharedPreferences
- `getThemeStyle()` - Plain SharedPreferences
- `getDynamicColor()` - Plain SharedPreferences
- `getAutoLockEnabled()` - EncryptedSharedPreferences
- `getRaspLoggingEnabled()` - EncryptedSharedPreferences
- `getLoggingLevel()` - EncryptedSharedPreferences
- `getClipboardAutoClear()` - EncryptedSharedPreferences

### Kategorie 2: File I/O (✅ Keine Violations)

| Datei | Status | Bemerkung |
|-------|--------|-----------|
| `RootDetector.kt` | ✅ Asynchron | `withContext(Dispatchers.IO)` |
| `EmulatorDetector.kt` | ✅ Asynchron | `withContext(Dispatchers.IO)` |
| `PluginImportHandler.kt` | ✅ Asynchron | `withContext(Dispatchers.IO)` |
| `PluginManager.kt` | ✅ Asynchron | `withContext(Dispatchers.IO)` |

### Kategorie 3: Network I/O (✅ Keine Violations)

| Datei | Status | Bemerkung |
|-------|--------|-----------|
| `GitHubPluginDownloadManager.kt` | ✅ Asynchron | Läuft auf `Dispatchers.IO` |

---

## 🎯 Priorisierung nach Häufigkeit

### Priorität P1 (Hoch) - Sofort beheben

1. **SettingsViewModel.loadSettings()**
   - **Häufigkeit:** Wird bei jedem Settings-Screen-Start aufgerufen
   - **Impact:** Kann StrictMode-Violations verursachen
   - **Lösung:** Umstellen auf Flow-basierte `observe*()`-Methoden

### Priorität P2 (Mittel) - Später beheben

Keine weiteren kritischen Violations gefunden.

### Priorität P3 (Niedrig) - Optional

Keine weiteren Violations gefunden.

---

## 📝 Fehlende Flow-Methoden in SettingsRepository

Folgende `observe*()`-Methoden müssen noch erstellt werden:

1. ❌ `observeAutoLockEnabled(): Flow<Boolean>` - **FEHLT**
2. ❌ `observeRaspLoggingEnabled(): Flow<Boolean>` - **FEHLT**
3. ❌ `observeClipboardAutoClear(): Flow<Boolean>` - **FEHLT**
4. ✅ `observeLoggingLevel(): Flow<String>` - **VORHANDEN** (Zeile 241-253)

---

## ✅ Bereits korrekt implementiert

### SettingsRepository - Flow-Methoden (vorhanden)
- ✅ `observeTheme(): Flow<String>` (Zeile 223-235)
- ✅ `observeThemeStyle(): Flow<String>` (Zeile 203-217)
- ✅ `observeDynamicColor(): Flow<Boolean>` (Zeile 279-291)
- ✅ `observeLoggingLevel(): Flow<String>` (Zeile 241-253)

### UI-Komponenten - Flow-basiert (vorhanden)
- ✅ `DashboardFragment.kt` - Verwendet `observeTheme()`, `observeThemeStyle()`, `observeDynamicColor()`
- ✅ `SettingsFragment.kt` - Verwendet `observeTheme()`, `observeThemeStyle()`, `observeDynamicColor()`
- ✅ `MainActivity.kt` - Verwendet `observeTheme()`, `observeThemeStyle()`, `observeDynamicColor()`

---

## 🔧 Nächste Schritte

### Phase 2: SettingsRepository erweitern
1. Erstelle fehlende `observe*()`-Methoden:
   - `observeAutoLockEnabled()`
   - `observeRaspLoggingEnabled()`
   - `observeClipboardAutoClear()`

### Phase 3: SettingsViewModel migrieren
1. Ersetze `loadSettings()` - verwende Flow-basierte Methoden
2. Ersetze alle `get*()`-Aufrufe durch `observe*()`-Flows
3. Verwende `stateIn()` für State-Management

---

## 📊 Statistiken

- **Gesamt gefundene Violations:** 1
- **Kritische Violations:** 1
- **Bereits asynchron:** 4 Dateien (RootDetector, EmulatorDetector, PluginImportHandler, PluginManager)
- **Zu migrieren:** 1 Datei (SettingsViewModel)
- **Fehlende Flow-Methoden:** 3 (observeAutoLockEnabled, observeRaspLoggingEnabled, observeClipboardAutoClear)

---

## ✅ Validierung

- [x] Logs nach StrictMode-Violations durchsucht
- [x] Code nach synchronen SharedPreferences-Zugriffen gescannt
- [x] Code nach synchronen File-Operationen gescannt
- [x] Code nach synchronen Network-Operationen gescannt
- [x] Liste aller betroffenen Dateien erstellt
- [x] Kategorisierung nach Violation-Typ durchgeführt
- [x] Priorisierung nach Häufigkeit durchgeführt

---

**Nächster Schritt:** Phase 2 - SettingsRepository erweitern
