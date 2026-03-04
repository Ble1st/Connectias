import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import 'package:path_provider/path_provider.dart';

import 'log_entry.dart';

/// Repository for log entries in SQLite.
class LogRepository {
  LogRepository._();
  static final LogRepository _instance = LogRepository._();
  static LogRepository get instance => _instance;

  static Database? _db;

  static const String _table = 'logs';

  Future<Database> get database async {
    if (_db != null) return _db!;
    final dir = await getApplicationDocumentsDirectory();
    final path = join(dir.path, 'connectias_logs.db');
    _db = await openDatabase(
      path,
      version: 1,
      onCreate: (db, _) {
        db.execute('''
          CREATE TABLE $_table (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp INTEGER NOT NULL,
            level TEXT NOT NULL,
            tag TEXT NOT NULL,
            message TEXT NOT NULL,
            source TEXT NOT NULL
          )
        ''');
      },
    );
    return _db!;
  }

  Future<void> insert({
    required DateTime timestamp,
    required String level,
    required String tag,
    required String message,
    required String source,
  }) async {
    final db = await database;
    await db.insert(_table, {
      'timestamp': timestamp.millisecondsSinceEpoch,
      'level': level,
      'tag': tag,
      'message': message,
      'source': source,
    });
  }

  Future<List<LogEntry>> getAll({int? limit}) async {
    final db = await database;
    final maps = await db.query(
      _table,
      orderBy: 'timestamp DESC',
      limit: limit ?? 10000,
    );
    return maps.map((m) => _mapToEntry(m)).toList();
  }

  Future<String> exportAsText() async {
    final db = await database;
    final maps = await db.query(_table, orderBy: 'timestamp ASC');
    final entries = maps.map((m) => _mapToEntry(m)).toList();
    return entries.map((e) => e.toExportLine()).join('\n');
  }

  Future<void> clear() async {
    final db = await database;
    await db.delete(_table);
  }

  LogEntry _mapToEntry(Map<String, dynamic> m) {
    return LogEntry(
      id: m['id'] as int,
      timestamp: DateTime.fromMillisecondsSinceEpoch(m['timestamp'] as int),
      level: m['level'] as String,
      tag: m['tag'] as String,
      message: m['message'] as String,
      source: m['source'] as String,
    );
  }
}
