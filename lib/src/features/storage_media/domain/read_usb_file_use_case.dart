import '../data/usb_volume_repository.dart';

/// Use case: read file content from an NTFS USB volume.
class ReadUsbFileUseCase {
  ReadUsbFileUseCase(this._repository);

  final UsbVolumeRepository _repository;

  Future<List<int>> call(
    int volumeId,
    String path, {
    int offset = 0,
    int length = 65536,
  }) =>
      _repository.readFile(volumeId, path, offset: offset, length: length);
}
