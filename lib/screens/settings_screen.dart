/// Settings Screen – App-Konfiguration
///
/// Zentrale Einstellungen für Connectias App
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/connectias_service.dart';

/// Settings Screen für App-Konfiguration
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final ConnectiasService _service = connectiasService;
  
  // Settings State
  bool _autoStart = true;
  bool _securityMonitoring = true;
  bool _performanceTracking = true;
  bool _debugMode = false;
  bool _darkMode = false;
  String _language = 'de';
  int _maxPlugins = 10;
  int _memoryLimit = 512; // MB
  int _networkTimeout = 30; // seconds
  String _logLevel = 'info';
  
  final List<String> _languages = ['de', 'en', 'fr', 'es'];
  final List<String> _logLevels = ['debug', 'info', 'warn', 'error'];

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    // Simuliere das Laden der Einstellungen
    await Future.delayed(const Duration(milliseconds: 500));
    
    setState(() {
      _autoStart = true;
      _securityMonitoring = true;
      _performanceTracking = true;
      _debugMode = false;
      _darkMode = false;
      _language = 'de';
      _maxPlugins = 10;
      _memoryLimit = 512;
      _networkTimeout = 30;
      _logLevel = 'info';
    });
  }

  Future<void> _saveSettings() async {
    // Simuliere das Speichern der Einstellungen
    await Future.delayed(const Duration(milliseconds: 300));
    
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Einstellungen gespeichert'),
        duration: Duration(seconds: 2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Einstellungen'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          IconButton(
            icon: const Icon(Icons.save),
            onPressed: _saveSettings,
            tooltip: 'Speichern',
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // General Settings
            _buildSectionHeader('Allgemein'),
            _buildSettingsCard([
              _buildSwitchTile(
                'Auto-Start',
                'App automatisch beim Systemstart laden',
                _autoStart,
                Icons.play_arrow,
                (value) => setState(() => _autoStart = value),
              ),
              _buildSwitchTile(
                'Debug-Modus',
                'Erweiterte Debug-Informationen anzeigen',
                _debugMode,
                Icons.bug_report,
                (value) => setState(() => _debugMode = value),
              ),
              _buildDropdownTile(
                'Sprache',
                'App-Sprache auswählen',
                _language,
                _languages,
                Icons.language,
                (value) => setState(() => _language = value!),
              ),
            ]),

            const SizedBox(height: 24),

            // Security Settings
            _buildSectionHeader('Sicherheit'),
            _buildSettingsCard([
              _buildSwitchTile(
                'Security Monitoring',
                'Kontinuierliche Sicherheitsüberwachung',
                _securityMonitoring,
                Icons.security,
                (value) => setState(() => _securityMonitoring = value),
              ),
              _buildDropdownTile(
                'Log-Level',
                'Detaillierungsgrad der Protokollierung',
                _logLevel,
                _logLevels,
                Icons.assignment,
                (value) => setState(() => _logLevel = value!),
              ),
            ]),

            const SizedBox(height: 24),

            // Performance Settings
            _buildSectionHeader('Performance'),
            _buildSettingsCard([
              _buildSwitchTile(
                'Performance Tracking',
                'Leistungsüberwachung aktivieren',
                _performanceTracking,
                Icons.speed,
                (value) => setState(() => _performanceTracking = value),
              ),
              _buildSliderTile(
                'Max. Plugins',
                'Maximale Anzahl gleichzeitiger Plugins',
                _maxPlugins.toDouble(),
                1,
                50,
                Icons.extension,
                (value) => setState(() => _maxPlugins = value.round()),
              ),
              _buildSliderTile(
                'Speicher-Limit (MB)',
                'Maximaler Speicherverbrauch pro Plugin',
                _memoryLimit.toDouble(),
                64,
                2048,
                Icons.memory,
                (value) => setState(() => _memoryLimit = value.round()),
              ),
              _buildSliderTile(
                'Netzwerk-Timeout (s)',
                'Timeout für Netzwerkanfragen',
                _networkTimeout.toDouble(),
                5,
                120,
                Icons.network_check,
                (value) => setState(() => _networkTimeout = value.round()),
              ),
            ]),

            const SizedBox(height: 24),

            // Advanced Settings
            _buildSectionHeader('Erweitert'),
            _buildSettingsCard([
              _buildActionTile(
                'Cache leeren',
                'Alle zwischengespeicherten Daten löschen',
                Icons.cleaning_services,
                () => _showClearCacheDialog(),
              ),
              _buildActionTile(
                'Logs exportieren',
                'Protokolldateien exportieren',
                Icons.download,
                () => _exportLogs(),
              ),
              _buildActionTile(
                'Einstellungen zurücksetzen',
                'Alle Einstellungen auf Standard zurücksetzen',
                Icons.restore,
                () => _showResetDialog(),
              ),
            ]),

            const SizedBox(height: 24),

            // About Section
            _buildSectionHeader('Über'),
            _buildSettingsCard([
              _buildInfoTile(
                'Version',
                '1.0.0',
                Icons.info,
              ),
              _buildInfoTile(
                'Build',
                '2024.01.15',
                Icons.build,
              ),
              _buildInfoTile(
                'Entwickler',
                'Connectias Team',
                Icons.people,
              ),
            ]),

            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleLarge?.copyWith(
          fontWeight: FontWeight.bold,
          color: Theme.of(context).colorScheme.primary,
        ),
      ),
    );
  }

  Widget _buildSettingsCard(List<Widget> children) {
    return Card(
      elevation: 2,
      child: Column(
        children: children,
      ),
    );
  }

  Widget _buildSwitchTile(
    String title,
    String subtitle,
    bool value,
    IconData icon,
    ValueChanged<bool> onChanged,
  ) {
    return SwitchListTile(
      title: Text(title),
      subtitle: Text(subtitle),
      value: value,
      onChanged: onChanged,
      secondary: Icon(icon),
      activeColor: Theme.of(context).colorScheme.primary,
    );
  }

  Widget _buildDropdownTile(
    String title,
    String subtitle,
    String value,
    List<String> options,
    IconData icon,
    ValueChanged<String?> onChanged,
  ) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Text(subtitle),
      trailing: DropdownButton<String>(
        value: value,
        onChanged: onChanged,
        items: options.map((String option) {
          return DropdownMenuItem<String>(
            value: option,
            child: Text(option.toUpperCase()),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildSliderTile(
    String title,
    String subtitle,
    double value,
    double min,
    double max,
    IconData icon,
    ValueChanged<double> onChanged,
  ) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(subtitle),
          const SizedBox(height: 8),
          Row(
            children: [
              Text('${value.round()}'),
              Expanded(
                child: Slider(
                  value: value,
                  min: min,
                  max: max,
                  divisions: (max - min).round(),
                  onChanged: onChanged,
                  activeColor: Theme.of(context).colorScheme.primary,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildActionTile(
    String title,
    String subtitle,
    IconData icon,
    VoidCallback onTap,
  ) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Text(subtitle),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }

  Widget _buildInfoTile(
    String title,
    String value,
    IconData icon,
  ) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      trailing: Text(
        value,
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
          color: Theme.of(context).colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }

  void _showClearCacheDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Cache leeren'),
        content: const Text(
          'Möchten Sie wirklich alle zwischengespeicherten Daten löschen? '
          'Dies kann die App-Performance vorübergehend beeinträchtigen.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Abbrechen'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(context).pop();
              _clearCache();
            },
            child: const Text('Löschen'),
          ),
        ],
      ),
    );
  }

  void _showResetDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Einstellungen zurücksetzen'),
        content: const Text(
          'Möchten Sie wirklich alle Einstellungen auf die Standardwerte zurücksetzen? '
          'Diese Aktion kann nicht rückgängig gemacht werden.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Abbrechen'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(context).pop();
              _resetSettings();
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.red,
              foregroundColor: Colors.white,
            ),
            child: const Text('Zurücksetzen'),
          ),
        ],
      ),
    );
  }

  Future<void> _clearCache() async {
    // Simuliere Cache-Löschung
    await Future.delayed(const Duration(seconds: 1));
    
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Cache erfolgreich geleert'),
          backgroundColor: Colors.green,
        ),
      );
    }
  }

  Future<void> _exportLogs() async {
    // Simuliere Log-Export
    await Future.delayed(const Duration(seconds: 2));
    
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Logs erfolgreich exportiert'),
          backgroundColor: Colors.green,
        ),
      );
    }
  }

  Future<void> _resetSettings() async {
    // Simuliere Einstellungs-Reset
    await Future.delayed(const Duration(seconds: 1));
    
    setState(() {
      _autoStart = true;
      _securityMonitoring = true;
      _performanceTracking = true;
      _debugMode = false;
      _darkMode = false;
      _language = 'de';
      _maxPlugins = 10;
      _memoryLimit = 512;
      _networkTimeout = 30;
      _logLevel = 'info';
    });
    
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Einstellungen erfolgreich zurückgesetzt'),
          backgroundColor: Colors.green,
        ),
      );
    }
  }
}
