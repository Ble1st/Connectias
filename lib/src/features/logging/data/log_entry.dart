/// A single log entry stored in the logging database.
class LogEntry {
  const LogEntry({
    required this.id,
    required this.timestamp,
    required this.level,
    required this.tag,
    required this.message,
    required this.source,
  });

  final int id;
  final DateTime timestamp;
  final String level;
  final String tag;
  final String message;
  final String source;

  /// Format as a single line for TXT export.
  String toExportLine() {
    final ts = timestamp.toIso8601String();
    return '[$ts] [$level] [$source] $tag: $message';
  }
}
