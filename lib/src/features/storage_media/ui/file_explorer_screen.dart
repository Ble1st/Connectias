import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../view_model/file_explorer_view_model.dart';

/// File explorer for NTFS USB volumes.
class FileExplorerScreen extends ConsumerStatefulWidget {
  const FileExplorerScreen({
    super.key,
    required this.deviceId,
    this.deviceName,
  });

  final String deviceId;
  final String? deviceName;

  @override
  ConsumerState<FileExplorerScreen> createState() => _FileExplorerScreenState();
}

class _FileExplorerScreenState extends ConsumerState<FileExplorerScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(fileExplorerViewModelProvider.notifier).openVolume(widget.deviceId);
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(fileExplorerViewModelProvider);
    final viewModel = ref.read(fileExplorerViewModelProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.deviceName ?? 'File Explorer'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () async {
            await viewModel.closeVolume();
            if (context.mounted) Navigator.of(context).pop();
          },
        ),
      ),
      body: _buildBody(context, state, viewModel),
    );
  }

  Widget _buildBody(
    BuildContext context,
    FileExplorerState state,
    FileExplorerViewModel viewModel,
  ) {
    if (state.volumeId == null && !state.isLoading && state.errorMessage == null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text(
              'Opening volume...',
              style: Theme.of(context).textTheme.bodyLarge,
            ),
          ],
        ),
      );
    }

    if (state.errorMessage != null && state.volumeId == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.error_outline,
                size: 48,
                color: Theme.of(context).colorScheme.error,
              ),
              const SizedBox(height: 16),
              Text(
                state.errorMessage!,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge,
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('Back'),
              ),
            ],
          ),
        ),
      );
    }

    if (state.fileContent != null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Material(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back),
                    onPressed: viewModel.closeFileViewer,
                  ),
                  Text(
                    'File content',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ],
              ),
            ),
          ),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: SelectableText(
                state.fileContent!,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      fontFamily: 'monospace',
                    ),
              ),
            ),
          ),
        ],
      );
    }

    return Column(
      children: [
        if (state.currentPath.isNotEmpty)
          Material(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: ListTile(
              leading: const Icon(Icons.arrow_upward),
              title: const Text('..'),
              onTap: () => viewModel.navigateUp(),
            ),
          ),
        if (state.isLoading && state.entries.isEmpty)
          const Expanded(
            child: Center(child: CircularProgressIndicator()),
          )
        else if (state.errorMessage != null)
          Expanded(
            child: Center(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.error_outline,
                      size: 48,
                      color: Theme.of(context).colorScheme.error,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      state.errorMessage!,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                  ],
                ),
              ),
            ),
          )
        else
          Expanded(
            child: ListView.builder(
              itemCount: state.entries.length,
              itemBuilder: (context, index) {
                final entry = state.entries[index];
                return ListTile(
                  leading: Icon(
                    entry.isDirectory ? Icons.folder : Icons.insert_drive_file,
                    color: entry.isDirectory
                        ? Theme.of(context).colorScheme.primary
                        : Theme.of(context).colorScheme.outline,
                  ),
                  title: Text(entry.name),
                  subtitle: entry.isDirectory
                      ? null
                      : Text(_formatSize(entry.size)),
                  onTap: () {
                    if (entry.isDirectory) {
                      viewModel.navigateTo(entry.name);
                    } else {
                      _showFileOptions(context, viewModel, entry.name);
                    }
                  },
                );
              },
            ),
          ),
      ],
    );
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  void _showFileOptions(
    BuildContext context,
    FileExplorerViewModel viewModel,
    String fileName,
  ) {
    showModalBottomSheet<void>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.visibility),
              title: const Text('Anzeigen'),
              subtitle: const Text('Datei in der App anzeigen'),
              onTap: () {
                Navigator.pop(ctx);
                viewModel.openFile(fileName);
              },
            ),
            ListTile(
              leading: const Icon(Icons.open_in_new),
              title: const Text('In anderer App öffnen'),
              subtitle: const Text('Mit Standard-App oder Auswahl öffnen'),
              onTap: () async {
                Navigator.pop(ctx);
                final scaffold = ScaffoldMessenger.of(context);
                final ok = await viewModel.openFileInOtherApp(fileName);
                if (context.mounted) {
                  scaffold.showSnackBar(
                    SnackBar(
                      content: Text(
                        ok
                            ? 'Datei wird in anderer App geöffnet'
                            : 'Öffnen fehlgeschlagen',
                      ),
                    ),
                  );
                }
              },
            ),
            ListTile(
              leading: const Icon(Icons.save_alt),
              title: const Text('Auf Gerät speichern'),
              subtitle: const Text('Speicherort wählen (wie bei Logs)'),
              onTap: () async {
                Navigator.pop(ctx);
                final scaffold = ScaffoldMessenger.of(context);
                final ok = await viewModel.saveFileToDevice(fileName);
                if (context.mounted) {
                  scaffold.showSnackBar(
                    SnackBar(
                      content: Text(
                        ok
                            ? 'Datei gespeichert'
                            : 'Speichern abgebrochen oder fehlgeschlagen',
                      ),
                    ),
                  );
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}
