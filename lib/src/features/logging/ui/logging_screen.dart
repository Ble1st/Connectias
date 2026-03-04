import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/log_entry.dart';
import '../services/logging_service.dart';
import '../domain/export_logs_use_case.dart';
import '../domain/clear_logs_use_case.dart';

final loggingServiceProvider = Provider<LoggingService>((ref) => LoggingService.instance);

final exportLogsUseCaseProvider = Provider<ExportLogsUseCase>((ref) {
  return ExportLogsUseCase(ref.watch(loggingServiceProvider));
});

final clearLogsUseCaseProvider = Provider<ClearLogsUseCase>((ref) {
  return ClearLogsUseCase(ref.watch(loggingServiceProvider));
});

/// Screen for viewing and exporting logs.
class LoggingScreen extends ConsumerStatefulWidget {
  const LoggingScreen({super.key});

  @override
  ConsumerState<LoggingScreen> createState() => _LoggingScreenState();
}

class _LoggingScreenState extends ConsumerState<LoggingScreen> {
  List<LogEntry> _logs = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadLogs();
  }

  Future<void> _loadLogs() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final service = LoggingService.instance;
      final entries = await service.getLogs(limit: 5000);
      setState(() {
        _logs = entries.cast<LogEntry>();
        _loading = false;
      });
    } catch (e, st) {
      LoggingService.instance.e('LoggingScreen', '${e.toString()}\n$st');
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _export() async {
    final useCase = ref.read(exportLogsUseCaseProvider);
    final ok = await useCase.call();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(ok ? 'Logs exported' : 'Export failed'),
        ),
      );
    }
  }

  Future<void> _clear() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Clear logs'),
        content: const Text('Delete all logs?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Clear'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await ref.read(clearLogsUseCaseProvider).call();
      await _loadLogs();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Logs'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loading ? null : _loadLogs,
          ),
          IconButton(
            icon: const Icon(Icons.save_alt),
            onPressed: _export,
          ),
          IconButton(
            icon: const Icon(Icons.delete_outline),
            onPressed: _clear,
          ),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading && _logs.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline, size: 48, color: Theme.of(context).colorScheme.error),
              const SizedBox(height: 16),
              Text(_error!, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton(onPressed: _loadLogs, child: const Text('Retry')),
            ],
          ),
        ),
      );
    }
    if (_logs.isEmpty) {
      return const Center(child: Text('No logs'));
    }
    return ListView.builder(
      itemCount: _logs.length,
      itemBuilder: (context, index) {
        final log = _logs[index];
        return ListTile(
          title: Text(
            log.message,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          subtitle: Text(
            '${log.timestamp.toIso8601String()} [${log.level}] [${log.source}] ${log.tag}',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        );
      },
    );
  }
}
