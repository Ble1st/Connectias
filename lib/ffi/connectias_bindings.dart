/// Connectias FFI Bindings – Dart ↔ Rust Bridge
///
/// Sichere FFI-Schnittstelle zur connectias_ffi.so Library
///
/// SICHERHEIT:
library;

import 'dart:ffi' as ffi;
import 'dart:io';
import 'package:ffi/ffi.dart' as ffi_pkg;

/// FFI Error Codes (müssen mit Rust synchron sein)
class FFIErrorCode {
  static const int success = 0;
  static const int invalidUtf8 = -1;
  static const int nullPointer = -2;
  static const int initFailed = -3;
  static const int pluginNotFound = -4;
  static const int executionFailed = -5;
  static const int securityViolation = -6;
  static const int lockPoisoned = -7;
}

/// Lade die Native Library
final ffi.DynamicLibrary _nativeLib = _loadNativeLib();

ffi.DynamicLibrary _loadNativeLib() {
  if (Platform.isAndroid) {
    return ffi.DynamicLibrary.open('libconnectias_ffi.so');
  } else if (Platform.isLinux) {
    return ffi.DynamicLibrary.open('libconnectias_ffi.so');
  } else if (Platform.isMacOS) {
    return ffi.DynamicLibrary.open('libconnectias_ffi.dylib');
  } else if (Platform.isWindows) {
    return ffi.DynamicLibrary.open('connectias_ffi.dll');
  } else {
    throw UnsupportedError(
      'Platform ${Platform.operatingSystem} nicht unterstützt',
    );
  }
}

// ============================================================================
// FFI TYPE DEFINITIONS
// ============================================================================

/// connectias_init() -> i32
typedef _InitNative = ffi.Int32 Function();
typedef _InitDart = int Function();

/// connectias_version() -> const char*
typedef _VersionNative = ffi.Pointer<ffi.Char> Function();
typedef _VersionDart = ffi.Pointer<ffi.Char> Function();

/// connectias_get_system_info() -> const char*
typedef _GetSystemInfoNative = ffi.Pointer<ffi.Char> Function();
typedef _GetSystemInfoDart = ffi.Pointer<ffi.Char> Function();

/// connectias_load_plugin(const char* path) -> const char*
typedef _LoadPluginNative =
    ffi.Pointer<ffi.Char> Function(ffi.Pointer<ffi.Char>);
typedef _LoadPluginDart = ffi.Pointer<ffi.Char> Function(ffi.Pointer<ffi.Char>);

/// connectias_unload_plugin(const char* id) -> i32
typedef _UnloadPluginNative = ffi.Int32 Function(ffi.Pointer<ffi.Char>);
typedef _UnloadPluginDart = int Function(ffi.Pointer<ffi.Char>);

/// connectias_execute_plugin(id, cmd, args, output) -> i32
typedef _ExecutePluginNative =
    ffi.Int32 Function(
      ffi.Pointer<ffi.Char>,
      ffi.Pointer<ffi.Char>,
      ffi.Pointer<ffi.Char>,
      ffi.Pointer<ffi.Pointer<ffi.Char>>,
    );
typedef _ExecutePluginDart =
    int Function(
      ffi.Pointer<ffi.Char>,
      ffi.Pointer<ffi.Char>,
      ffi.Pointer<ffi.Char>,
      ffi.Pointer<ffi.Pointer<ffi.Char>>,
    );

/// connectias_list_plugins() -> const char*
typedef _ListPluginsNative = ffi.Pointer<ffi.Char> Function();
typedef _ListPluginsDart = ffi.Pointer<ffi.Char> Function();

/// connectias_rasp_check_environment() -> i32
typedef _RaspCheckEnvNative = ffi.Int32 Function();
typedef _RaspCheckEnvDart = int Function();

/// connectias_rasp_check_root() -> i32
typedef _RaspCheckRootNative = ffi.Int32 Function();
typedef _RaspCheckRootDart = int Function();

/// connectias_rasp_check_debugger() -> i32
typedef _RaspCheckDebuggerNative = ffi.Int32 Function();
typedef _RaspCheckDebuggerDart = int Function();

/// connectias_rasp_check_emulator() -> i32
typedef _RaspCheckEmulatorNative = ffi.Int32 Function();
typedef _RaspCheckEmulatorDart = int Function();

/// connectias_rasp_check_tamper() -> i32
typedef _RaspCheckTamperNative = ffi.Int32 Function();
typedef _RaspCheckTamperDart = int Function();

/// connectias_get_last_error() -> const char*
typedef _GetLastErrorNative = ffi.Pointer<ffi.Char> Function();
typedef _GetLastErrorDart = ffi.Pointer<ffi.Char> Function();

/// connectias_free_string(const char* s) -> void
typedef _FreeStringNative = ffi.Void Function(ffi.Pointer<ffi.Char>);
typedef _FreeStringDart = void Function(ffi.Pointer<ffi.Char>);

// ============================================================================
// FUNCTION LOOKUPS
// ============================================================================

final _init = _nativeLib.lookupFunction<_InitNative, _InitDart>(
  'connectias_init',
);
final _version = _nativeLib.lookupFunction<_VersionNative, _VersionDart>(
  'connectias_version',
);
final _getSystemInfo = _nativeLib
    .lookupFunction<_GetSystemInfoNative, _GetSystemInfoDart>(
      'connectias_get_system_info',
    );
final _loadPlugin = _nativeLib
    .lookupFunction<_LoadPluginNative, _LoadPluginDart>(
      'connectias_load_plugin',
    );
final _unloadPlugin = _nativeLib
    .lookupFunction<_UnloadPluginNative, _UnloadPluginDart>(
      'connectias_unload_plugin',
    );
final _executePlugin = _nativeLib
    .lookupFunction<_ExecutePluginNative, _ExecutePluginDart>(
      'connectias_execute_plugin',
    );
final _listPlugins = _nativeLib
    .lookupFunction<_ListPluginsNative, _ListPluginsDart>(
      'connectias_list_plugins',
    );
final _raspCheckEnv = _nativeLib
    .lookupFunction<_RaspCheckEnvNative, _RaspCheckEnvDart>(
      'connectias_rasp_check_environment',
    );
final _raspCheckRoot = _nativeLib
    .lookupFunction<_RaspCheckRootNative, _RaspCheckRootDart>(
      'connectias_rasp_check_root',
    );
final _raspCheckDebugger = _nativeLib
    .lookupFunction<_RaspCheckDebuggerNative, _RaspCheckDebuggerDart>(
      'connectias_rasp_check_debugger',
    );
final _raspCheckEmulator = _nativeLib
    .lookupFunction<_RaspCheckEmulatorNative, _RaspCheckEmulatorDart>(
      'connectias_rasp_check_emulator',
    );
final _raspCheckTamper = _nativeLib
    .lookupFunction<_RaspCheckTamperNative, _RaspCheckTamperDart>(
      'connectias_rasp_check_tamper',
    );
final _getLastError = _nativeLib
    .lookupFunction<_GetLastErrorNative, _GetLastErrorDart>(
      'connectias_get_last_error',
    );
final _freeString = _nativeLib
    .lookupFunction<_FreeStringNative, _FreeStringDart>(
      'connectias_free_string',
    );

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/// Konvertiere Dart String zu C String
ffi.Pointer<ffi.Char> _stringToUtf8(String s) {
  return s.toNativeUtf8().cast<ffi.Char>();
}

/// Konvertiere C String zu Dart String
String _utf8ToString(ffi.Pointer<ffi.Char> ptr) {
  if (ptr == ffi.nullptr) {
    return '';
  }
  return ptr.cast<ffi_pkg.Utf8>().toDartString();
}

/// Freigabe eines C Strings (MUSS aufgerufen werden!)
void freeString(ffi.Pointer<ffi.Char> ptr) {
  if (ptr != ffi.nullptr) {
    _freeString(ptr);
  }
}

/// Hole den letzten Fehler-String
String getLastError() {
  final errorPtr = _getLastError();
  if (errorPtr == ffi.nullptr) {
    return '';
  }
  final error = _utf8ToString(errorPtr);
  freeString(errorPtr);
  return error;
}

/// Exception für FFI Fehler
class ConnectiasFFIException implements Exception {
  final int errorCode;
  final String message;

  ConnectiasFFIException(this.errorCode, this.message);

  @override
  String toString() => 'ConnectiasFFIException ($errorCode): $message';

  String get description {
    switch (errorCode) {
      case FFIErrorCode.invalidUtf8:
        return 'Ungültige UTF-8 Sequenz';
      case FFIErrorCode.nullPointer:
        return 'Null Pointer';
      case FFIErrorCode.initFailed:
        return 'Initialisierung fehlgeschlagen';
      case FFIErrorCode.pluginNotFound:
        return 'Plugin nicht gefunden';
      case FFIErrorCode.executionFailed:
        return 'Ausführung fehlgeschlagen';
      case FFIErrorCode.securityViolation:
        return 'Sicherheitsverletzung';
      case FFIErrorCode.lockPoisoned:
        return 'Lock vergiftet';
      default:
        return 'Unbekannter Fehler: $errorCode';
    }
  }
}

// ============================================================================
// PUBLIC FFI INTERFACE
// ============================================================================

/// Initialisiere die FFI Bridge
///
/// MUSS einmalig vor anderen Funktionen aufgerufen werden
Future<void> init() async {
  final result = _init();
  if (result != FFIErrorCode.success) {
    throw ConnectiasFFIException(result, 'FFI Initialisierung fehlgeschlagen');
  }
}

/// Gib die FFI Bridge Version zurück
String getVersion() {
  final versionPtr = _version();
  final version = _utf8ToString(versionPtr);
  freeString(versionPtr);
  return version;
}

/// Gib Systeminfo aus
String getSystemInfo() {
  final infoPtr = _getSystemInfo();
  final info = _utf8ToString(infoPtr);
  freeString(infoPtr);
  return info;
}

/// Lade ein Plugin
///
/// @param pluginPath Pfad zum Plugin WASM File
/// @return Plugin-ID
/// @throws ConnectiasFFIException bei Fehler
Future<String> loadPlugin(String pluginPath) async {
  final pathPtr = _stringToUtf8(pluginPath);
  try {
    final idPtr = _loadPlugin(pathPtr);
    if (idPtr == ffi.nullptr) {
      final error = getLastError();
      throw ConnectiasFFIException(FFIErrorCode.executionFailed, error);
    }
    final pluginId = _utf8ToString(idPtr);
    freeString(idPtr);
    return pluginId;
  } finally {
    ffi_pkg.malloc.free(pathPtr);
  }
}

/// Entlade ein Plugin
///
/// @param pluginId Eindeutige Plugin-ID
/// @throws ConnectiasFFIException bei Fehler
Future<void> unloadPlugin(String pluginId) async {
  final idPtr = _stringToUtf8(pluginId);
  try {
    final result = _unloadPlugin(idPtr);
    if (result != FFIErrorCode.success) {
      throw ConnectiasFFIException(result, getLastError());
    }
  } finally {
    ffi_pkg.malloc.free(idPtr);
  }
}

/// Führe ein Plugin aus
///
/// @param pluginId Eindeutige Plugin-ID
/// @param command Command Name
/// @param argsJson JSON mit Arguments
/// @return Ergebnis als String
/// @throws ConnectiasFFIException bei Fehler
Future<String> executePlugin(
  String pluginId,
  String command,
  Map<String, String> args,
) async {
  final idPtr = _stringToUtf8(pluginId);
  final cmdPtr = _stringToUtf8(command);

  // Konvertiere Map zu JSON
  final argsJson = args.entries.map((e) => '"${e.key}":"${e.value}"').join(',');
  final argsJsonStr = '{$argsJson}';
  final argsPtr = _stringToUtf8(argsJsonStr);

  try {
    // Allokiere Output Pointer
    final outputPtr = ffi_pkg.malloc<ffi.Pointer<ffi.Char>>();

    final result = _executePlugin(idPtr, cmdPtr, argsPtr, outputPtr);
    if (result != FFIErrorCode.success) {
      ffi_pkg.malloc.free(outputPtr);
      throw ConnectiasFFIException(result, getLastError());
    }

    final output = _utf8ToString(outputPtr.value);
    freeString(outputPtr.value);
    ffi_pkg.malloc.free(outputPtr);

    return output;
  } finally {
    ffi_pkg.malloc.free(idPtr);
    ffi_pkg.malloc.free(cmdPtr);
    ffi_pkg.malloc.free(argsPtr);
  }
}

/// Liste alle geladenen Plugins auf
///
/// @return JSON Array mit Plugin-Informationen
Future<List<String>> listPlugins() async {
  final jsonPtr = _listPlugins();
  if (jsonPtr == ffi.nullptr) {
    return [];
  }

  try {
    // Parse JSON (vereinfacht)
    // jsonStr wurde entfernt, da nicht verwendet
    final plugins = <String>[];
    // TODO: Proper JSON parsing
    return plugins;
  } finally {
    freeString(jsonPtr);
  }
}

// ============================================================================
// SECURITY (RASP)
// ============================================================================

/// Führe vollständigen RASP-Security Check durch
///
/// KRITISCH: Bei Rückgabe > 0 MUSS die App sofort beendet werden!
///
/// @return 0 = sicher, > 0 = gefährdet, < 0 = Fehler
Future<int> raspCheckEnvironment() async {
  return _raspCheckEnv();
}

/// Prüfe auf Root/Super-User Zugriff
///
/// @return 0 = safe, 1 = suspicious, 2 = compromised
Future<int> raspCheckRoot() async {
  return _raspCheckRoot();
}

/// Prüfe auf Debugger
///
/// @return 0 = safe, 1 = suspicious, 2 = compromised
Future<int> raspCheckDebugger() async {
  return _raspCheckDebugger();
}

/// Prüfe auf Emulator/Virtualisierung
///
/// @return 0 = safe, 1 = suspicious, 2 = compromised
Future<int> raspCheckEmulator() async {
  return _raspCheckEmulator();
}

/// Prüfe auf Tamper/Manipulation
///
/// @return 0 = safe, 1 = suspicious, 2 = compromised
Future<int> raspCheckTamper() async {
  return _raspCheckTamper();
}

/// Prüfe alle RASP-Vektoren
class RaspStatus {
  final int root;
  final int debugger;
  final int emulator;
  final int tamper;

  RaspStatus({
    required this.root,
    required this.debugger,
    required this.emulator,
    required this.tamper,
  });

  bool get isSafe => root == 0 && debugger == 0 && emulator == 0 && tamper == 0;

  bool get isCompromised =>
      root == 2 || debugger == 2 || emulator == 2 || tamper == 2;

  bool get isSuspicious => !isSafe && !isCompromised;

  @override
  String toString() =>
      '''
RaspStatus(
  root: $root,
  debugger: $debugger,
  emulator: $emulator,
  tamper: $tamper,
  safe: $isSafe,
  suspicious: $isSuspicious,
  compromised: $isCompromised,
)''';
}

/// Hole vollständigen RASP-Status
Future<RaspStatus> getRaspStatus() async {
  return RaspStatus(
    root: await raspCheckRoot(),
    debugger: await raspCheckDebugger(),
    emulator: await raspCheckEmulator(),
    tamper: await raspCheckTamper(),
  );
}
