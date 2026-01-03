import 'package:flutter/material.dart';
import '../services/log_service.dart';
import '../widgets/app_drawer.dart';

class LogViewerScreen extends StatefulWidget {
  const LogViewerScreen({super.key});

  @override
  State<LogViewerScreen> createState() => _LogViewerScreenState();
}

class _LogViewerScreenState extends State<LogViewerScreen> {
  final LogService _logService = LogService();
  List<String> _logs = [];
  String _currentLogLevel = 'INFO';
  bool _isLoading = false;

  final List<String> _logLevels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'];

  @override
  void initState() {
    super.initState();
    _loadLogs();
    _loadCurrentLogLevel();
  }

  Future<void> _loadLogs() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final logs = await _logService.getLogs(
        levelFilter: _currentLogLevel == 'ALL' ? null : _currentLogLevel,
        limit: 100,
        offset: 0,
      );
      setState(() {
        _logs = logs;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error loading logs: $e')),
        );
      }
    }
  }

  Future<void> _loadCurrentLogLevel() async {
    try {
      final level = await _logService.getLogLevel();
      setState(() {
        _currentLogLevel = level;
      });
    } catch (e) {
      // Ignore error, use default
    }
  }

  Future<void> _setLogLevel(String level) async {
    try {
      await _logService.setLogLevel(level);
      setState(() {
        _currentLogLevel = level;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Log level set to $level')),
        );
      }
      _loadLogs();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error setting log level: $e')),
        );
      }
    }
  }

  Color _getLogLevelColor(String log) {
    if (log.contains('ERROR')) return Colors.red;
    if (log.contains('WARN')) return Colors.orange;
    if (log.contains('INFO')) return Colors.blue;
    if (log.contains('DEBUG')) return Colors.green;
    if (log.contains('TRACE')) return Colors.grey;
    return Colors.black;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('Log Viewer'),
        leading: Builder(
          builder: (context) => IconButton(
            icon: const Icon(Icons.menu),
            onPressed: () => Scaffold.of(context).openDrawer(),
          ),
        ),
      ),
      drawer: const AppDrawer(),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Row(
              children: [
                const Text(
                  'Log Level: ',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
                const SizedBox(width: 8),
                DropdownButton<String>(
                  value: _currentLogLevel,
                  items: _logLevels.map((String level) {
                    return DropdownMenuItem<String>(
                      value: level,
                      child: Text(level),
                    );
                  }).toList(),
                  onChanged: (String? newValue) {
                    if (newValue != null) {
                      _setLogLevel(newValue);
                    }
                  },
                ),
                const Spacer(),
                IconButton(
                  icon: const Icon(Icons.refresh),
                  onPressed: _loadLogs,
                ),
              ],
            ),
          ),
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _logs.isEmpty
                    ? const Center(child: Text('No logs available'))
                    : RefreshIndicator(
                        onRefresh: _loadLogs,
                        child: ListView.builder(
                          itemCount: _logs.length,
                          itemBuilder: (context, index) {
                            final log = _logs[index];
                            return ListTile(
                              title: Text(
                                log,
                                style: TextStyle(
                                  color: _getLogLevelColor(log),
                                  fontSize: 12,
                                ),
                              ),
                              dense: true,
                            );
                          },
                        ),
                      ),
          ),
        ],
      ),
    );
  }
}

