# WASM Plugin System Documentation

## Overview

The WASM Plugin System allows loading and executing WebAssembly (WASM) plugins in the Connectias Android app. Plugins run in isolated threads with resource limits and security sandboxing.

## Architecture

### Components

1. **WasmRuntime**: Wrapper around WASM runtime (currently mock implementation, can be replaced with real runtime)
2. **PluginManager**: Manages plugin lifecycle (load, execute, unload)
3. **PluginExecutor**: Provides thread isolation for plugin execution
4. **ResourceMonitor**: Monitors and enforces resource limits (memory, CPU, execution time)
5. **PluginSignatureVerifier**: Verifies plugin signatures using RSA PKCS#1 v1.5
6. **PluginZipParser**: Parses plugin ZIP files and extracts metadata

### Plugin Lifecycle

1. **Load**: Plugin ZIP file is parsed, signature verified, WASM module loaded
2. **Init**: Plugin store is created with resource limits
3. **Execute**: Plugin commands are executed in isolated threads
4. **Unload**: Plugin resources are cleaned up

## Plugin Format

Plugins are distributed as ZIP files containing:

- `plugin.json`: Plugin metadata (ID, name, version, permissions, resource limits)
- `main.wasm`: WASM module bytecode (or custom entry point)
- `META-INF/SIGNATURE.RSA`: Base64-encoded RSA signature (optional)

### plugin.json Format

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "author": "Author Name",
  "description": "Plugin description",
  "permissions": ["storage.read", "network.access"],
  "resourceLimits": {
    "maxMemory": 104857600,
    "maxExecutionTimeSeconds": 30,
    "maxFuel": 1000000
  },
  "entryPoint": "main.wasm",
  "dependencies": []
}
```

## Security

### Signature Verification

Plugins can be signed using RSA PKCS#1 v1.5 with SHA-256. The signature is verified against a public key before loading.

### Resource Limits

- **Memory**: Default 100MB per plugin
- **Execution Time**: Default 30 seconds per execution
- **Fuel**: Default 1M fuel units (CPU limit)

### Thread Isolation

Each plugin runs in its own thread pool with isolated execution context, preventing interference between plugins.

## API Usage

### Loading a Plugin

```kotlin
val pluginManager: PluginManager = // injected via Hilt
val zipFile = File("/path/to/plugin.zip")
val publicKey: PublicKey = // load from resources or secure storage

val plugin = pluginManager.loadPlugin(zipFile, publicKey)
```

### Executing a Plugin

```kotlin
val result = pluginManager.executePlugin(
    pluginId = "my-plugin",
    command = "hello",
    args = mapOf("name" to "World")
)
```

### Unloading a Plugin

```kotlin
pluginManager.unloadPlugin("my-plugin")
```

## UI Integration

The `PluginManagerScreen` Composable provides a UI for managing plugins:

- List of loaded plugins
- Load new plugin button
- Execute plugin commands
- Unload plugins

## Testing

Unit tests are provided for:
- WasmRuntime: Module loading and function execution
- PluginManager: Lifecycle management
- ResourceMonitor: Limit enforcement
- PluginSignatureVerifier: Signature verification

## Future Improvements

1. Replace mock WASM runtime with real implementation (Wasmtime via JNI or alternative)
2. Add plugin marketplace/store
3. Support plugin dependencies
4. Add plugin hot-reload capability
5. Improve resource monitoring accuracy

## Troubleshooting

### Plugin fails to load
- Check plugin.json format
- Verify signature (if required)
- Check WASM module validity

### Plugin execution fails
- Check resource limits
- Verify plugin function names
- Check plugin status (must be READY)

### Memory issues
- Reduce plugin resource limits
- Unload unused plugins
- Check for memory leaks in plugins

