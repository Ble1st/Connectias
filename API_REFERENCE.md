# Connectias API Reference

## ConnectiasService

High-level service for Plugin Management and Security.

### Initialization

```dart
// Singleton access
final service = connectiasService;

// Initialize
await service.init();

// Check if initialized
if (service.isInitialized) {
  // Ready to use
}
```

### Plugin Management

```dart
// Load plugin
final pluginId = await service.loadPlugin('/path/to/plugin.wasm');

// Execute plugin
final result = await service.executePlugin(
  pluginId,
  'myCommand',
  {'param1': 'value1'},
);

// List all plugins
final plugins = await service.listPlugins();

// Unload plugin
await service.unloadPlugin(pluginId);
```

### Security Operations

```dart
// Check full security status
final status = await service.checkSecurity();
if (status.isCompromised) {
  // Handle compromised device
}

// Individual checks
final rooted = await service.isRooted();
final debugged = await service.isDebugged();
final emulated = await service.isEmulated();
```

### Cleanup

```dart
// Cleanup (required)
await service.dispose();
```

## SecureStorageService

Encrypted storage using Android Keystore / iOS Secure Enclave.

```dart
final storage = secureStorageService;

// Store value
await storage.saveSecure('key', 'value');

// Retrieve value
final value = await storage.getSecure('key');

// Store JSON
await storage.saveJson('settings', {'theme': 'dark'});

// Delete value
await storage.deleteSecure('key');

// Clear all
await storage.clear();
```

## NetworkSecurityService

TLS 1.3 + Certificate Pinning.

```dart
final network = networkSecurityService;

// Create secure client
final client = network.createSecureClient();

// Register policy
network.registerPolicy(
  NetworkSecurityPolicy(
    host: 'api.example.com',
    certificatePins: ['sha256/...'],
  ),
);

// Validate URL
if (await network.validateUrl(Uri.parse('https://api.example.com'))) {
  // URL is valid
}
```

## PermissionService

Role-Based Access Control.

```dart
final permissions = permissionService;

// Grant permissions
final perms = PermissionService.advancedPermissions('plugin1');
await permissions.grantPermissions(perms);

// Check permission
if (permissions.hasPermission('plugin1', PluginPermission.networkAccess)) {
  // Has permission
}

// Revoke permissions
await permissions.revokePermissions('plugin1');

// Audit log
final log = permissions.getAuditLog();
```

---

See ARCHITECTURE.md for detailed system design.
