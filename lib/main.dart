import 'package:flutter/material.dart';
import 'dart:async';
import 'package:fl_chart/fl_chart.dart';
import 'screens/plugin_manager_screen.dart';
import 'screens/security_dashboard_screen.dart';
import 'screens/settings_screen.dart';
import 'utils/animations.dart';

void main() {
  runApp(const ConnectiasApp());
}

/// Connectias Haupt-App
/// 
/// Sichere Plugin-Plattform mit RASP-Schutz und Sandbox-Isolation
class ConnectiasApp extends StatelessWidget {
  const ConnectiasApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Connectias',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.blue,
          brightness: Brightness.light,
        ),
        useMaterial3: true,
        appBarTheme: const AppBarTheme(
          centerTitle: true,
          elevation: 2,
        ),
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.blue,
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
        appBarTheme: const AppBarTheme(
          centerTitle: true,
          elevation: 2,
        ),
      ),
      home: const ConnectiasHomePage(),
    );
  }
}

class ConnectiasHomePage extends StatefulWidget {
  const ConnectiasHomePage({super.key});

  @override
  State<ConnectiasHomePage> createState() => _ConnectiasHomePageState();
}

class _ConnectiasHomePageState extends State<ConnectiasHomePage> {
  int _selectedIndex = 0;

  final List<Widget> _screens = [
    const ConnectiasDashboard(),
    const PluginManagerScreen(),
    const SecurityDashboardScreen(),
    const SettingsScreen(),
  ];

  final List<String> _titles = [
    'Connectias',
    'Plugin Manager',
    'Security Dashboard',
    'Einstellungen',
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_selectedIndex]),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: _screens[_selectedIndex],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (index) {
          setState(() {
            _selectedIndex = index;
          });
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.dashboard),
            selectedIcon: Icon(Icons.dashboard),
            label: 'Dashboard',
          ),
          NavigationDestination(
            icon: Icon(Icons.extension),
            selectedIcon: Icon(Icons.extension),
            label: 'Plugins',
          ),
          NavigationDestination(
            icon: Icon(Icons.security),
            selectedIcon: Icon(Icons.security),
            label: 'Security',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings),
            selectedIcon: Icon(Icons.settings),
            label: 'Einstellungen',
          ),
        ],
      ),
    );
  }
}

/// Connectias Dashboard - Erweiterte Hauptübersicht
class ConnectiasDashboard extends StatefulWidget {
  const ConnectiasDashboard({super.key});

  @override
  State<ConnectiasDashboard> createState() => _ConnectiasDashboardState();
}

class _ConnectiasDashboardState extends State<ConnectiasDashboard>
    with TickerProviderStateMixin {
  late TabController _tabController;
  Timer? _refreshTimer;
  bool _isLoading = false;
  
  // Dashboard Data
  int _totalPlugins = 0;
  int _activePlugins = 0;
  String _securityStatus = 'Safe';
  Color _securityColor = Colors.green;
  double _systemPerformance = 0.0;
  List<Map<String, dynamic>> _recentActivity = [];
  Map<String, dynamic> _systemMetrics = {};

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _loadDashboardData();
    _startRealTimeUpdates();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _refreshTimer?.cancel();
    super.dispose();
  }

  void _startRealTimeUpdates() {
    _refreshTimer = Timer.periodic(const Duration(seconds: 5), (timer) {
      _loadDashboardData();
    });
  }

  Future<void> _loadDashboardData() async {
    if (_isLoading) return;
    
    setState(() => _isLoading = true);
    
    try {
      // Simuliere Dashboard-Daten
      await Future.delayed(const Duration(milliseconds: 500));
      
      setState(() {
        _totalPlugins = 5;
        _activePlugins = 3;
        _securityStatus = 'Secure';
        _securityColor = Colors.green;
        _systemPerformance = 0.87;
        _recentActivity = [
          {
            'type': 'plugin_loaded',
            'message': 'Storage Plugin loaded',
            'timestamp': DateTime.now().subtract(const Duration(minutes: 2)),
            'icon': Icons.extension,
            'color': Colors.blue,
          },
          {
            'type': 'security_check',
            'message': 'Security scan completed',
            'timestamp': DateTime.now().subtract(const Duration(minutes: 5)),
            'icon': Icons.security,
            'color': Colors.green,
          },
          {
            'type': 'performance',
            'message': 'Performance optimized',
            'timestamp': DateTime.now().subtract(const Duration(minutes: 10)),
            'icon': Icons.speed,
            'color': Colors.orange,
          },
        ];
        _systemMetrics = {
          'cpu_usage': 23.5,
          'memory_usage': 67.2,
          'network_activity': 12.8,
          'storage_used': 45.6,
        };
      });
    } finally {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Header mit Tab-Navigation
        Container(
          color: Theme.of(context).colorScheme.surface,
          child: TabBar(
            controller: _tabController,
            tabs: const [
              Tab(icon: Icon(Icons.dashboard), text: 'Overview'),
              Tab(icon: Icon(Icons.analytics), text: 'Performance'),
              Tab(icon: Icon(Icons.timeline), text: 'Activity'),
            ],
          ),
        ),
        
        // Tab Content
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: [
              _buildOverviewTab(),
              _buildPerformanceTab(),
              _buildActivityTab(),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildOverviewTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Welcome Card
          _buildWelcomeCard(),
          const SizedBox(height: 24),

          // System Status Grid
          _buildSystemStatusGrid(),
          const SizedBox(height: 24),

          // Quick Actions
          _buildQuickActions(),
          const SizedBox(height: 24),

          // System Health
          _buildSystemHealth(),
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
          _buildPerformanceMetrics(),
          const SizedBox(height: 24),

          // Resource Usage
          _buildResourceUsage(),
          const SizedBox(height: 24),

          // Performance Charts
          _buildPerformanceCharts(),
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
          _buildRecentActivity(),
          const SizedBox(height: 24),

          // System Logs
          _buildSystemLogs(),
        ],
      ),
    );
  }

  Widget _buildWelcomeCard() {
    return Card(
      elevation: 4,
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          gradient: LinearGradient(
            colors: [
              Theme.of(context).colorScheme.primary,
              Theme.of(context).colorScheme.primary.withValues(alpha: 0.8),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    Icons.security,
                    size: 32,
                    color: Colors.white,
                  ),
                  const SizedBox(width: 12),
                  const Text(
                    'Connectias',
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                  const Spacer(),
                  if (_isLoading)
                    const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              const Text(
                'Sichere Plugin-Plattform mit RASP-Schutz',
                style: TextStyle(
                  fontSize: 16,
                  color: Colors.white70,
                ),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  _buildStatusIndicator('System', _securityStatus, _securityColor),
                  const SizedBox(width: 16),
                  _buildStatusIndicator('Performance', '${(_systemPerformance * 100).toInt()}%', Colors.blue),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatusIndicator(String label, String value, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.2),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(
              color: color,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            '$label: $value',
            style: const TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSystemStatusGrid() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'System Status',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 12),
        GridView.count(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          crossAxisCount: 2,
          crossAxisSpacing: 12,
          mainAxisSpacing: 12,
          childAspectRatio: 1.5,
          children: [
            _buildStatCard(
              context,
              'Total Plugins',
              _totalPlugins.toString(),
              Icons.extension,
              Colors.blue,
              subtitle: '$_activePlugins active',
            ),
            _buildStatCard(
              context,
              'Security',
              _securityStatus,
              Icons.shield,
              _securityColor,
              subtitle: 'All checks passed',
            ),
            _buildStatCard(
              context,
              'Performance',
              '${(_systemPerformance * 100).toInt()}%',
              Icons.speed,
              Colors.orange,
              subtitle: 'System optimized',
            ),
            _buildStatCard(
              context,
              'Uptime',
              '2d 14h',
              Icons.schedule,
              Colors.green,
              subtitle: 'Stable operation',
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildQuickActions() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Quick Actions',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _buildActionCard(
                context,
                'Plugin Manager',
                'Manage plugins',
                Icons.extension,
                Colors.blue,
                () {
                  // Navigation wird über BottomNav gehandelt
                },
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _buildActionCard(
                context,
                'Security Check',
                'Run security scan',
                Icons.security,
                Colors.green,
                () {
                  _runSecurityCheck();
                },
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _buildActionCard(
                context,
                'Performance',
                'View metrics',
                Icons.analytics,
                Colors.orange,
                () {
                  _tabController.animateTo(1);
                },
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _buildActionCard(
                context,
                'Activity Log',
                'View recent activity',
                Icons.timeline,
                Colors.purple,
                () {
                  _tabController.animateTo(2);
                },
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildSystemHealth() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'System Health',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            _buildHealthMetric('CPU Usage', _systemMetrics['cpu_usage'] ?? 0.0, Colors.red),
            const SizedBox(height: 8),
            _buildHealthMetric('Memory Usage', _systemMetrics['memory_usage'] ?? 0.0, Colors.blue),
            const SizedBox(height: 8),
            _buildHealthMetric('Network Activity', _systemMetrics['network_activity'] ?? 0.0, Colors.green),
            const SizedBox(height: 8),
            _buildHealthMetric('Storage Used', _systemMetrics['storage_used'] ?? 0.0, Colors.orange),
          ],
        ),
      ),
    );
  }

  Widget _buildHealthMetric(String label, double value, Color color) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(label),
            Text('${value.toStringAsFixed(1)}%'),
          ],
        ),
        const SizedBox(height: 4),
        LinearProgressIndicator(
          value: value / 100,
          backgroundColor: Colors.grey[300],
          valueColor: AlwaysStoppedAnimation<Color>(color),
        ),
      ],
    );
  }

  Widget _buildPerformanceMetrics() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Performance Metrics',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _buildMetricCard(
                    'Response Time',
                    '45ms',
                    Icons.timer,
                    Colors.green,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _buildMetricCard(
                    'Throughput',
                    '1.2k req/s',
                    Icons.speed,
                    Colors.blue,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: _buildMetricCard(
                    'Error Rate',
                    '0.1%',
                    Icons.error_outline,
                    Colors.orange,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _buildMetricCard(
                    'Availability',
                    '99.9%',
                    Icons.check_circle,
                    Colors.green,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResourceUsage() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Resource Usage',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            _buildResourceChart('CPU', _systemMetrics['cpu_usage'] ?? 0.0, Colors.red),
            const SizedBox(height: 12),
            _buildResourceChart('Memory', _systemMetrics['memory_usage'] ?? 0.0, Colors.blue),
            const SizedBox(height: 12),
            _buildResourceChart('Network', _systemMetrics['network_activity'] ?? 0.0, Colors.green),
            const SizedBox(height: 12),
            _buildResourceChart('Storage', _systemMetrics['storage_used'] ?? 0.0, Colors.orange),
          ],
        ),
      ),
    );
  }

  Widget _buildResourceChart(String label, double value, Color color) {
    return Column(
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
        Container(
          height: 8,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(4),
            color: Colors.grey[300],
          ),
          child: FractionallySizedBox(
            alignment: Alignment.centerLeft,
            widthFactor: value / 100,
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(4),
                color: color,
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildPerformanceCharts() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Performance Trends',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            Container(
              height: 200,
              decoration: BoxDecoration(
                color: Colors.grey[100],
                borderRadius: BorderRadius.circular(8),
              ),
              child: const SystemPerformanceChart(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRecentActivity() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Recent Activity',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            if (_recentActivity.isEmpty)
              const Text('No recent activity')
            else
              ..._recentActivity.map((activity) => _buildActivityItem(activity)),
          ],
        ),
      ),
    );
  }

  Widget _buildActivityItem(Map<String, dynamic> activity) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(
            activity['icon'],
            color: activity['color'],
            size: 20,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  activity['message'],
                  style: const TextStyle(fontWeight: FontWeight.w500),
                ),
                Text(
                  _formatTimestamp(activity['timestamp']),
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSystemLogs() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'System Logs',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            Container(
              height: 200,
              decoration: BoxDecoration(
                color: Colors.grey[900],
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Padding(
                padding: EdgeInsets.all(12),
                child: Text(
                  '[INFO] System initialized\n[INFO] Security checks passed\n[INFO] Plugins loaded successfully\n[DEBUG] Performance monitoring active',
                  style: TextStyle(
                    color: Colors.green,
                    fontFamily: 'monospace',
                    fontSize: 12,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _formatTimestamp(DateTime timestamp) {
    final now = DateTime.now();
    final difference = now.difference(timestamp);
    
    if (difference.inMinutes < 1) {
      return 'Just now';
    } else if (difference.inMinutes < 60) {
      return '${difference.inMinutes}m ago';
    } else if (difference.inHours < 24) {
      return '${difference.inHours}h ago';
    } else {
      return '${difference.inDays}d ago';
    }
  }

  void _runSecurityCheck() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Running security check...'),
        duration: Duration(seconds: 2),
      ),
    );
  }

         Widget _buildStatCard(
           BuildContext context,
           String title,
           String value,
           IconData icon,
           Color color, {
           String? subtitle,
         }) {
           return ConnectiasAnimations.animatedCard(
             child: Padding(
               padding: const EdgeInsets.all(16),
               child: Column(
                 children: [
                   ConnectiasAnimations.pulsing(
                     child: Icon(icon, color: color, size: 32),
                   ),
                   const SizedBox(height: 8),
                   Text(
                     title,
                     style: const TextStyle(fontWeight: FontWeight.w500),
                     textAlign: TextAlign.center,
                   ),
                   const SizedBox(height: 4),
                  // Prüfe ob value numerisch ist
                  double.tryParse(value) != null
                      ? ConnectiasAnimations.animateValue<double>(
                          begin: 0.0,
                          end: double.parse(value),
                          duration: const Duration(milliseconds: 800),
                          builder: (context, animatedValue, child) {
                            return Text(
                              animatedValue.toStringAsFixed(0),
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                                color: color,
                              ),
                            );
                          },
                        )
                      : Text(
                          value,
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: color,
                          ),
                        ),
                   if (subtitle != null) ...[
                     const SizedBox(height: 4),
                     Text(
                       subtitle,
                       style: TextStyle(
                         fontSize: 12,
                         color: Colors.grey[600],
                       ),
                       textAlign: TextAlign.center,
                     ),
                   ],
                 ],
               ),
             ),
           );
         }

         Widget _buildActionCard(
           BuildContext context,
           String title,
           String subtitle,
           IconData icon,
           Color color,
           VoidCallback onTap,
         ) {
           return ConnectiasAnimations.animatedButton(
             onPressed: onTap,
             child: ConnectiasAnimations.animatedCard(
               child: Padding(
                 padding: const EdgeInsets.all(16),
                 child: Column(
                   children: [
                     ConnectiasAnimations.rotating(
                       child: Icon(icon, size: 32, color: color),
                     ),
                     const SizedBox(height: 8),
                     Text(
                       title,
                       style: const TextStyle(fontWeight: FontWeight.bold),
                       textAlign: TextAlign.center,
                     ),
                     const SizedBox(height: 4),
                     Text(
                       subtitle,
                       style: TextStyle(
                         fontSize: 12,
                         color: Theme.of(context).colorScheme.onSurfaceVariant,
                       ),
                       textAlign: TextAlign.center,
                     ),
                   ],
                 ),
               ),
             ),
           );
         }

  Widget _buildMetricCard(
    String title,
    String value,
    IconData icon,
    Color color,
  ) {
    return Card(
      color: color.withValues(alpha: 0.1),
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
}

/// System Performance Chart Widget
class SystemPerformanceChart extends StatefulWidget {
  const SystemPerformanceChart({super.key});
  
  @override
  State<SystemPerformanceChart> createState() => _SystemPerformanceChartState();
}

class _SystemPerformanceChartState extends State<SystemPerformanceChart> {
  List<FlSpot> _systemData = [];
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
    // Lade historische System-Daten (letzte 12 Stunden)
    _generateSampleData();
  }
  
  void _startPeriodicUpdates() {
    _updateTimer = Timer.periodic(const Duration(seconds: 10), (timer) {
      _updateChartData();
    });
  }
  
  void _generateSampleData() {
    // Generiere Sample-System-Daten für Demo
    final now = DateTime.now();
    _systemData = List.generate(12, (index) {
      final time = now.subtract(Duration(hours: 11 - index));
      final hour = time.hour;
      final system = 40 + (hour * 3) + (index % 4) * 8; // Simuliere System-Performance
      return FlSpot(index.toDouble(), system.toDouble());
    });
  }
  
  void _updateChartData() {
    if (!mounted) return;
    
    setState(() {
      // Verschiebe alle Daten um eine Position nach links
      _systemData.removeAt(0);
      
      // Füge neue Daten am Ende hinzu
      final now = DateTime.now();
      final hour = now.hour;
      final minute = now.minute;
      
      final newSystem = 40 + (hour * 3) + (minute % 30) * 2;
      
      _systemData.add(FlSpot(11, newSystem.toDouble()));
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
            'System Performance (12h)',
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
                  verticalInterval: 2,
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
                      interval: 3,
                      getTitlesWidget: (value, meta) {
                        final hour = DateTime.now().subtract(
                          Duration(hours: 11 - value.toInt()),
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
                maxX: 11,
                minY: 0,
                maxY: 100,
                lineBarsData: [
                  LineChartBarData(
                    spots: _systemData,
                    isCurved: true,
                    color: Colors.purple,
                    barWidth: 3,
                    isStrokeCapRound: true,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                      show: true,
                      color: Colors.purple.withOpacity(0.1),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Center(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: const BoxDecoration(
                    color: Colors.purple,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 4),
                const Text(
                  'System Performance',
                  style: TextStyle(fontSize: 12),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
