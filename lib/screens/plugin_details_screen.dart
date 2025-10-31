/// Plugin Details Screen – Vollständige Plugin-Informationen
///
/// Detaillierte Ansicht für einzelne Plugins mit allen Informationen
library;

import 'package:flutter/material.dart';
import 'dart:async';
import 'package:fl_chart/fl_chart.dart';
import '../models/plugin_model.dart';
import '../services/connectias_service.dart';

/// Plugin Details Screen mit vollständigen Informationen
class PluginDetailsScreen extends StatefulWidget {
  final PluginModel plugin;

  const PluginDetailsScreen({
    super.key,
    required this.plugin,
  });

  @override
  State<PluginDetailsScreen> createState() => _PluginDetailsScreenState();
}

class _PluginDetailsScreenState extends State<PluginDetailsScreen>
    with TickerProviderStateMixin {
  final ConnectiasService _service = connectiasService;
  
  late TabController _tabController;
  Timer? _refreshTimer;
  bool _isLoading = false;
  
  // Plugin Details
  Map<String, dynamic> _pluginStats = {};
  List<Map<String, dynamic>> _recentActivity = [];
  List<Map<String, dynamic>> _permissions = [];
  Map<String, dynamic> _performanceMetrics = {};

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
    _loadPluginDetails();
    _startAutoRefresh();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _refreshTimer?.cancel();
    super.dispose();
  }

  void _startAutoRefresh() {
    _refreshTimer = Timer.periodic(const Duration(seconds: 10), (timer) {
      _loadPluginDetails();
    });
  }

  Future<void> _loadPluginDetails() async {
    if (_isLoading) return;

    setState(() => _isLoading = true);

    try {
      // Simuliere Plugin-Details
      await Future.delayed(const Duration(milliseconds: 500));

      setState(() {
        _pluginStats = {
          'executionCount': 42,
          'averageExecutionTime': 150,
          'memoryUsage': 32.5,
          'cpuUsage': 15.2,
          'networkRequests': 8,
          'lastExecution': DateTime.now().subtract(const Duration(minutes: 5)),
          'uptime': '2d 14h 32m',
        };

        _recentActivity = [
          {
            'type': 'execution',
            'message': 'Plugin executed successfully',
            'timestamp': DateTime.now().subtract(const Duration(minutes: 5)),
            'icon': Icons.play_arrow,
            'color': Colors.green,
          },
          {
            'type': 'error',
            'message': 'Network timeout occurred',
            'timestamp': DateTime.now().subtract(const Duration(minutes: 15)),
            'icon': Icons.error,
            'color': Colors.red,
          },
          {
            'type': 'warning',
            'message': 'High memory usage detected',
            'timestamp': DateTime.now().subtract(const Duration(minutes: 30)),
            'icon': Icons.warning,
            'color': Colors.orange,
          },
        ];

        _permissions = [
          {
            'name': 'storage:read',
            'description': 'Read access to storage',
            'granted': true,
            'icon': Icons.storage,
          },
          {
            'name': 'network:https',
            'description': 'HTTPS network access',
            'granted': true,
            'icon': Icons.network_check,
          },
          {
            'name': 'system:admin',
            'description': 'Administrative access',
            'granted': false,
            'icon': Icons.admin_panel_settings,
          },
        ];

        _performanceMetrics = {
          'responseTime': 45.2,
          'throughput': 1200,
          'errorRate': 0.1,
          'availability': 99.9,
        };
      });
    } finally {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.plugin.name),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadPluginDetails,
            tooltip: 'Aktualisieren',
          ),
          PopupMenuButton<String>(
            onSelected: _handleMenuAction,
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: 'toggle',
                child: ListTile(
                  leading: Icon(Icons.power_settings_new),
                  title: Text('Ein/Ausschalten'),
                ),
              ),
              const PopupMenuItem(
                value: 'update',
                child: ListTile(
                  leading: Icon(Icons.update),
                  title: Text('Aktualisieren'),
                ),
              ),
              const PopupMenuItem(
                value: 'uninstall',
                child: ListTile(
                  leading: Icon(Icons.delete),
                  title: Text('Deinstallieren'),
                ),
              ),
            ],
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(icon: Icon(Icons.info), text: 'Info'),
            Tab(icon: Icon(Icons.analytics), text: 'Performance'),
            Tab(icon: Icon(Icons.security), text: 'Sicherheit'),
            Tab(icon: Icon(Icons.timeline), text: 'Aktivität'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildInfoTab(),
          _buildPerformanceTab(),
          _buildSecurityTab(),
          _buildActivityTab(),
        ],
      ),
    );
  }

  Widget _buildInfoTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Plugin Header
          _buildPluginHeader(),
          const SizedBox(height: 24),

          // Basic Information
          _buildInfoSection('Grundinformationen', [
            _buildInfoTile('Version', widget.plugin.version, Icons.info),
            _buildInfoTile('Autor', widget.plugin.author, Icons.person),
            _buildInfoTile('Kategorie', widget.plugin.category, Icons.category),
            _buildInfoTile('Status', _getStatusText(), Icons.circle, _getStatusColor()),
          ]),

          const SizedBox(height: 24),

          // Plugin Description
          _buildInfoSection('Beschreibung', [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  widget.plugin.description,
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
              ),
            ),
          ]),

          const SizedBox(height: 24),

          // Statistics
          _buildInfoSection('Statistiken', [
            _buildStatCard('Ausführungen', '${_pluginStats['executionCount']}', Icons.play_arrow, Colors.blue),
            _buildStatCard('Speicher', '${_pluginStats['memoryUsage']} MB', Icons.memory, Colors.orange),
            _buildStatCard('CPU', '${_pluginStats['cpuUsage']}%', Icons.speed, Colors.green),
            _buildStatCard('Uptime', _pluginStats['uptime'], Icons.schedule, Colors.purple),
          ]),
        ],
      ),
    );
  }

  Widget _buildPerformanceTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Performance Metrics
          _buildInfoSection('Leistungsmetriken', [
            _buildMetricCard('Antwortzeit', '${_performanceMetrics['responseTime']} ms', Icons.timer, Colors.blue),
            _buildMetricCard('Durchsatz', '${_performanceMetrics['throughput']} req/s', Icons.speed, Colors.green),
            _buildMetricCard('Fehlerrate', '${_performanceMetrics['errorRate']}%', Icons.error, Colors.red),
            _buildMetricCard('Verfügbarkeit', '${_performanceMetrics['availability']}%', Icons.check_circle, Colors.purple),
          ]),

          const SizedBox(height: 24),

          // Resource Usage
          _buildInfoSection('Ressourcenverbrauch', [
            _buildResourceChart('CPU', _pluginStats['cpuUsage'] ?? 0.0, Colors.red),
            _buildResourceChart('Speicher', _pluginStats['memoryUsage'] ?? 0.0, Colors.blue),
            _buildResourceChart('Netzwerk', 12.5, Colors.green),
          ]),

          const SizedBox(height: 24),

          // Performance History
          _buildInfoSection('Leistungsverlauf', [
            Container(
              height: 200,
              decoration: BoxDecoration(
                color: Colors.grey[100],
                borderRadius: BorderRadius.circular(8),
              ),
              child: PerformanceChart(pluginId: widget.plugin.id),
            ),
          ]),
        ],
      ),
    );
  }

  Widget _buildSecurityTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Security Status
          _buildInfoSection('Sicherheitsstatus', [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Icon(
                      Icons.security,
                      color: Colors.green,
                      size: 32,
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Sicher',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const Text('Alle Sicherheitsprüfungen bestanden'),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ]),

          const SizedBox(height: 24),

          // Permissions
          _buildInfoSection('Berechtigungen', [
            ..._permissions.map((permission) => _buildPermissionTile(permission)),
          ]),

          const SizedBox(height: 24),

          // Security Actions
          _buildInfoSection('Sicherheitsaktionen', [
            _buildActionCard(
              'Sicherheitsscan',
              'Vollständigen Sicherheitsscan durchführen',
              Icons.security,
              Colors.blue,
              () => _runSecurityScan(),
            ),
            _buildActionCard(
              'Berechtigungen prüfen',
              'Plugin-Berechtigungen überprüfen',
              Icons.verified_user,
              Colors.orange,
              () => _checkPermissions(),
            ),
          ]),
        ],
      ),
    );
  }

  Widget _buildActivityTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Recent Activity
          _buildInfoSection('Letzte Aktivität', [
            if (_recentActivity.isEmpty)
              const Card(
                child: Padding(
                  padding: EdgeInsets.all(16),
                  child: Text('Keine Aktivität'),
                ),
              )
            else
              ..._recentActivity.map((activity) => _buildActivityTile(activity)),
          ]),

          const SizedBox(height: 24),

          // Activity Filters
          _buildInfoSection('Filter', [
            Row(
              children: [
                Expanded(
                  child: FilterChip(
                    label: const Text('Alle'),
                    selected: true,
                    onSelected: (selected) {},
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: FilterChip(
                    label: const Text('Fehler'),
                    selected: false,
                    onSelected: (selected) {},
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: FilterChip(
                    label: const Text('Warnungen'),
                    selected: false,
                    onSelected: (selected) {},
                  ),
                ),
              ],
            ),
          ]),
        ],
      ),
    );
  }

  Widget _buildPluginHeader() {
    return Card(
      elevation: 4,
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          gradient: LinearGradient(
            colors: [
              Theme.of(context).colorScheme.primary,
              Theme.of(context).colorScheme.primary.withOpacity(0.8),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              Icon(
                _getPluginIcon(),
                size: 48,
                color: Colors.white,
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.plugin.name,
                      style: const TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      widget.plugin.version,
                      style: const TextStyle(
                        fontSize: 16,
                        color: Colors.white70,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        _buildStatusChip(),
                        const SizedBox(width: 8),
                        Chip(
                          label: Text(
                            widget.plugin.category,
                            style: const TextStyle(color: Colors.white),
                          ),
                          backgroundColor: Colors.white.withOpacity(0.2),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              if (_isLoading)
                const SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildInfoSection(String title, List<Widget> children) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 12),
        ...children,
      ],
    );
  }

  Widget _buildInfoTile(String label, String value, IconData icon, [Color? color]) {
    return ListTile(
      leading: Icon(icon, color: color),
      title: Text(label),
      trailing: Text(
        value,
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
          color: color ?? Theme.of(context).colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }

  Widget _buildStatCard(String title, String value, IconData icon, Color color) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Icon(icon, color: color, size: 32),
            const SizedBox(height: 8),
            Text(
              title,
              style: const TextStyle(fontWeight: FontWeight.w500),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 4),
            Text(
              value,
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: color,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMetricCard(String title, String value, IconData icon, Color color) {
    return Card(
      color: color.withOpacity(0.1),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          children: [
            Icon(icon, color: color, size: 24),
            const SizedBox(height: 8),
            Text(
              value,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: color,
              ),
            ),
            Text(
              title,
              style: TextStyle(
                fontSize: 12,
                color: color,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResourceChart(String label, double value, Color color) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(label),
                Text('${value.toStringAsFixed(1)}%'),
              ],
            ),
            const SizedBox(height: 8),
            LinearProgressIndicator(
              value: value / 100,
              backgroundColor: Colors.grey[300],
              valueColor: AlwaysStoppedAnimation<Color>(color),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionTile(Map<String, dynamic> permission) {
    return ListTile(
      leading: Icon(
        permission['icon'],
        color: permission['granted'] ? Colors.green : Colors.red,
      ),
      title: Text(permission['name']),
      subtitle: Text(permission['description']),
      trailing: Icon(
        permission['granted'] ? Icons.check_circle : Icons.cancel,
        color: permission['granted'] ? Colors.green : Colors.red,
      ),
    );
  }

  Widget _buildActionCard(
    String title,
    String subtitle,
    IconData icon,
    Color color,
    VoidCallback onTap,
  ) {
    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(icon, size: 32, color: color),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildActivityTile(Map<String, dynamic> activity) {
    return Card(
      child: ListTile(
        leading: Icon(
          activity['icon'],
          color: activity['color'],
        ),
        title: Text(activity['message']),
        subtitle: Text(
          _formatTimestamp(activity['timestamp']),
          style: TextStyle(
            fontSize: 12,
            color: Colors.grey[600],
          ),
        ),
      ),
    );
  }

  Widget _buildStatusChip() {
    Color color;
    String text;
    
    switch (widget.plugin.status) {
      case PluginStatus.active:
        color = Colors.green;
        text = 'Aktiv';
        break;
      case PluginStatus.inactive:
        color = Colors.grey;
        text = 'Inaktiv';
        break;
      case PluginStatus.error:
        color = Colors.red;
        text = 'Fehler';
        break;
      case PluginStatus.loading:
        color = Colors.orange;
        text = 'Lädt';
        break;
    }
    
    return Chip(
      label: Text(
        text,
        style: const TextStyle(color: Colors.white),
      ),
      backgroundColor: color,
    );
  }

  IconData _getPluginIcon() {
    switch (widget.plugin.category.toLowerCase()) {
      case 'storage':
        return Icons.storage;
      case 'network':
        return Icons.network_check;
      case 'security':
        return Icons.security;
      case 'utility':
        return Icons.build;
      default:
        return Icons.extension;
    }
  }

  String _getStatusText() {
    switch (widget.plugin.status) {
      case PluginStatus.active:
        return 'Aktiv';
      case PluginStatus.inactive:
        return 'Inaktiv';
      case PluginStatus.error:
        return 'Fehler';
      case PluginStatus.loading:
        return 'Lädt';
    }
  }

  Color _getStatusColor() {
    switch (widget.plugin.status) {
      case PluginStatus.active:
        return Colors.green;
      case PluginStatus.inactive:
        return Colors.grey;
      case PluginStatus.error:
        return Colors.red;
      case PluginStatus.loading:
        return Colors.orange;
    }
  }

  String _formatTimestamp(DateTime timestamp) {
    final now = DateTime.now();
    final difference = now.difference(timestamp);

    if (difference.inMinutes < 1) {
      return 'Gerade eben';
    } else if (difference.inMinutes < 60) {
      return 'vor ${difference.inMinutes} Minuten';
    } else if (difference.inHours < 24) {
      return 'vor ${difference.inHours} Stunden';
    } else {
      return 'vor ${difference.inDays} Tagen';
    }
  }

  void _handleMenuAction(String action) {
    switch (action) {
      case 'toggle':
        _togglePlugin();
        break;
      case 'update':
        _updatePlugin();
        break;
      case 'uninstall':
        _showUninstallDialog();
        break;
    }
  }

  void _togglePlugin() async {
    try {
      // Echte Plugin-Toggle-Implementierung
      final newStatus = widget.plugin.status == PluginStatus.active 
          ? PluginStatus.inactive 
          : PluginStatus.active;
      
      // Hier würde der echte Service-Call stattfinden
      // await ConnectiasService.instance.togglePlugin(widget.plugin.id, newStatus);
      
      // Erstelle neues PluginModel mit aktualisiertem Status
      final updatedPlugin = PluginModel(
        id: widget.plugin.id,
        name: widget.plugin.name,
        version: widget.plugin.version,
        description: widget.plugin.description,
        author: widget.plugin.author,
        category: widget.plugin.category,
        status: newStatus,
        permissions: widget.plugin.permissions,
        lastUsed: widget.plugin.lastUsed,
        memoryUsage: widget.plugin.memoryUsage,
        isEnabled: newStatus == PluginStatus.active,
        metadata: widget.plugin.metadata,
      );
      
      // Signalisiere Parent über Status-Änderung
      // SnackBar wird im Parent angezeigt, damit sie sichtbar ist
      Navigator.of(context).pop(updatedPlugin);
    } catch (e) {
      // Fehler-SnackBar
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Fehler beim Toggle des Plugins: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  void _updatePlugin() {
    // Simuliere Plugin-Update
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Plugin wird aktualisiert...'),
      ),
    );
  }

  void _showUninstallDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Plugin deinstallieren'),
        content: Text(
          'Möchten Sie das Plugin "${widget.plugin.name}" wirklich deinstallieren?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Abbrechen'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(context).pop();
              _uninstallPlugin();
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.red,
              foregroundColor: Colors.white,
            ),
            child: const Text('Deinstallieren'),
          ),
        ],
      ),
    );
  }

  void _uninstallPlugin() {
    // Simuliere Plugin-Deinstallation
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Plugin wurde deinstalliert'),
        backgroundColor: Colors.green,
      ),
    );
  }

  void _runSecurityScan() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Sicherheitsscan wird durchgeführt...'),
      ),
    );
  }

  void _checkPermissions() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Berechtigungen werden überprüft...'),
      ),
    );
  }
}

/// Performance Chart Widget
class PerformanceChart extends StatefulWidget {
  final String pluginId;
  
  const PerformanceChart({
    super.key,
    required this.pluginId,
  });
  
  @override
  State<PerformanceChart> createState() => _PerformanceChartState();
}

class _PerformanceChartState extends State<PerformanceChart> {
  List<FlSpot> _cpuData = [];
  List<FlSpot> _memoryData = [];
  List<FlSpot> _networkData = [];
  Timer? _updateTimer;
  
  @override
  void initState() {
    super.initState();
    _loadInitialData();
    _startPeriodicUpdates();
  }
  
  @override
  void dispose() {
    _updateTimer?.cancel();
    super.dispose();
  }
  
  void _loadInitialData() {
    // Lade historische Daten (letzte 24 Stunden)
    _generateSampleData();
  }
  
  void _startPeriodicUpdates() {
    _updateTimer = Timer.periodic(const Duration(seconds: 5), (timer) {
      _updateChartData();
    });
  }
  
  void _generateSampleData() {
    // Generiere Sample-Daten für Demo
    final now = DateTime.now();
    _cpuData = List.generate(24, (index) {
      final time = now.subtract(Duration(hours: 23 - index));
      final hour = time.hour;
      final cpu = 20 + (hour * 2) + (index % 3) * 5; // Simuliere CPU-Usage
      return FlSpot(index.toDouble(), cpu.toDouble());
    });
    
    _memoryData = List.generate(24, (index) {
      final time = now.subtract(Duration(hours: 23 - index));
      final hour = time.hour;
      final memory = 30 + (hour * 1.5) + (index % 2) * 3; // Simuliere Memory-Usage
      return FlSpot(index.toDouble(), memory.toDouble());
    });
    
    _networkData = List.generate(24, (index) {
      final time = now.subtract(Duration(hours: 23 - index));
      final hour = time.hour;
      final network = 10 + (hour * 0.8) + (index % 4) * 2; // Simuliere Network-Usage
      return FlSpot(index.toDouble(), network.toDouble());
    });
  }
  
  void _updateChartData() {
    if (!mounted) return;
    
    setState(() {
      // Verschiebe alle Daten um eine Position nach links
      _cpuData.removeAt(0);
      _memoryData.removeAt(0);
      _networkData.removeAt(0);
      // x-Koordinaten der verbleibenden Punkte anpassen (nach links schieben)
      _cpuData = _cpuData.map((p) => FlSpot(p.x - 1, p.y)).toList();
      _memoryData = _memoryData.map((p) => FlSpot(p.x - 1, p.y)).toList();
      _networkData = _networkData.map((p) => FlSpot(p.x - 1, p.y)).toList();
      
      // Füge neue Daten am Ende hinzu
      final now = DateTime.now();
      final hour = now.hour;
      final minute = now.minute;
      
      final newCpu = 20 + (hour * 2) + (minute % 10) * 2;
      final newMemory = 30 + (hour * 1.5) + (minute % 15) * 1.5;
      final newNetwork = 10 + (hour * 0.8) + (minute % 20) * 1.2;
      
      _cpuData.add(FlSpot(23, newCpu.toDouble()));
      _memoryData.add(FlSpot(23, newMemory.toDouble()));
      _networkData.add(FlSpot(23, newNetwork.toDouble()));
    });
  }
  
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Performance Metrics (24h)',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 16),
          Expanded(
            child: LineChart(
              LineChartData(
                gridData: FlGridData(
                  show: true,
                  drawVerticalLine: true,
                  horizontalInterval: 20,
                  verticalInterval: 4,
                  getDrawingHorizontalLine: (value) {
                    return FlLine(
                      color: Colors.grey[300]!,
                      strokeWidth: 1,
                    );
                  },
                  getDrawingVerticalLine: (value) {
                    return FlLine(
                      color: Colors.grey[300]!,
                      strokeWidth: 1,
                    );
                  },
                ),
                titlesData: FlTitlesData(
                  leftTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 40,
                      interval: 20,
                      getTitlesWidget: (value, meta) {
                        return Text(
                          '${value.toInt()}%',
                          style: const TextStyle(fontSize: 10),
                        );
                      },
                    ),
                  ),
                  bottomTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 30,
                      interval: 6,
                      getTitlesWidget: (value, meta) {
                        final hour = DateTime.now().subtract(
                          Duration(hours: 23 - value.toInt()),
                        ).hour;
                        return Text(
                          '${hour.toString().padLeft(2, '0')}:00',
                          style: const TextStyle(fontSize: 10),
                        );
                      },
                    ),
                  ),
                  rightTitles: const AxisTitles(
                    sideTitles: SideTitles(showTitles: false),
                  ),
                  topTitles: const AxisTitles(
                    sideTitles: SideTitles(showTitles: false),
                  ),
                ),
                borderData: FlBorderData(
                  show: true,
                  border: Border.all(color: Colors.grey[400]!),
                ),
                minX: 0,
                maxX: 23,
                minY: 0,
                maxY: 100,
                lineBarsData: [
                  LineChartBarData(
                    spots: _cpuData,
                    isCurved: true,
                    color: Colors.red,
                    barWidth: 2,
                    isStrokeCapRound: true,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                      show: true,
                      color: Colors.red.withOpacity(0.1),
                    ),
                  ),
                  LineChartBarData(
                    spots: _memoryData,
                    isCurved: true,
                    color: Colors.blue,
                    barWidth: 2,
                    isStrokeCapRound: true,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                      show: true,
                      color: Colors.blue.withOpacity(0.1),
                    ),
                  ),
                  LineChartBarData(
                    spots: _networkData,
                    isCurved: true,
                    color: Colors.green,
                    barWidth: 2,
                    isStrokeCapRound: true,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                      show: true,
                      color: Colors.green.withOpacity(0.1),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _buildLegendItem('CPU', Colors.red),
              _buildLegendItem('Memory', Colors.blue),
              _buildLegendItem('Network', Colors.green),
            ],
          ),
        ],
      ),
    );
  }
  
  Widget _buildLegendItem(String label, Color color) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 12,
          height: 12,
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
          ),
        ),
        const SizedBox(width: 4),
        Text(
          label,
          style: const TextStyle(fontSize: 12),
        ),
      ],
    );
  }
}