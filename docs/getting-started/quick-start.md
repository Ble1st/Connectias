# Quick Start Guide

## First Steps

1. **Initialize the service:**
```dart
final service = ConnectiasService();
await service.init();
```

2. **Check security status:**
```dart
final secure = await service.checkSecurity();
print('Device is secure: ${secure.isSafe}');
```

3. **Load your first plugin:**
```dart
final pluginId = await service.loadPlugin('/path/to/plugin.wasm');
```

4. **Execute the plugin:**
```dart
final result = await service.executePlugin(pluginId, 'hello', {});
print('Result: $result');
```

5. **Cleanup:**
```dart
await service.dispose();
```

## Next Steps

- [Installation Guide](installation.md)
- [First Plugin Tutorial](first-plugin.md)
- [Architecture Overview](../architecture/system-overview.md)
