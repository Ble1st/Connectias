/// Secure Storage Service – Vereinfachte Implementierung
/// 
/// Lokale Datei-basierte Verschlüsselung ohne externe Dependencies
library;

import 'package:flutter/foundation.dart';
import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';

/// Vereinfachte Storage Service
class SecureStorageService {
  static final SecureStorageService _instance = SecureStorageService._internal();

  factory SecureStorageService() {
    return _instance;
  }

  SecureStorageService._internal();

  // =========================================================================
  // PUBLIC API
  // =========================================================================

  /// Speichere einen verschlüsselten Wert
  Future<void> write(String key, String value) async {
    try {
      final encrypted = _encrypt(value);
      final file = await _getStorageFile();
      final data = await _readFileData(file);
      data[key] = encrypted;
      await _writeFileData(file, data);
      debugPrint('✅ SecureStorage: $key gespeichert');
    } catch (e) {
      debugPrint('❌ SecureStorage Fehler: $e');
      rethrow;
    }
  }

  /// Lese einen entschlüsselten Wert
  Future<String?> read(String key) async {
    try {
      final file = await _getStorageFile();
      final data = await _readFileData(file);
      final encrypted = data[key];
      if (encrypted == null) return null;
      return _decrypt(encrypted);
    } catch (e) {
      debugPrint('❌ SecureStorage Fehler: $e');
      return null;
    }
  }

  /// Lösche einen Wert
  Future<void> delete(String key) async {
    try {
      final file = await _getStorageFile();
      final data = await _readFileData(file);
      data.remove(key);
      await _writeFileData(file, data);
      debugPrint('✅ SecureStorage: $key gelöscht');
    } catch (e) {
      debugPrint('❌ SecureStorage Fehler: $e');
      rethrow;
    }
  }

  /// Lösche alle Werte
  Future<void> deleteAll() async {
    try {
      final file = await _getStorageFile();
      if (await file.exists()) {
        await file.delete();
      }
      debugPrint('✅ SecureStorage: Alle Daten gelöscht');
    } catch (e) {
      debugPrint('❌ SecureStorage Fehler: $e');
      rethrow;
    }
  }

  /// Alias für deleteAll
  Future<void> clear() => deleteAll();

  /// Alias für write
  Future<void> saveSecure(String key, String value) => write(key, value);

  /// Alias für read
  Future<String?> getSecure(String key) => read(key);

  /// Alias für delete
  Future<void> deleteSecure(String key) => delete(key);

  /// Alias für containsKey
  Future<bool> hasKey(String key) => containsKey(key);

  /// Speichere JSON
  Future<void> saveJson(String key, Map<String, dynamic> data) async {
    final jsonStr = jsonEncode(data);
    await write(key, jsonStr);
  }

  /// Lese JSON
  Future<T?> getJson<T>(String key, T Function(Map<String, dynamic>) parser) async {
    final jsonStr = await read(key);
    if (jsonStr == null) return null;
    try {
      final data = jsonDecode(jsonStr) as Map<String, dynamic>;
      return parser(data);
    } catch (e) {
      debugPrint('❌ JSON Parse Fehler: $e');
      return null;
    }
  }

  /// Prüfe ob ein Key existiert
  Future<bool> containsKey(String key) async {
    try {
      final file = await _getStorageFile();
      final data = await _readFileData(file);
      return data.containsKey(key);
    } catch (e) {
      debugPrint('❌ SecureStorage Fehler: $e');
      return false;
    }
  }

  /// Liste alle Keys auf
  Future<List<String>> getAllKeys() async {
    try {
      final file = await _getStorageFile();
      final data = await _readFileData(file);
      return data.keys.toList();
    } catch (e) {
      debugPrint('❌ SecureStorage Fehler: $e');
      return [];
    }
  }

  // =========================================================================
  // PRIVATE METHODS
  // =========================================================================

  /// Verschlüssele einen String
  String _encrypt(String value) {
    final bytes = utf8.encode(value);
    final key = _getEncryptionKey();
    final encrypted = _xorEncrypt(bytes, key);
    return base64.encode(encrypted);
  }

  /// Entschlüssele einen String
  String _decrypt(String encrypted) {
    final bytes = base64.decode(encrypted);
    final key = _getEncryptionKey();
    final decrypted = _xorEncrypt(bytes, key);
    return utf8.decode(decrypted);
  }

  /// XOR-Verschlüsselung (einfach aber funktional)
  List<int> _xorEncrypt(List<int> data, List<int> key) {
    final result = <int>[];
    for (int i = 0; i < data.length; i++) {
      result.add(data[i] ^ key[i % key.length]);
    }
    return result;
  }

  /// Generiere einen Verschlüsselungsschlüssel
  List<int> _getEncryptionKey() {
    // Einfacher aber deterministischer Schlüssel
    final deviceId = Platform.isAndroid ? 'android' : 'ios';
    final keyString = 'connectias_secure_storage_$deviceId';
    return utf8.encode(keyString).take(16).toList();
  }

  /// Hole die Storage-Datei
  Future<File> _getStorageFile() async {
    final directory = await getApplicationDocumentsDirectory();
    return File('${directory.path}/connectias_secure_storage.json');
  }

  /// Lese Daten aus der Datei
  Future<Map<String, String>> _readFileData(File file) async {
    try {
      if (!await file.exists()) {
        return {};
      }
      final content = await file.readAsString();
      final data = json.decode(content) as Map<String, dynamic>;
      return data.map((key, value) => MapEntry(key, value.toString()));
    } catch (e) {
      debugPrint('⚠️ Fehler beim Lesen der Storage-Datei: $e');
      return {};
    }
  }

  /// Schreibe Daten in die Datei
  Future<void> _writeFileData(File file, Map<String, String> data) async {
    try {
      final content = json.encode(data);
      await file.writeAsString(content);
    } catch (e) {
      debugPrint('❌ Fehler beim Schreiben der Storage-Datei: $e');
      rethrow;
    }
  }
}

// ============================================================================
// SINGLETON ACCESS
// ============================================================================

/// Globale SecureStorageService Instance
final secureStorageService = SecureStorageService();//ich diene der aktualisierung wala
