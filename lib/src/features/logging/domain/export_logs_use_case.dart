import '../services/logging_service.dart';

/// Use case: export logs to a TXT file via Android SAF.
class ExportLogsUseCase {
  ExportLogsUseCase(this._service);

  final LoggingService _service;

  Future<bool> call() => _service.exportToFile();
}
