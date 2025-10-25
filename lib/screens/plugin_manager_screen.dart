/// Plugin Manager Screen – UI für Plugin-Verwaltung
/// 
/// Zeigt geladene Plugins, Statusanzeigen und Management-Optionen
library plugin_manager_screen;

import 'package:flutter/material.dart';
import '../services/connectias_service.dart';
import '../models/plugin_model.dart';

class PluginManagerScreen extends StatefulWidget {
  const PluginManagerScreen({Key? key}) : super(key: key);

  @override
  State<PluginManagerScreen> createState() => _PluginManagerScreenState();
}

class _PluginManagerScreenState extends State<PluginManagerScreen> {
  final List<PluginModel> _plugins = [];
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _loadPlugins();
  }

  Future<void> _loadPlugins() async {
    setState(() => _loading = true);
    try {
      final service = connectiasService;
      final pluginIds = await service.listPlugins();
      setState(() {
        _plugins.clear();
        for (final id in pluginIds) {
          _plugins.add(PluginModel(
            id: id,
            status: PluginStatus.loaded,
          ));
        }
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Plugins')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _plugins.isEmpty
              ? const Center(child: Text('Keine Plugins'))
              : ListView.builder(
                  itemCount: _plugins.length,
                  itemBuilder: (c, i) => ListTile(
                    title: Text(_plugins[i].id),
                    trailing: const Icon(Icons.check_circle, color: Colors.green),
                  ),
                ),
    );
  }
}
