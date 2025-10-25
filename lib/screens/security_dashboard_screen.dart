/// Security Dashboard – Echtzeit-Sicherheitsstatus
/// 
/// Zeigt RASP-Checks, Threat-Detection und Sicherheitsmetriken
library security_dashboard_screen;

import 'package:flutter/material.dart';
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

  @override
  void initState() {
    super.initState();
    _loadSecurityStatus();
  }

  Future<void> _loadSecurityStatus() async {
    try {
      final status = await connectiasService.checkSecurity();
      setState(() {
        _raspStatus = status;
        _loading = false;
      });
    } catch (e) {
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Security Dashboard')),
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

          // Checks
          const Text('Security Checks', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          _buildCheckTile('Root Detection', status.root),
          _buildCheckTile('Debugger Detection', status.debugger),
          _buildCheckTile('Emulator Detection', status.emulator),
          _buildCheckTile('Tamper Detection', status.tamper),
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
}
