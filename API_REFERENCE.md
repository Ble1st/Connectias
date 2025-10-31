# API Reference

Connectias provides comprehensive APIs for both Rust and Dart/Flutter development.

## Quick Links

- [Rust API Documentation](docs/api/rust-api.md) - Core Rust APIs for plugin development
- [Dart API Documentation](docs/api/dart-api.md) - Flutter/Dart APIs for UI development
- [Security Guidelines](docs/security/security-guidelines.md) - Security best practices and policies

## Overview

Connectias is a secure plugin platform that provides:

- **WASM Runtime**: Safe execution environment for plugins
- **Security Services**: Certificate pinning, CT verification, threat detection
- **Message Broker**: Inter-plugin communication system
- **Permission System**: Granular access control
- **Performance Monitoring**: Real-time metrics and analytics

## Getting Started

### Rust Development

```rust
use connectias_core::{PluginManager, WasmRuntime};
use connectias_security::{RaspMonitor, SecurityPolicy};

// Initialize plugin manager
let mut plugin_manager = PluginManager::new();
let wasm_runtime = WasmRuntime::new();

// Load and execute plugin
let plugin_info = plugin_manager.load_plugin("path/to/plugin.wasm")?;
let result = wasm_runtime.execute_plugin(&plugin_info, &input_data)?;
```

### Dart/Flutter Development

```dart
import 'package:connectias/connectias.dart';

// Initialize Connectias service
await ConnectiasService.instance.initialize();

// Load plugin
final plugin = await ConnectiasService.instance.loadPlugin(File('plugin.wasm'));

// Get plugin metrics
final metrics = await ConnectiasService.instance.getPluginMetrics(plugin.id);
```

## Security Features

- **Certificate Pinning**: SHA-256 SPKI pinning for secure connections
- **Certificate Transparency**: CT log verification for certificate validation
- **WASM Sandboxing**: Isolated execution environment for plugins
- **Threat Detection**: Real-time monitoring and alerting
- **Permission System**: Granular access control for plugins

## Performance

- **Real-time Monitoring**: CPU, memory, and network metrics
- **Resource Limits**: Configurable limits per plugin
- **Caching**: Intelligent caching for improved performance
- **Async Operations**: Non-blocking I/O for better responsiveness

## Support

- **Documentation**: [docs.connectias.com](https://docs.connectias.com)
- **GitHub**: [github.com/connectias/connectias](https://github.com/connectias/connectias)
- **Security**: [security@connectias.com](mailto:security@connectias.com)
- **Issues**: [GitHub Issues](https://github.com/connectias/connectias/issues)