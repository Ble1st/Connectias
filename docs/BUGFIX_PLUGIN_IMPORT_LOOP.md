# Bugfix: Plugin Import Endlosschleife

**Datum:** 2026-01-25
**Status:** ✅ BEHOBEN
**Schweregrad:** P0 (Kritischer Bug)

---

## 🐛 Problem

Nach dem Import eines Plugins trat eine **Endlosschleife** auf, wenn der User nicht alle erforderlichen Permissions gewährte:

```
1. Plugin wird importiert ✅
2. Plugin wird automatisch geladen und aktiviert ❌
3. Aktivierung schlägt fehl (fehlende Permissions)
4. Permission-Dialog erscheint
5. User gewährt nur Custom Permissions (lehnt z.B. CAMERA ab)
6. Code versucht erneut zu aktivieren
7. Schlägt wieder fehl → zurück zu Schritt 3
```

### Log-Symptome

```
Plugin imported successfully: com.ble1st.connectias.test2plugin
Plugin loaded in sandbox: Test2 Plugin (Three-Process UI)

// Endlosschleife:
Plugin requires permissions: [...CAMERA]
User consent granted: [...ohne CAMERA]
Plugin requires permissions: [...CAMERA]  // Wieder!
User consent granted: [...ohne CAMERA]
... (endlos, hunderte Male)
```

## 🔍 Ursachen-Analyse

### Ursache 1: Automatisches Enable nach Import

**Datei:** `PluginManagementScreen.kt:144-163`

```kotlin
// VORHER (falsch):
val loadResult = pluginManager.loadAndEnablePlugin(pluginId)
loadResult.onSuccess { metadata ->
    importMessage = "Plugin imported, loaded and enabled: $pluginId"
    moduleRegistry.registerModule(moduleInfo.copy(isActive = true))
}
```

**Problem:**
- Nach Import wurde automatisch versucht, das Plugin zu aktivieren
- Wenn Permissions fehlten, wurde direkt die Permission-Anfrage gestartet
- Führte zu Endlosschleife, wenn User Permissions ablehnte

### Ursache 2: Aktivierung bei teilweisen Permissions

**Datei:** `PluginManagementScreen.kt:101-111`

```kotlin
// VORHER (falsch):
// Try enabling plugin if at least some permissions were granted
if (grantedPermissions.isNotEmpty() || pendingCustomPermissions.isNotEmpty()) {
    scope.launch {
        val result = pluginManager.enablePlugin(plugin.pluginId)
        // Schlägt fehl, wenn nicht ALLE Permissions gewährt wurden!
    }
}
```

**Problem:**
- Code versuchte zu aktivieren, sobald **mindestens eine** Permission gewährt wurde
- Wenn User z.B. CAMERA ablehnte, aber Custom Permissions gewährte:
  - `grantedPermissions.isNotEmpty()` war true (Custom Permissions)
  - `enablePlugin()` wurde aufgerufen
  - Schlägt fehl wegen fehlender CAMERA
  - Löst erneute Permission-Anfrage aus → Endlosschleife

## ✅ Lösung

### Fix 1: Nur Laden, nicht Aktivieren

**Datei:** `PluginManagementScreen.kt:144-163`

```kotlin
// NACHHER (korrekt):
val loadResult = pluginManager.loadPlugin(pluginId)  // Nur laden!
loadResult.onSuccess { metadata ->
    importMessage = "Plugin imported and loaded: $pluginId\nYou can enable it by toggling the switch."
    moduleRegistry.registerModule(moduleInfo.copy(isActive = false))  // Inaktiv!
}
```

**Vorteile:**
- ✅ Plugin wird importiert und erscheint in der Liste
- ✅ User kann manuell über Toggle-Button aktivieren
- ✅ Keine automatische Permission-Anfrage
- ✅ User hat Kontrolle über den Aktivierungszeitpunkt

### Fix 2: Nur bei ALLEN Permissions aktivieren

**Datei:** `PluginManagementScreen.kt:101-121`

```kotlin
// NACHHER (korrekt):
// Only try enabling plugin if ALL requested permissions were granted
if (deniedPermissions.isEmpty()) {
    // Alle Permissions gewährt - aktivieren
    scope.launch {
        val result = pluginManager.enablePlugin(plugin.pluginId)
        result.onSuccess {
            Timber.i("Plugin enabled after permission grant: ${plugin.pluginId}")
        }.onFailure { error ->
            Timber.e(error, "Failed to enable plugin after permission grant: ${plugin.pluginId}")
        }
    }
} else {
    // Einige Permissions abgelehnt - NICHT aktivieren
    Timber.w("Plugin ${plugin.pluginId} cannot be enabled - user denied permissions: $deniedPermissions")
    // Plugin bleibt in LOADED state
}
```

**Vorteile:**
- ✅ Aktivierung nur wenn **ALLE** Permissions gewährt
- ✅ Keine Endlosschleife bei abgelehnten Permissions
- ✅ Klares Logging für Debugging
- ✅ Plugin bleibt in LOADED state (kann später aktiviert werden)

## 📝 User Experience nach Fix

### Import-Flow (Neu)

```
1. User wählt Plugin-Datei (.apk/.cplug)
2. Plugin wird importiert ✅
3. Plugin wird geladen (NICHT aktiviert) ✅
4. Success-Dialog: "Plugin imported and loaded: XYZ
                    You can enable it by toggling the switch."
5. Plugin erscheint in Liste als "LOADED" (grauer Toggle)
6. User klickt Toggle → Permission-Dialog erscheint
7. User gewährt/lehnt Permissions ab
8. Plugin wird nur aktiviert, wenn ALLE Permissions gewährt wurden
```

### Permission-Dialog-Flow (Neu)

```
Szenario 1: User gewährt ALLE Permissions
→ Plugin wird aktiviert ✅
→ Toggle wird grün ✅
→ Plugin erscheint im FAB-Menu ✅

Szenario 2: User lehnt einige Permissions ab (z.B. CAMERA)
→ Plugin wird NICHT aktiviert ❌
→ Toggle bleibt grau
→ Plugin bleibt in LOADED state
→ User kann später erneut versuchen zu aktivieren (Toggle erneut klicken)
→ KEINE Endlosschleife ✅
```

## 🧪 Testing

### Manueller Test

1. Plugin mit CAMERA-Permission erstellen
2. Plugin importieren
3. Prüfen:
   - ✅ Plugin erscheint in Liste als "LOADED"
   - ✅ Kein automatischer Permission-Dialog
4. Toggle klicken
5. Im Permission-Dialog:
   - Szenario A: Alle Permissions gewähren → Plugin aktiviert
   - Szenario B: CAMERA ablehnen → Plugin bleibt inaktiv, KEINE Schleife

### Regression-Tests

- ✅ Plugin ohne Permissions importieren → funktioniert
- ✅ Plugin mit nur Custom Permissions → funktioniert
- ✅ Plugin mit nur Android Permissions → funktioniert
- ✅ Plugin mit gemischten Permissions → funktioniert

## 🔐 Sicherheits-Implikationen

### Vorher

- ❌ Automatische Permission-Anfragen ohne User-Interaktion
- ❌ Endlosschleife konnte UI blockieren
- ❌ User hatte keine Kontrolle über Aktivierungszeitpunkt

### Nachher

- ✅ Explizite User-Aktion erforderlich (Toggle klicken)
- ✅ User hat volle Kontrolle über Permissions
- ✅ Keine unerwarteten Permission-Dialoge
- ✅ Klare Trennung: Import → Laden → Aktivieren

## 📊 Auswirkungen

### Performance

- ✅ **Reduziert:** Keine wiederholten `enablePlugin()`-Aufrufe
- ✅ **Reduziert:** Weniger IPC-Calls zwischen Prozessen
- ✅ **Reduziert:** Weniger Permission-Manager-Aufrufe

### Benutzererfahrung

- ✅ **Verbessert:** User hat Kontrolle über Aktivierungszeitpunkt
- ✅ **Verbessert:** Keine überraschenden Permission-Dialoge
- ✅ **Verbessert:** Klare Feedback-Messages
- ✅ **Verhindert:** UI-Freeze durch Endlosschleife

### Code-Qualität

- ✅ **Verbessert:** Explizite Logik statt implizites Verhalten
- ✅ **Verbessert:** Besseres Error-Handling
- ✅ **Verbessert:** Klare Kommentare und Logs

## 🚀 Nächste Schritte (Optional)

### Enhancement 1: User Feedback

Wenn User Permissions ablehnt, könnte ein Snackbar erscheinen:

```kotlin
if (deniedPermissions.isNotEmpty()) {
    snackbarHostState.showSnackbar(
        message = "Plugin requires additional permissions: ${deniedPermissions.joinToString()}",
        duration = SnackbarDuration.Long
    )
}
```

### Enhancement 2: Retry-Mechanismus

Button "Retry Permissions" für Plugins, die wegen fehlender Permissions nicht aktiviert wurden:

```kotlin
if (plugin.state == PluginState.LOADED && hasRequiredPermissions) {
    Button("Grant Permissions") {
        // Zeige Permission-Dialog erneut
    }
}
```

---

**Zusammenfassung:**

Die Endlosschleife beim Plugin-Import wurde durch zwei einfache Änderungen behoben:
1. Nach Import nur laden, nicht aktivieren (User macht das manuell)
2. Nur aktivieren, wenn **ALLE** Permissions gewährt wurden (nicht bei teilweisen)

Das Plugin-System ist jetzt robuster und gibt dem User mehr Kontrolle.
