import '../services/logging_service.dart';

/// Use case: clear all logs from the database.
class ClearLogsUseCase {
  ClearLogsUseCase(this._service);

  final LoggingService _service;

  Future<void> call() => _service.clear();
}
