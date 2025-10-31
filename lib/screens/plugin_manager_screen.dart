/// Plugin Manager Screen – UI für Plugin-Verwaltung
/// 
/// Zeigt geladene Plugins, Statusanzeigen und Management-Optionen
library;

import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:io';
import 'package:path/path.dart' as path;
import 'package:file_picker/file_picker.dart';
import '../services/connectias_service.dart';
import '../models/plugin_model.dart';
import 'plugin_details_screen.dart';

class PluginManagerScreen extends StatefulWidget {
  const PluginManagerScreen({super.key});

  @override
  State<PluginManagerScreen> createState() => _PluginManagerScreenState();
}

class _PluginManagerScreenState extends State<PluginManagerScreen> with TickerProviderStateMixin {
  final List<PluginModel> _plugins = [];
  bool _loading = false;
  Timer? _refreshTimer;
  String _searchQuery = '';
  PluginSortOption _sortOption = PluginSortOption.name;
  bool _showOnlyActive = false;

  @override
  void initState() {
    super.initState();
    _loadPlugins();
    _startAutoRefresh();
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  void _startAutoRefresh() {
    _refreshTimer = Timer.periodic(const Duration(seconds: 10), (timer) {
      _loadPlugins();
    });
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
            name: _getPluginDisplayName(id),
            version: '1.0.0',
            author: 'Connectias Team',
            category: _getPluginCategory(id),
            description: _getPluginDescription(id),
            status: PluginStatus.active,
            permissions: _getPluginPermissions(id),
            lastUsed: DateTime.now().subtract(Duration(minutes: id.hashCode % 60)),
            memoryUsage: (id.hashCode % 100) + 10,
            isEnabled: true,
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

  String _getPluginDisplayName(String id) {
    return id.replaceAll('-', ' ').split(' ').map((word) => 
      word.isNotEmpty ? word[0].toUpperCase() + word.substring(1) : ''
    ).join(' ');
  }

  List<String> _getPluginPermissions(String id) {
    final permissions = <String>[];
    if (id.contains('storage')) permissions.add('Storage');
    if (id.contains('network')) permissions.add('Network');
    if (id.contains('system')) permissions.add('System');
    return permissions.isNotEmpty ? permissions : ['Basic'];
  }

  String _getPluginCategory(String id) {
    if (id.contains('storage')) return 'Storage';
    if (id.contains('network')) return 'Network';
    if (id.contains('security')) return 'Security';
    if (id.contains('utility')) return 'Utility';
    return 'General';
  }

  String _getPluginDescription(String id) {
    if (id.contains('storage')) return 'Plugin for storage management and file operations';
    if (id.contains('network')) return 'Plugin for network communication and API calls';
    if (id.contains('security')) return 'Plugin for security monitoring and protection';
    if (id.contains('utility')) return 'Utility plugin for various system functions';
    return 'A general purpose plugin for the Connectias platform';
  }

  @override
  Widget build(BuildContext context) {
    final filteredPlugins = _getFilteredPlugins();
    
    return Scaffold(
      appBar: AppBar(
        title: const Text('Plugin Manager'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadPlugins,
          ),
          PopupMenuButton<String>(
            onSelected: _handleSortOption,
            itemBuilder: (context) => [
              const PopupMenuItem(value: 'name', child: Text('Sort by Name')),
              const PopupMenuItem(value: 'status', child: Text('Sort by Status')),
              const PopupMenuItem(value: 'memory', child: Text('Sort by Memory')),
              const PopupMenuItem(value: 'lastUsed', child: Text('Sort by Last Used')),
            ],
            icon: const Icon(Icons.sort),
          ),
        ],
      ),
      body: Column(
        children: [
          _buildSearchAndFilters(),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : filteredPlugins.isEmpty
                    ? _buildEmptyState()
                    : _buildPluginList(filteredPlugins),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showInstallPluginDialog,
        icon: const Icon(Icons.add),
        label: const Text('Install Plugin'),
      ),
    );
  }

  List<PluginModel> _getFilteredPlugins() {
    var filtered = _plugins.where((plugin) {
      final matchesSearch = _searchQuery.isEmpty || 
          plugin.name.toLowerCase().contains(_searchQuery.toLowerCase()) ||
          plugin.id.toLowerCase().contains(_searchQuery.toLowerCase());
      
      final matchesFilter = !_showOnlyActive || plugin.isEnabled;
      
      return matchesSearch && matchesFilter;
    }).toList();

    // Sort plugins
    switch (_sortOption) {
      case PluginSortOption.name:
        filtered.sort((a, b) => a.name.compareTo(b.name));
        break;
      case PluginSortOption.status:
        filtered.sort((a, b) => a.status.index.compareTo(b.status.index));
        break;
      case PluginSortOption.memory:
        filtered.sort((a, b) => b.memoryUsage.compareTo(a.memoryUsage));
        break;
      case PluginSortOption.lastUsed:
        filtered.sort((a, b) => b.lastUsed.compareTo(a.lastUsed));
        break;
    }

    return filtered;
  }

  void _handleSortOption(String option) {
    setState(() {
      switch (option) {
        case 'name':
          _sortOption = PluginSortOption.name;
          break;
        case 'status':
          _sortOption = PluginSortOption.status;
          break;
        case 'memory':
          _sortOption = PluginSortOption.memory;
          break;
        case 'lastUsed':
          _sortOption = PluginSortOption.lastUsed;
          break;
      }
    });
  }

  Widget _buildSearchAndFilters() {
    return Container(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          TextField(
            decoration: const InputDecoration(
              hintText: 'Search plugins...',
              prefixIcon: Icon(Icons.search),
              border: OutlineInputBorder(),
            ),
            onChanged: (value) {
              setState(() {
                _searchQuery = value;
              });
            },
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              FilterChip(
                label: const Text('Active Only'),
                selected: _showOnlyActive,
                onSelected: (selected) {
                  setState(() {
                    _showOnlyActive = selected;
                  });
                },
              ),
              const SizedBox(width: 8),
              Text('${_getFilteredPlugins().length} plugins'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.extension,
            size: 64,
            color: Colors.grey[400],
          ),
          const SizedBox(height: 16),
          Text(
            _searchQuery.isNotEmpty ? 'No plugins match your search' : 'No plugins installed',
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: Colors.grey[600],
            ),
          ),
          const SizedBox(height: 8),
          Text(
            _searchQuery.isNotEmpty 
                ? 'Try adjusting your search terms'
                : 'Install your first plugin to get started',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: Colors.grey[500],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPluginList(List<PluginModel> plugins) {
    return RefreshIndicator(
      onRefresh: _loadPlugins,
      child: ListView.builder(
        itemCount: plugins.length,
        itemBuilder: (context, index) {
          final plugin = plugins[index];
          return _buildPluginCard(plugin);
        },
      ),
    );
  }

  Widget _buildPluginCard(PluginModel plugin) {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: InkWell(
        onTap: () => _navigateToPluginDetails(plugin),
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  _buildPluginIcon(plugin),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          plugin.name,
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          'v${plugin.version}',
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.grey[600],
                          ),
                        ),
                      ],
                    ),
                  ),
                  _buildStatusChip(plugin.status),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  _buildPermissionChips(plugin.permissions),
                  const Spacer(),
                  _buildMemoryUsage(plugin.memoryUsage),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Icon(
                    Icons.access_time,
                    size: 14,
                    color: Colors.grey[600],
                  ),
                  const SizedBox(width: 4),
                  Text(
                    'Last used: ${_formatLastUsed(plugin.lastUsed)}',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey[600],
                    ),
                  ),
                  const Spacer(),
                  _buildActionButtons(plugin),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPluginIcon(PluginModel plugin) {
    Color iconColor;
    IconData iconData;
    
    if (plugin.permissions.contains('Storage')) {
      iconColor = Colors.blue;
      iconData = Icons.storage;
    } else if (plugin.permissions.contains('Network')) {
      iconColor = Colors.green;
      iconData = Icons.network_check;
    } else if (plugin.permissions.contains('System')) {
      iconColor = Colors.orange;
      iconData = Icons.settings;
    } else {
      iconColor = Colors.grey;
      iconData = Icons.extension;
    }

    return Container(
      width: 40,
      height: 40,
      decoration: BoxDecoration(
        color: iconColor.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Icon(iconData, color: iconColor, size: 20),
    );
  }

  Widget _buildStatusChip(PluginStatus status) {
    Color color;
    String text;
    
    switch (status) {
      case PluginStatus.active:
        color = Colors.green;
        text = 'Active';
        break;
      case PluginStatus.loading:
        color = Colors.blue;
        text = 'Loading';
        break;
      case PluginStatus.error:
        color = Colors.red;
        text = 'Error';
        break;
      case PluginStatus.inactive:
        color = Colors.orange;
        text = 'Inactive';
        break;
    }

    return Chip(
      label: Text(text),
      backgroundColor: color.withValues(alpha: 0.2),
      labelStyle: TextStyle(color: color, fontSize: 10),
    );
  }

  Widget _buildPermissionChips(List<String> permissions) {
    return Wrap(
      spacing: 4,
      children: permissions.take(3).map((permission) {
        return Chip(
          label: Text(permission),
          backgroundColor: Colors.blue.withValues(alpha: 0.1),
          labelStyle: const TextStyle(fontSize: 10),
        );
      }).toList(),
    );
  }

  Widget _buildMemoryUsage(int memoryUsage) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          Icons.memory,
          size: 14,
          color: Colors.grey[600],
        ),
        const SizedBox(width: 4),
        Text(
          '${memoryUsage}MB',
          style: TextStyle(
            fontSize: 12,
            color: Colors.grey[600],
          ),
        ),
      ],
    );
  }

  Widget _buildActionButtons(PluginModel plugin) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          icon: Icon(
            plugin.isEnabled ? Icons.pause : Icons.play_arrow,
            size: 20,
          ),
          onPressed: () => _togglePlugin(plugin),
          tooltip: plugin.isEnabled ? 'Disable' : 'Enable',
        ),
        IconButton(
          icon: const Icon(Icons.more_vert, size: 20),
          onPressed: () => _showPluginMenu(plugin),
        ),
      ],
    );
  }

  String _formatLastUsed(DateTime lastUsed) {
    final now = DateTime.now();
    final difference = now.difference(lastUsed);
    
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

  void _navigateToPluginDetails(PluginModel plugin) async {
    final updatedPlugin = await Navigator.push<PluginModel>(
      context,
      MaterialPageRoute(
        builder: (context) => PluginDetailsScreen(plugin: plugin),
      ),
    );
    
    // Zeige SnackBar wenn Plugin aktualisiert wurde
    if (updatedPlugin != null && mounted) {
      setState(() {
        // Aktualisiere Plugin in der Liste
        final index = _plugins.indexWhere((p) => p.id == updatedPlugin.id);
        if (index != -1) {
          _plugins[index] = updatedPlugin;
        }
      });
      
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Plugin ${updatedPlugin.name} wurde ${updatedPlugin.status == PluginStatus.active ? 'aktiviert' : 'deaktiviert'}'),
          backgroundColor: Colors.green,
        ),
      );
    }
  }

  void _togglePlugin(PluginModel plugin) {
    setState(() {
      plugin.isEnabled = !plugin.isEnabled;
    });
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          plugin.isEnabled 
              ? '${plugin.name} enabled' 
              : '${plugin.name} disabled',
        ),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  void _showPluginMenu(PluginModel plugin) {
    showModalBottomSheet(
      context: context,
      builder: (context) => Container(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.info),
              title: const Text('View Details'),
              onTap: () {
                Navigator.pop(context);
                _navigateToPluginDetails(plugin);
              },
            ),
            ListTile(
              leading: Icon(plugin.isEnabled ? Icons.pause : Icons.play_arrow),
              title: Text(plugin.isEnabled ? 'Disable' : 'Enable'),
              onTap: () {
                Navigator.pop(context);
                _togglePlugin(plugin);
              },
            ),
            ListTile(
              leading: const Icon(Icons.update),
              title: const Text('Check for Updates'),
              onTap: () {
                Navigator.pop(context);
                _checkForUpdates(plugin);
              },
            ),
            ListTile(
              leading: const Icon(Icons.delete, color: Colors.red),
              title: const Text('Uninstall', style: TextStyle(color: Colors.red)),
              onTap: () {
                Navigator.pop(context);
                _showUninstallDialog(plugin);
              },
            ),
          ],
        ),
      ),
    );
  }

  void _checkForUpdates(PluginModel plugin) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Checking for updates for ${plugin.name}...'),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  void _showUninstallDialog(PluginModel plugin) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Uninstall Plugin'),
        content: Text('Are you sure you want to uninstall ${plugin.name}?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              _uninstallPlugin(plugin);
            },
            child: const Text('Uninstall', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }

  void _uninstallPlugin(PluginModel plugin) {
    setState(() {
      _plugins.remove(plugin);
    });
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('${plugin.name} has been uninstalled'),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  void _showInstallPluginDialog() {
    showDialog(
      context: context,
      builder: (context) => PluginInstallDialog(
        onInstall: (file) async {
          // Dialog wird in _installPlugin() geschlossen, nicht hier
          await _installPlugin(file);
        },
      ),
    );
  }
  
  Future<void> _installPlugin(File file) async {
    // Schließe Install-Dialog zuerst
    if (mounted) {
      Navigator.of(context).pop();
    }
    
    try {
      // Zeige Progress-Dialog
      if (mounted) {
        showDialog(
          context: context,
          barrierDismissible: false,
          builder: (context) => const AlertDialog(
            content: Row(
              children: [
                CircularProgressIndicator(),
                SizedBox(width: 16),
                Text('Installing plugin...'),
              ],
            ),
          ),
        );
      }
      
      // Lade Plugin
      final pluginId = await connectiasService.loadPlugin(file.path);
      
      // Schließe Progress-Dialog
      if (mounted) Navigator.pop(context);
      
      // Zeige Success-Dialog
      if (mounted) {
        showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Success'),
            content: Text('Plugin installed successfully!\nID: $pluginId'),
            actions: [
              TextButton(
                onPressed: () {
                  Navigator.pop(context);
                  setState(() {}); // Refresh plugin list
                },
                child: const Text('OK'),
              ),
            ],
          ),
        );
      }
    } catch (e) {
      // Schließe Progress-Dialog
      if (mounted) Navigator.pop(context);
      
      // Zeige Error-Dialog
      if (mounted) {
        showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Installation Failed'),
            content: Text('Failed to install plugin:\n$e'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('OK'),
              ),
            ],
          ),
        );
      }
    }
  }
}

enum PluginSortOption {
  name,
  status,
  memory,
  lastUsed,
}

/// Plugin Installation Dialog
class PluginInstallDialog extends StatefulWidget {
  final Function(File) onInstall;
  
  const PluginInstallDialog({
    super.key,
    required this.onInstall,
  });
  
  @override
  State<PluginInstallDialog> createState() => _PluginInstallDialogState();
}

class _PluginInstallDialogState extends State<PluginInstallDialog> {
  bool _isLoading = false;
  String? _selectedFile;
  
  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Install Plugin'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('Select a plugin file to install:'),
          const SizedBox(height: 16),
          if (_selectedFile != null) ...[
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.grey[100],
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(Icons.file_present, color: Colors.blue),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      _selectedFile!.split('/').last,
                      style: const TextStyle(fontWeight: FontWeight.w500),
                    ),
                  ),
                  IconButton(
                    onPressed: () => setState(() => _selectedFile = null),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
          ],
          ElevatedButton.icon(
            onPressed: _isLoading ? null : _selectFile,
            icon: const Icon(Icons.folder_open),
            label: Text(_selectedFile == null ? 'Select File' : 'Change File'),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: _isLoading ? null : () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _selectedFile != null && !_isLoading ? _installPlugin : null,
          child: _isLoading 
            ? const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Text('Install'),
        ),
      ],
    );
  }
  
  Future<void> _selectFile() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['wasm', 'zip'],
        allowMultiple: false,
      );
      
      if (result != null && result.files.isNotEmpty) {
        final file = result.files.first;
        // Verwende name wenn path null ist, normalisiere Pfad
        final filePath = file.path ?? file.name;
        final displayName = file.name.isNotEmpty ? file.name : path.basename(filePath);
        
        // Validiere Datei-Existenz und Lesbarkeit
        if (filePath.isNotEmpty) {
          final fileObj = File(filePath);
          if (await fileObj.exists()) {
            setState(() {
              _selectedFile = filePath;
            });
          } else {
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('Datei existiert nicht: $displayName')),
              );
            }
          }
        } else {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('Ungültiger Dateipfad: $displayName')),
            );
          }
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Fehler beim Auswählen der Datei: $e')),
        );
      }
    }
  }
  
  Future<void> _installPlugin() async {
    if (_selectedFile == null) return;
    
    setState(() => _isLoading = true);
    
    try {
      final file = File(_selectedFile!);
      await widget.onInstall(file);
      
      // Dialog wird nicht hier geschlossen, sondern in der Hauptklasse _installPlugin()
    } catch (e) {
      // Error wird von der Hauptklasse _installPlugin Methode behandelt
      // Hier nur setState zurücksetzen
      if (mounted) {
        setState(() => _isLoading = false);
      }
      // Re-throw damit die Hauptklasse den Error behandeln kann
      rethrow;
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }
}
