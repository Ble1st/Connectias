# Connectias User Guide

## Übersicht

Connectias ist eine umfassende FOSS Android-App für Netzwerk-Analyse, Sicherheit, Privacy und System-Utilities. Diese Anleitung hilft Ihnen, alle Features der App zu nutzen.

## Erste Schritte

### App starten

1. Öffnen Sie die Connectias App
2. Die App führt automatisch Sicherheitsprüfungen durch
3. Nach erfolgreicher Prüfung wird das Security Dashboard angezeigt

### Navigation

- **Bottom Navigation**: Wechseln Sie zwischen den Hauptbereichen
- **Dashboard Cards**: Tippen Sie auf Cards, um zu detaillierten Ansichten zu gelangen
- **Back Button**: Zurück zur vorherigen Ansicht

## Security Features

### Security Dashboard

Das Security Dashboard zeigt den aktuellen Sicherheitsstatus Ihrer App:
- RASP Protection Status
- Erkannte Bedrohungen
- Sicherheitsempfehlungen

### Certificate Analyzer

Analysieren Sie SSL/TLS-Zertifikate von Websites:

1. Öffnen Sie das Security Dashboard
2. Tippen Sie auf "Certificate Analyzer"
3. Geben Sie eine HTTPS-URL ein (z.B. `https://example.com`)
4. Tippen Sie auf "Analyze Certificate"
5. Die App zeigt Zertifikatsdetails, Ablaufdatum und Self-Signed Status

### Password Strength Checker

Überprüfen Sie die Stärke Ihrer Passwörter:

1. Öffnen Sie "Password Strength" im Security Dashboard
2. Geben Sie ein Passwort ein
3. Die App zeigt:
   - Stärke-Score (0-10)
   - Stärke-Level (Very Weak bis Very Strong)
   - Entropie in Bits
   - Detailliertes Feedback

**Passwort generieren:**
1. Geben Sie die gewünschte Länge ein (Standard: 16)
2. Aktivieren Sie "Include special characters" (optional)
3. Tippen Sie auf "Generate Password"
4. Das generierte Passwort wird automatisch analysiert

### Encryption Tools

Verschlüsseln und entschlüsseln Sie Text:

1. Öffnen Sie "Encryption Tools"
2. Wählen Sie Operation: Encrypt, Decrypt oder Generate Key
3. **Encrypt:**
   - Geben Sie Text und Passwort ein
   - Tippen Sie auf "Execute"
   - Kopieren Sie verschlüsselte Daten und IV
4. **Decrypt:**
   - Geben Sie verschlüsselte Daten, IV und Passwort ein
   - Tippen Sie auf "Execute"

### Firewall Analyzer

Analysieren Sie App-Netzwerkberechtigungen:

1. Öffnen Sie "Firewall Analyzer"
2. Tippen Sie auf "Analyze App Network Permissions"
3. Die App zeigt:
   - Alle Apps mit Netzwerkberechtigungen
   - Risiko-Bewertung
   - Gründe für Risiko-Klassifizierung

## Network Features

### Network Dashboard

Übersicht über Netzwerk-Status und verfügbare Tools.

### Port Scanner

Scannen Sie Ports auf Netzwerkgeräten:

1. Öffnen Sie "Port Scanner" im Network Dashboard
2. Geben Sie eine IP-Adresse oder Hostname ein
3. Wählen Sie Ports (Common Ports oder Custom)
4. Tippen Sie auf "Start Scan"
5. Ergebnisse zeigen offene/geschlossene Ports und erkannte Services

### DNS Lookup

Führen Sie DNS-Abfragen durch:

1. Öffnen Sie "DNS Lookup"
2. Geben Sie einen Domain-Namen ein
3. Wählen Sie Record-Typ (A, AAAA, MX, TXT, CNAME)
4. Optional: Wählen Sie einen Custom DNS Server
5. Tippen Sie auf "Lookup"
6. Ergebnisse zeigen DNS-Records und Response-Zeit

### Network Monitor

Überwachen Sie Netzwerk-Traffic:

1. Öffnen Sie "Network Monitor"
2. Die App zeigt:
   - Connection Type
   - Received Bytes (Rx)
   - Sent Bytes (Tx)
   - Current Rates (Rx/Tx pro Sekunde)
   - Total Bytes
3. Tippen Sie auf "Start Monitoring" für kontinuierliche Überwachung

### WiFi Analyzer

Analysieren Sie WiFi-Kanäle:

1. Öffnen Sie "WiFi Analyzer"
2. Tippen Sie auf "Analyze WiFi Channels"
3. Die App zeigt:
   - Verfügbare Kanäle mit Signal-Stärke
   - Kanal-Überlappungen
   - Empfohlener Kanal

**Hinweis:** Location-Berechtigung erforderlich für WiFi-Scanning.

## Privacy Features

### Privacy Dashboard

Übersicht über Privacy-Status in verschiedenen Kategorien.

### Tracker Detection

Erkennen Sie Tracker in installierten Apps:

1. Öffnen Sie "Tracker Detection"
2. Tippen Sie auf "Scan for Trackers"
3. Die App zeigt Apps mit erkannten Tracker-Domains
4. Tracker werden nach Kategorie kategorisiert (Analytics, Advertising, etc.)

### Permissions Analyzer

Analysieren Sie App-Berechtigungen:

1. Öffnen Sie "Permissions Analyzer"
2. Tippen Sie auf "Analyze Permissions"
3. Die App zeigt:
   - Apps mit riskanten Berechtigungen
   - Berechtigungsdetails
   - Risiko-Bewertung
4. Tippen Sie auf eine App für Empfehlungen

### Data Leakage Scanner

Überwachen Sie Daten-Leaks:

1. Öffnen Sie "Data Leakage Scanner"
2. **Clipboard Monitoring:**
   - Tippen Sie auf "Start Monitoring"
   - Die App überwacht Clipboard auf sensible Daten
   - Erkannte sensible Daten werden angezeigt
3. **Text Sensitivity Analysis:**
   - Geben Sie Text ein
   - Tippen Sie auf "Analyze Sensitivity"
   - Die App zeigt Sensitivity-Level (None bis Critical)
4. **Apps mit Clipboard Access:**
   - Tippen Sie auf "Get Apps"
   - Liste zeigt Apps mit Clipboard-Zugriff

## Device Info Features

### Device Info

Zeigt grundlegende System- und Geräteinformationen.

### Battery Analyzer

Analysieren Sie Batterie-Status:

1. Öffnen Sie "Battery Analyzer"
2. Die App zeigt:
   - Batterie-Prozent
   - Lade-Status
   - Gesundheit
   - Spannung, Temperatur
   - Kapazität und Strom
3. **Monitoring:**
   - Tippen Sie auf "Start Monitoring" für kontinuierliche Überwachung
4. **Zeit-Schätzung:**
   - Tippen Sie auf "Estimate Time" für geschätzte Zeit bis voll/leer

### Storage Analyzer

Analysieren Sie Speicher:

1. Öffnen Sie "Storage Analyzer"
2. Die App zeigt:
   - Interner Speicher (Total, Used, Free)
   - Externer Speicher (falls verfügbar)
3. **Große Dateien finden:**
   - Geben Sie minimale Größe in MB ein (Standard: 10 MB)
   - Tippen Sie auf "Find Large Files"
   - Liste zeigt große Dateien mit Pfad und Größe

### Process Monitor

Überwachen Sie laufende Prozesse:

1. Öffnen Sie "Process Monitor"
2. Die App zeigt:
   - Speicher-Statistiken (Total, Used, Available)
   - Liste laufender Prozesse
   - Speicherverbrauch pro Prozess
   - Sortiert nach Speicherverbrauch

### Sensor Monitor

Überwachen Sie Sensoren:

1. Öffnen Sie "Sensor Monitor"
2. Die App zeigt verfügbare Sensoren
3. Tippen Sie auf einen Sensor für Real-time Monitoring
4. Sensor-Daten werden kontinuierlich angezeigt

## Utilities Features

### Utilities Dashboard

Übersicht über alle verfügbaren Utility-Tools.

### Hash & Checksum Tools

Generieren Sie Hashes:

1. Öffnen Sie "Hash & Checksum"
2. Wählen Sie Algorithmus (MD5, SHA-1, SHA-256, SHA-512)
3. Geben Sie Text ein oder wählen Sie Datei
4. Tippen Sie auf "Generate Hash"
5. Hash wird angezeigt und kann kopiert werden

### Encoding/Decoding Tools

Kodieren und dekodieren Sie Text:

1. Öffnen Sie "Encoding/Decoding"
2. Wählen Sie Encoding-Typ (Base64, URL, Hex)
3. Geben Sie Text ein
4. Tippen Sie auf "Encode" oder "Decode"
5. Ergebnis wird angezeigt

### QR Code Tools

Generieren und scannen Sie QR Codes:

1. Öffnen Sie "QR Code"
2. **Generieren:**
   - Geben Sie Text ein
   - Tippen Sie auf "Generate QR Code"
   - QR Code wird angezeigt
3. **Scannen:**
   - Tippen Sie auf "Scan QR Code"
   - Kamera öffnet sich
   - Scannen Sie QR Code

### Text Tools

Text-Utilities:

1. Öffnen Sie "Text Tools"
2. **Case Converter:**
   - Wählen Sie Case-Typ (UPPER, lower, Title, Sentence)
   - Text wird konvertiert
3. **Word/Character Counter:**
   - Text wird automatisch gezählt
4. **JSON Formatter:**
   - Geben Sie JSON ein
   - Tippen Sie auf "Format JSON"
   - Formatiertes JSON wird angezeigt

### API Tester

Testen Sie REST APIs:

1. Öffnen Sie "API Tester"
2. Wählen Sie HTTP-Methode (GET, POST, PUT, DELETE, PATCH)
3. Geben Sie URL ein
4. Optional: Headers und Body hinzufügen
5. Tippen Sie auf "Send Request"
6. Response wird angezeigt (JSON, XML, Plain Text)

### Log Viewer

Zeigen Sie Logs an:

1. Öffnen Sie "Log Viewer"
2. Die App zeigt System- und App-Logs
3. Verwenden Sie Filter für spezifische Log-Level
4. Suchen Sie nach Text in Logs

### Color Tools

Konvertieren Sie Farben:

1. Öffnen Sie "Color Tools"
2. Geben Sie Farbe ein (HEX, RGB, HSL, HSV)
3. Wählen Sie Ziel-Format
4. Tippen Sie auf "Convert"
5. Konvertierte Farbe wird angezeigt
6. **Contrast Checker:**
   - Geben Sie zwei Farben ein
   - App zeigt Kontrast-Ratio für Accessibility

## Backup Features

### Backup Dashboard

Übersicht über Backup- und Restore-Funktionen.

### Export Data

Exportieren Sie App-Daten:

1. Öffnen Sie "Export Data"
2. Wählen Sie Format (JSON, CSV, PDF)
3. Tippen Sie auf "Export Data"
4. Datei wird im Downloads-Ordner gespeichert
5. Liste zeigt alle exportierten Dateien

### Import Data

Importieren Sie Backup-Daten:

1. Öffnen Sie "Import Data"
2. Tippen Sie auf "Select Backup File"
3. Wählen Sie Backup-Datei
4. Die App validiert und importiert Daten
5. Import-Ergebnis wird angezeigt

## Tipps & Best Practices

### Sicherheit

- Verwenden Sie starke Passwörter (Score ≥ 7)
- Überprüfen Sie regelmäßig App-Berechtigungen
- Analysieren Sie Zertifikate vor dem Besuch von Websites
- Überwachen Sie Clipboard auf sensible Daten

### Privacy

- Führen Sie regelmäßig Tracker-Scans durch
- Überprüfen Sie App-Berechtigungen
- Nutzen Sie Data Leakage Scanner für Clipboard-Monitoring

### Performance

- Deaktivieren Sie nicht benötigte Module in `gradle.properties`
- Verwenden Sie Network Monitor sparsam (batterieintensiv)
- Speicher-Analyse kann bei großen Verzeichnissen Zeit benötigen

## Häufige Probleme

### Location-Berechtigung für WiFi-Scanning

**Problem:** WiFi-Scanning funktioniert nicht.

**Lösung:** Gewähren Sie Location-Berechtigung in den App-Einstellungen.

### Keine Netzwerk-Daten

**Problem:** Network Monitor zeigt keine Daten.

**Lösung:** Stellen Sie sicher, dass Sie mit einem Netzwerk verbunden sind.

### Export funktioniert nicht

**Problem:** Export schlägt fehl.

**Lösung:** Überprüfen Sie Storage-Berechtigungen (Android 10 und niedriger).

## Support

Für Fragen und Probleme:
- GitHub Issues: [Repository URL]
- Dokumentation: Siehe `docs/` Verzeichnis

## Changelog

Siehe [README.md](../README.md) für Versionshistorie.

