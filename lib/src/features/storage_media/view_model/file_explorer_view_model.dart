import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../logging/services/logging_service.dart';
import '../data/usb_directory_entry.dart';
import '../services/file_open_save_service.dart';
import '../services/usb_volume_service.dart';
import '../data/usb_volume_repository.dart';
import '../domain/list_usb_directory_use_case.dart';
import '../domain/read_usb_file_use_case.dart';
import 'usb_devices_view_model.dart';

/// UI state for the file explorer.
class FileExplorerState {
  const FileExplorerState({
    this.isLoading = false,
    this.entries = const [],
    this.currentPath = '',
    this.volumeId,
    this.errorMessage,
    this.fileContent,
  });

  final bool isLoading;
  final List<UsbDirectoryEntry> entries;
  final String currentPath;
  final int? volumeId;
  final String? errorMessage;
  final String? fileContent;

  static const FileExplorerState initial = FileExplorerState();
}

final usbVolumeServiceProvider = Provider<UsbVolumeService>((ref) => UsbVolumeService());

final usbVolumeRepositoryProvider = Provider<UsbVolumeRepository>((ref) {
  return UsbVolumeRepository(
    ref.watch(usbBridgeProvider),
    ref.watch(usbVolumeServiceProvider),
  );
});

final listUsbDirectoryUseCaseProvider = Provider<ListUsbDirectoryUseCase>((ref) {
  return ListUsbDirectoryUseCase(ref.watch(usbVolumeRepositoryProvider));
});

final readUsbFileUseCaseProvider = Provider<ReadUsbFileUseCase>((ref) {
  return ReadUsbFileUseCase(ref.watch(usbVolumeRepositoryProvider));
});

final fileOpenSaveServiceProvider = Provider<FileOpenSaveService>((ref) {
  return FileOpenSaveService(ref.watch(usbVolumeRepositoryProvider));
});

final fileExplorerViewModelProvider =
    NotifierProvider<FileExplorerViewModel, FileExplorerState>(FileExplorerViewModel.new);

class FileExplorerViewModel extends Notifier<FileExplorerState> {
  @override
  FileExplorerState build() => FileExplorerState.initial;

  /// Opens the volume and loads root directory.
  Future<void> openVolume(String deviceId) async {
    state = FileExplorerState(
      isLoading: true,
      currentPath: '',
      errorMessage: null,
    );

    final repository = ref.read(usbVolumeRepositoryProvider);
    final listUseCase = ref.read(listUsbDirectoryUseCaseProvider);

    try {
      final volumeId = await repository.openVolume(deviceId);
      final entries = await listUseCase.call(volumeId, path: '');

      state = FileExplorerState(
        volumeId: volumeId,
        entries: entries,
        currentPath: '',
        errorMessage: null,
      );
    } on UsbVolumeRepositoryException catch (e) {
      LoggingService.instance.e('FileExplorerViewModel', 'openVolume: ${e.message}');
      state = FileExplorerState(errorMessage: e.message);
    } catch (e, st) {
      LoggingService.instance.e('FileExplorerViewModel', 'openVolume: ${e.toString()}\n$st');
      state = FileExplorerState(errorMessage: e.toString());
    }
  }

  /// Navigates into a directory.
  Future<void> navigateTo(String name) async {
    final volumeId = state.volumeId;
    if (volumeId == null) return;

    final newPath = state.currentPath.isEmpty
        ? name
        : '${state.currentPath}/$name';

    state = FileExplorerState(
      volumeId: volumeId,
      currentPath: newPath,
      entries: state.entries,
      isLoading: true,
    );

    final listUseCase = ref.read(listUsbDirectoryUseCaseProvider);

    try {
      final entries = await listUseCase.call(volumeId, path: newPath);
      state = FileExplorerState(
        volumeId: volumeId,
        currentPath: newPath,
        entries: entries,
        errorMessage: null,
      );
    } on UsbVolumeRepositoryException catch (e) {
      LoggingService.instance.e('FileExplorerViewModel', 'navigateTo: ${e.message}');
      state = FileExplorerState(
        volumeId: volumeId,
        currentPath: state.currentPath,
        entries: state.entries,
        errorMessage: e.message,
      );
    } catch (e, st) {
      LoggingService.instance.e('FileExplorerViewModel', 'navigateTo: ${e.toString()}\n$st');
      state = FileExplorerState(
        volumeId: volumeId,
        currentPath: state.currentPath,
        entries: state.entries,
        errorMessage: e.toString(),
      );
    }
  }

  /// Navigates up one level.
  Future<void> navigateUp() async {
    final volumeId = state.volumeId;
    if (volumeId == null) return;

    final parts = state.currentPath.split('/').where((s) => s.isNotEmpty).toList();
    if (parts.isEmpty) return;

    parts.removeLast();
    final newPath = parts.join('/');

    state = FileExplorerState(
      volumeId: volumeId,
      currentPath: newPath,
      entries: state.entries,
      isLoading: true,
    );

    final listUseCase = ref.read(listUsbDirectoryUseCaseProvider);

    try {
      final entries = await listUseCase.call(volumeId, path: newPath);
      state = FileExplorerState(
        volumeId: volumeId,
        currentPath: newPath,
        entries: entries,
        errorMessage: null,
      );
    } catch (e, st) {
      LoggingService.instance.e('FileExplorerViewModel', 'navigateUp: ${e.toString()}\n$st');
      state = FileExplorerState(
        volumeId: volumeId,
        currentPath: newPath,
        entries: const [],
        errorMessage: e.toString(),
      );
    }
  }

  /// Opens a file and loads its content for display.
  Future<void> openFile(String name) async {
    final volumeId = state.volumeId;
    if (volumeId == null) return;

    final path = state.currentPath.isEmpty ? name : '${state.currentPath}/$name';

    state = FileExplorerState(
      volumeId: volumeId,
      currentPath: state.currentPath,
      entries: state.entries,
      isLoading: true,
    );

    final readUseCase = ref.read(readUsbFileUseCaseProvider);

    try {
      final bytes = await readUseCase.call(volumeId, path, length: 256 * 1024);
      final content = String.fromCharCodes(bytes);
      state = FileExplorerState(
        volumeId: volumeId,
        currentPath: state.currentPath,
        entries: state.entries,
        fileContent: content,
        errorMessage: null,
      );
    } on UsbVolumeRepositoryException catch (e) {
      LoggingService.instance.e('FileExplorerViewModel', 'openFile: ${e.message}');
      state = FileExplorerState(
        volumeId: volumeId,
        currentPath: state.currentPath,
        entries: state.entries,
        errorMessage: e.message,
      );
    } catch (e, st) {
      LoggingService.instance.e('FileExplorerViewModel', 'openFile: ${e.toString()}\n$st');
      state = FileExplorerState(
        volumeId: volumeId,
        currentPath: state.currentPath,
        entries: state.entries,
        errorMessage: e.toString(),
      );
    }
  }

  /// Opens the file in an external app (system chooser).
  Future<bool> openFileInOtherApp(String name) async {
    final volumeId = state.volumeId;
    if (volumeId == null) return false;
    final path = state.currentPath.isEmpty ? name : '${state.currentPath}/$name';
    final service = ref.read(fileOpenSaveServiceProvider);
    return service.openInOtherApp(volumeId: volumeId, path: path, fileName: name);
  }

  /// Saves the file to device storage via SAF (user picks location).
  Future<bool> saveFileToDevice(String name) async {
    final volumeId = state.volumeId;
    if (volumeId == null) return false;
    final path = state.currentPath.isEmpty ? name : '${state.currentPath}/$name';
    final service = ref.read(fileOpenSaveServiceProvider);
    return service.saveToDevice(volumeId: volumeId, path: path, fileName: name);
  }

  /// Closes the file viewer.
  void closeFileViewer() {
    state = FileExplorerState(
      volumeId: state.volumeId,
      currentPath: state.currentPath,
      entries: state.entries,
      fileContent: null,
    );
  }

  /// Closes the volume and resets state.
  Future<void> closeVolume() async {
    final volumeId = state.volumeId;
    if (volumeId != null) {
      final repository = ref.read(usbVolumeRepositoryProvider);
      try {
        await repository.closeVolume(volumeId);
      } catch (e, st) {
        LoggingService.instance.e('FileExplorerViewModel', 'closeVolume: ${e.toString()}\n$st');
      }
    }
    state = FileExplorerState.initial;
  }
}
