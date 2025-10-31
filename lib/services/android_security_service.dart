import 'package:flutter/services.dart';

class AndroidSecurityService {
  static const platform = MethodChannel('connectias/security');
  
  Future<SecurityCheckResult> performCheck() async {
    try {
      final result = await platform.invokeMethod('performSecurityCheck');
      
      // Type validation for result
      if (result == null) {
        throw Exception('Security check failed: received null result from platform');
      }
      if (result is! Map) {
        throw Exception('Security check failed: expected Map result, got ${result.runtimeType}');
      }
      
      return SecurityCheckResult.fromMap(result);
    } on PlatformException catch (e) {
      throw Exception('Security check failed: ${e.message}');
    } on ArgumentError catch (e) {
      throw Exception('Security check failed: invalid data format - ${e.message}');
    }
  }
  
  Future<bool> detectRoot() async {
    try {
      final result = await platform.invokeMethod('detectRoot');
      
      // Type validation for result
      if (result == null) {
        throw Exception('Root detection failed: received null result from platform');
      }
      if (result is! bool) {
        throw Exception('Root detection failed: expected bool result, got ${result.runtimeType}');
      }
      
      return result;
    } on PlatformException catch (e) {
      throw Exception('Root detection failed: ${e.message}');
    }
  }
  
  Future<bool> detectDebugger() async {
    try {
      final result = await platform.invokeMethod('detectDebugger');
      
      // Type validation for result
      if (result == null) {
        throw Exception('Debugger detection failed: received null result from platform');
      }
      if (result is! bool) {
        throw Exception('Debugger detection failed: expected bool result, got ${result.runtimeType}');
      }
      
      return result;
    } on PlatformException catch (e) {
      throw Exception('Debugger detection failed: ${e.message}');
    }
  }
  
  Future<bool> detectEmulator() async {
    try {
      final result = await platform.invokeMethod('detectEmulator');
      
      // Type validation for result
      if (result == null) {
        throw Exception('Emulator detection failed: received null result from platform');
      }
      if (result is! bool) {
        throw Exception('Emulator detection failed: expected bool result, got ${result.runtimeType}');
      }
      
      return result;
    } on PlatformException catch (e) {
      throw Exception('Emulator detection failed: ${e.message}');
    }
  }
}

class SecurityCheckResult {
  final bool passed;
  final Map<String, bool> details;
  
  SecurityCheckResult({required this.passed, required this.details});
  
  factory SecurityCheckResult.fromMap(Map<dynamic, dynamic> map) {
    // Type validation for 'passed' field
    if (map['passed'] == null) {
      throw ArgumentError('SecurityCheckResult.fromMap: missing required field "passed"');
    }
    if (map['passed'] is! bool) {
      throw ArgumentError('SecurityCheckResult.fromMap: field "passed" must be a bool, got ${map['passed'].runtimeType}');
    }
    
    // Type validation for 'details' field
    if (map['details'] == null) {
      throw ArgumentError('SecurityCheckResult.fromMap: missing required field "details"');
    }
    if (map['details'] is! Map) {
      throw ArgumentError('SecurityCheckResult.fromMap: field "details" must be a Map, got ${map['details'].runtimeType}');
    }
    
    // Validate details map contents
    final detailsMap = map['details'] as Map;
    final validatedDetails = <String, bool>{};
    
    for (final entry in detailsMap.entries) {
      final key = entry.key;
      final value = entry.value;
      
      if (key is! String) {
        throw ArgumentError('SecurityCheckResult.fromMap: details key must be a String, got ${key.runtimeType}');
      }
      if (value is! bool) {
        throw ArgumentError('SecurityCheckResult.fromMap: details value must be a bool, got ${value.runtimeType}');
      }
      
      validatedDetails[key] = value;
    }
    
    return SecurityCheckResult(
      passed: map['passed'] as bool,
      details: validatedDetails,
    );
  }
}
