import '../data/usb_directory_entry.dart';
import '../data/usb_volume_repository.dart';

/// Use case: list directory entries on an NTFS USB volume.
class ListUsbDirectoryUseCase {
  ListUsbDirectoryUseCase(this._repository);

  final UsbVolumeRepository _repository;

  Future<List<UsbDirectoryEntry>> call(int volumeId, {String path = ''}) =>
      _repository.listDirectory(volumeId, path: path);
}
