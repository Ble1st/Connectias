/// Security Dashboard – Echtzeit-Sicherheitsstatus
/// 
/// Zeigt RASP-Checks, Threat-Detection und Sicherheitsmetriken
library;

import 'package:flutter/material.dart';
import 'dart:async';
import '../services/connectias_service.dart';

/// Security Dashboard
class SecurityDashboardScreen extends StatefulWidget {
  const SecurityDashboardScreen({super.key});

  @override
  State<SecurityDashboardScreen> createState() => _SecurityDashboardScreenState();
}

class _SecurityDashboardScreenState extends State<SecurityDashboardScreen> {
  RaspCheckResult? _raspStatus;
  bool _loading = true;
  Timer? _refreshTimer;
  List<ThreatEvent> _threatEvents = [];
  List<PluginSecurityInfo> _pluginSecurity = [];
  SecurityMetrics? _securityMetrics;

  @override
  void initState() {
    super.initState();
    _loadSecurityStatus();
    _startRealTimeMonitoring();
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadSecurityStatus() async {
    try {
      final status = await connectiasService.checkSecurity();
      final threatEvents = await connectiasService.getThreatEvents();
      final pluginSecurity = await connectiasService.getPluginSecurityInfo();
      final securityMetrics = await connectiasService.getSecurityMetrics();
      
      setState(() {
        _raspStatus = status;
        _threatEvents = threatEvents;
        _pluginSecurity = pluginSecurity;
        _securityMetrics = securityMetrics;
        _loading = false;
      });
    } catch (e) {
      setState(() => _loading = false);
    }
  }

  void _startRealTimeMonitoring() {
    _refreshTimer = Timer.periodic(const Duration(seconds: 5), (timer) {
      _loadSecurityStatus();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Security Dashboard'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadSecurityStatus,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _buildSecurityInfo(),
    );
  }

  Widget _buildSecurityInfo() {
    if (_raspStatus == null) {
      return const Center(child: Text('Fehler beim Laden'));
    }

    final status = _raspStatus!;
    final isSafe = status.isSafe;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Status Card
          Card(
            color: isSafe ? Colors.green : Colors.orange,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Icon(
                    isSafe ? Icons.shield_outlined : Icons.warning,
                    color: Colors.white,
                    size: 32,
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Gerät Status',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          isSafe ? '✅ Sicher' : '⚠️ Verdächtig',
                          style: const TextStyle(color: Colors.white),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Security Metrics
          if (_securityMetrics != null) _buildSecurityMetrics(),
          const SizedBox(height: 24),

          // Checks
          const Text('Security Checks', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          _buildCheckTile('Root Detection', status.root),
          _buildCheckTile('Debugger Detection', status.debugger),
          _buildCheckTile('Emulator Detection', status.emulator),
          _buildCheckTile('Tamper Detection', status.tamper),
          const SizedBox(height: 24),

          // Threat Events
          _buildThreatEvents(),
          const SizedBox(height: 24),

          // Plugin Security
          _buildPluginSecurity(),
        ],
      ),
    );
  }

  Widget _buildCheckTile(String label, int value) {
    final text = value == 0 ? '✅ Safe' : value == 1 ? '⚠️ Suspicious' : '❌ Compromised';
    final color = value == 0 ? Colors.green : value == 1 ? Colors.orange : Colors.red;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label),
          Chip(
            label: Text(text),
            backgroundColor: color.withValues(alpha: 0.2),
            labelStyle: TextStyle(color: color),
          ),
        ],
      ),
    );
  }

  Widget _buildSecurityMetrics() {
    final metrics = _securityMetrics!;
    
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Security Metrics',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _buildMetricCard(
                    'Threats Blocked',
                    metrics.threatsBlocked.toString(),
                    Icons.block,
                    Colors.red,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _buildMetricCard(
                    'Plugins Monitored',
                    metrics.pluginsMonitored.toString(),
                    Icons.security,
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
                    'Rate Limits',
                    metrics.rateLimitsHit.toString(),
                    Icons.speed,
                    Colors.orange,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _buildMetricCard(
                    'Security Score',
                    '${(metrics.securityScore * 100).toInt()}%',
                    Icons.grade,
                    metrics.securityScore > 0.8 ? Colors.green : Colors.yellow,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMetricCard(String title, String value, IconData icon, Color color) {
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
                fontSize: 20,
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

  Widget _buildThreatEvents() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Recent Threat Events',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            if (_threatEvents.isEmpty)
              const Text('No recent threats detected')
            else
              ..._threatEvents.take(5).map((event) => _buildThreatEventTile(event)),
          ],
        ),
      ),
    );
  }

  Widget _buildThreatEventTile(ThreatEvent event) {
    Color color;
    IconData icon;
    
    switch (event.severity) {
      case 'low':
        color = Colors.yellow;
        icon = Icons.info;
        break;
      case 'medium':
        color = Colors.orange;
        icon = Icons.warning;
        break;
      case 'high':
        color = Colors.red;
        icon = Icons.error;
        break;
      case 'critical':
        color = Colors.purple;
        icon = Icons.dangerous;
        break;
      default:
        color = Colors.grey;
        icon = Icons.help;
    }

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  event.description,
                  style: const TextStyle(fontWeight: FontWeight.w500),
                ),
                Text(
                  'Plugin: ${event.pluginId} • ${event.timestamp}',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ),
          Chip(
            label: Text(event.severity.toUpperCase()),
            backgroundColor: color.withValues(alpha: 0.2),
            labelStyle: TextStyle(color: color, fontSize: 10),
          ),
        ],
      ),
    );
  }

  Widget _buildPluginSecurity() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Plugin Security Status',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            if (_pluginSecurity.isEmpty)
              const Text('No plugins loaded')
            else
              ..._pluginSecurity.map((plugin) => _buildPluginSecurityTile(plugin)),
          ],
        ),
      ),
    );
  }

  Widget _buildPluginSecurityTile(PluginSecurityInfo plugin) {
    Color statusColor;
    IconData statusIcon;
    
    if (plugin.isSecure) {
      statusColor = Colors.green;
      statusIcon = Icons.check_circle;
    } else {
      statusColor = Colors.red;
      statusIcon = Icons.cancel;
    }

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Icon(statusIcon, color: statusColor, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  plugin.pluginId,
                  style: const TextStyle(fontWeight: FontWeight.w500),
                ),
                Text(
                  'Permissions: ${plugin.permissions.length} • Threats: ${plugin.threatCount}',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ),
          Chip(
            label: Text(plugin.isSecure ? 'SECURE' : 'RISK'),
            backgroundColor: statusColor.withValues(alpha: 0.2),
            labelStyle: TextStyle(color: statusColor, fontSize: 10),
          ),
        ],
      ),
    );
  }
}

