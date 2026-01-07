# 📱 Plugin-System: Fehlende App-Komponenten

**Datum:** 2026-01-07  
**Status:** UI & Benachrichtigungen implementiert

---

## ✅ Neu implementierte App-Komponenten

### 1. **PluginNotificationManager** ✅
**Datei:** `app/src/main/java/com/ble1st/connectias/ui/PluginNotificationManager.kt`

**Funktionen:**
- ✅ **3 Notification Channels:**
  - `plugin_updates` - Plugin-Updates (IMPORTANCE_DEFAULT)
  - `plugin_errors` - Plugin-Fehler (IMPORTANCE_HIGH)
  - `plugin_permissions` - Berechtigungsanfragen (IMPORTANCE_HIGH)

- ✅ **Benachrichtigungstypen:**
  - `notifyPluginUpdateAvailable()` - Update verfügbar
  - `notifyPluginError()` - Fehler beim Laden/Aktivieren
  - `notifyPluginPermissionRequired()` - Berechtigungen erforderlich
  - `notifyPluginLoaded()` - Plugin geladen (Low Priority, 3s Timeout)
  - `notifyPluginEnabled()` - Plugin aktiviert (Low Priority, 3s Timeout)
  - `cancelAllPluginNotifications()` - Alle löschen

- ✅ **Integration:**
  - In `PluginService` integriert (optional dependency)
  - In `PluginModule` als Singleton bereitgestellt
  - PendingIntent zu MainActivity mit `navigate_to=plugin_management`

---

### 2. **PluginPermissionDialog** ✅
**Datei:** `app/src/main/java/com/ble1st/connectias/ui/PluginPermissionDialog.kt`

**Funktionen:**
- ✅ **Compose AlertDialog** für Permission-Consent
- ✅ **Gefährliche Permissions** - Liste mit Beschreibungen
- ✅ **Kritische Permissions** - Rot markiert, nicht erlaubt
- ✅ **Permission-Formatierung:**
  - Lesbare Namen (z.B. "Read external storage")
  - Deutsche Beschreibungen (z.B. "Zugriff auf Dateien und Medien")
- ✅ **Aktionen:**
  - "Erlauben" - Gewährt Berechtigungen
  - "Ablehnen" - Verweigert Berechtigungen
  - Automatisch disabled bei kritischen Permissions

---

### 3. **PluginManagementScreen** ✅
**Datei:** `app/src/main/java/com/ble1st/connectias/ui/PluginManagementScreen.kt`

**Funktionen:**
- ✅ **Plugin-Liste** mit LazyColumn
- ✅ **Plugin-Cards** mit:
  - Name, Version, Autor
  - Beschreibung
  - Enable/Disable Switch
  - Status-Anzeige (LOADED/ENABLED/DISABLED/ERROR)
  - Details ausklappbar
  - Berechtigungen anzeigen
  - Deinstallieren-Button

- ✅ **TopAppBar** mit:
  - Zurück-Navigation
  - Aktualisieren-Button

- ✅ **Empty State:**
  - Icon + Text wenn keine Plugins installiert

- ✅ **Plugin-Details:**
  - Plugin-ID
  - Kategorie
  - Status
  - Abhängigkeiten
  - Native Bibliotheken
  - Berechtigungen

---

## 🔔 Benachrichtigungs-Integration

### In PluginService integriert:
```kotlin
// Plugin geladen
notificationManager?.notifyPluginLoaded(pluginInfo.metadata.pluginName)

// Plugin aktiviert
notificationManager?.notifyPluginEnabled(plugin.metadata.pluginName)

// Fehler beim Laden
notificationManager?.notifyPluginError(pluginFile.name, error.message)

// Berechtigungen erforderlich
notificationManager?.notifyPluginPermissionRequired(
    plugin.metadata.pluginName,
    permValidation.dangerousPermissions
)

// Update verfügbar
notificationManager?.notifyPluginUpdateAvailable(
    pluginInfo.metadata.pluginName,
    pluginInfo.metadata.version,
    update.version
)
```

---

## 📋 Noch fehlende Komponenten

### 1. **Plugin-Import-UI** ⏳
**Was fehlt:**
- Button zum Öffnen des File-Pickers (SAF)
- Import-Progress-Dialog
- Import-Erfolg/Fehler-Feedback

**Wo implementieren:**
- In `PluginManagementScreen` als FloatingActionButton
- Oder als separater "Import"-Tab

**Code-Beispiel:**
```kotlin
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()
) { uri ->
    uri?.let {
        scope.launch {
            pluginService.importPluginFromUri(it)
        }
    }
}

FloatingActionButton(onClick = { launcher.launch("application/*") }) {
    Icon(Icons.Default.Add, "Plugin importieren")
}
```

---

### 2. **Navigation zu PluginManagementScreen** ⏳
**Was fehlt:**
- Navigation-Destination in `nav_graph.xml` oder Compose Navigation
- Menü-Eintrag in Settings oder Hauptmenü

**Wo implementieren:**
- In `MainActivity` oder Settings-Fragment
- Als Navigation-Item im Drawer/Bottom-Sheet

**Code-Beispiel:**
```kotlin
// In FabWithBottomSheet oder Settings
navController.navigate("plugin_management")
```

---

### 3. **Permission-Consent-Flow** ⏳
**Was fehlt:**
- Automatisches Anzeigen des `PluginPermissionDialog` vor Enable
- State-Management für Dialog-Anzeige
- Callback nach Grant/Deny

**Wo implementieren:**
- In `PluginManagementScreen` beim Toggle
- Oder in separatem ViewModel

**Code-Beispiel:**
```kotlin
var showPermissionDialog by remember { mutableStateOf<PluginInfo?>(null) }

// Beim Toggle
if (enabled && plugin.metadata.permissions.isNotEmpty()) {
    showPermissionDialog = plugin
} else {
    pluginService.enablePlugin(plugin.pluginId)
}

// Dialog anzeigen
showPermissionDialog?.let { plugin ->
    PluginPermissionDialog(
        pluginMetadata = plugin.metadata,
        dangerousPermissions = /* ... */,
        onGrantPermissions = {
            pluginService.grantPermissionConsent(plugin.pluginId, permissions)
            pluginService.enablePlugin(plugin.pluginId)
            showPermissionDialog = null
        },
        onDeny = { showPermissionDialog = null }
    )
}
```

---

### 4. **Plugin-Update-UI** ⏳
**Was fehlt:**
- Update-Badge auf Plugin-Cards
- "Update"-Button
- Update-Progress-Dialog
- Auto-Update-Einstellung

**Wo implementieren:**
- In `PluginManagementScreen` als Badge/Button
- In Settings als Toggle für Auto-Update

---

### 5. **Plugin-Fehler-Anzeige** ⏳
**Was fehlt:**
- Error-State in Plugin-Cards
- Fehlerdetails anzeigen
- Retry-Button

**Wo implementieren:**
- In `PluginCard` als Error-Banner
- Mit Snackbar für temporäre Fehler

---

### 6. **Plugin-Store/Marketplace** ⏳
**Was fehlt:**
- Liste verfügbarer Plugins (von GitHub Releases)
- Download-Button
- Bewertungen/Beschreibungen
- Kategorien-Filter

**Wo implementieren:**
- Als separater Screen "Plugin-Store"
- Nutzt `GitHubPluginDownloadManager`

---

## 🎯 Prioritäten für nächste Schritte

### **Hoch (Blocker für Nutzung)**
1. ✅ ~~Benachrichtigungen~~ (ERLEDIGT)
2. ✅ ~~Plugin-Management-UI~~ (ERLEDIGT)
3. ⏳ **Plugin-Import-UI** - Ohne Import können keine Plugins installiert werden
4. ⏳ **Navigation zu Plugin-Management** - UI ist nicht erreichbar
5. ⏳ **Permission-Consent-Flow** - Plugins können nicht aktiviert werden

### **Mittel (Wichtig für UX)**
6. ⏳ Plugin-Update-UI
7. ⏳ Plugin-Fehler-Anzeige
8. ⏳ Empty-State-Verbesserungen

### **Niedrig (Nice-to-have)**
9. ⏳ Plugin-Store/Marketplace
10. ⏳ Plugin-Bewertungen
11. ⏳ Plugin-Kategorien-Filter

---

## 📝 Integration-Checkliste

### ✅ Bereits integriert:
- [x] PluginNotificationManager in PluginModule
- [x] PluginNotificationManager in PluginService
- [x] Benachrichtigungen bei Load/Enable/Error/Permission/Update
- [x] PluginPermissionDialog (Compose-Komponente)
- [x] PluginManagementScreen (Compose-Komponente)

### ⏳ Noch zu integrieren:
- [ ] Navigation-Route zu PluginManagementScreen
- [ ] Menü-Eintrag für Plugin-Management
- [ ] Import-Button in PluginManagementScreen
- [ ] Permission-Dialog in Enable-Flow
- [ ] Update-Checks in UI anzeigen
- [ ] Error-Handling in UI

---

## 🚀 Quick-Start für fehlende Integration

### 1. Navigation hinzufügen (MainActivity oder NavGraph):
```kotlin
// In Compose Navigation
composable("plugin_management") {
    PluginManagementScreen(
        pluginService = pluginService,
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### 2. Menü-Eintrag (z.B. in FabWithBottomSheet):
```kotlin
FeatureItem(
    icon = Icons.Default.Extension,
    title = "Plugins",
    onClick = { navController.navigate("plugin_management") }
)
```

### 3. Import-Button (in PluginManagementScreen):
```kotlin
floatingActionButton = {
    FloatingActionButton(onClick = { /* launcher.launch() */ }) {
        Icon(Icons.Default.Add, "Plugin importieren")
    }
}
```

---

## 📊 Zusammenfassung

### Implementiert (3 Komponenten):
1. ✅ **PluginNotificationManager** - Vollständiges Benachrichtigungssystem
2. ✅ **PluginPermissionDialog** - Permission-Consent-UI
3. ✅ **PluginManagementScreen** - Plugin-Verwaltungs-UI

### Fehlt noch (6 Komponenten):
1. ⏳ Plugin-Import-UI (File-Picker)
2. ⏳ Navigation-Integration
3. ⏳ Permission-Consent-Flow
4. ⏳ Plugin-Update-UI
5. ⏳ Plugin-Fehler-Anzeige
6. ⏳ Plugin-Store/Marketplace

### Kritisch für MVP:
- **Import-UI** - Ohne Import keine Plugins
- **Navigation** - UI nicht erreichbar
- **Permission-Flow** - Plugins können nicht aktiviert werden

---

**Erstellt von:** Cascade AI  
**Basis:** PLUGIN_SECURITY_IMPLEMENTATION_PLAN.md  
**Status:** Phase 3 erweitert um UI-Komponenten
