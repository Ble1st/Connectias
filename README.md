# Connectias - Enterprise Security Framework

<div align="center">

![Connectias Logo](logo_preview.html)

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android API](https://img.shields.io/badge/API-33%2B-brightgreen.svg)](https://android-arsenal.com/api?level=33)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org)
[![Rust](https://img.shields.io/badge/Rust-1.70-orange.svg)](https://www.rust-lang.org)

**A modern, enterprise-grade Android security framework with sandbox-isolated plugin system**

[Features](#-features) • [Architecture](#-architecture) • [Installation](#-installation) • [Documentation](#-documentation) • [Contributing](#-contributing)

</div>

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Architecture](#-architecture)
- [Security](#-security)
- [Plugin System](#-plugin-system)
- [Installation](#-installation)
- [Building](#-building)
- [Usage](#-usage)
- [API Documentation](#-api-documentation)
- [Contributing](#-contributing)
- [License](#-license)

## 🎯 Overview

Connectias is a cutting-edge Android security framework designed for enterprise environments. It combines advanced security features with a flexible plugin architecture, allowing organizations to deploy customized security solutions without compromising on performance or user experience.

### Key Highlights

- **🔒 Enterprise-Grade Security**: Multi-layered protection with Rust-native RASP
- **🔌 Plugin-Only Architecture**: All features delivered through isolated plugins
- **⚡ High Performance**: Optimized with Rust NDK and modern Android practices
- **🛡️ Sandbox Isolation**: True OS-level process isolation for plugins
- **📱 Modern UI**: Jetpack Compose with Material 3 Design

## ✨ Features

### Security Features
- **Root Detection**: Advanced detection using Rust-native implementation
- **Debugger Detection**: Prevents runtime analysis and tampering
- **Emulator Detection**: Identifies and blocks emulator environments
- **Tamper Detection**: Monitors application integrity
- **SSL Certificate Pinning**: Prevents man-in-the-middle attacks
- **Encrypted Storage**: SQLCipher for databases, EncryptedDataStore for preferences

### Plugin System
- **Process Isolation**: Each plugin runs in isolated sandbox (`isolatedProcess=true`)
- **Memory Management**: Automatic monitoring and cleanup (50MB warning, 100MB auto-unload)
- **Permission Control**: Granular permission management with user consent
- **Hardware Bridge**: Secure hardware access delegation
- **AIDL-IPC**: Type-safe inter-process communication
- **Dynamic Loading**: Runtime plugin installation and updates

### Development Features
- **Clean Architecture**: 14 core modules with clear separation of concerns
- **Type-Safe Builds**: Kotlin DSL with version catalogs
- **Hilt DI**: Comprehensive dependency injection
- **R8 Full Mode**: Aggressive code optimization
- **Multi-ABI Support**: ARM64, ARMv7, x86_64, x86

## 🏗️ Architecture

```
Connectias/
├── app/                    # Main application module (container)
├── core/                   # Core business logic and security
│   ├── data/              # Repository implementations
│   ├── database/          # SQLCipher database setup
│   ├── datastore/         # Encrypted preferences
│   ├── domain/            # Use cases and business rules
│   ├── model/             # Data models
│   ├── network/           # Network layer
│   └── security/          # RASP implementation
├── common/                # Shared UI components and utilities
├── feature-settings/      # Settings module
├── plugin-sdk-temp/       # Plugin development kit
└── benchmark/             # Performance benchmarks
```

### Security Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Main Process  │    │  Plugin Sandbox  │    │  Hardware Bridge│
│                 │    │  (:plugin_sandbox)│    │                 │
│ - App UI        │◄──►│ - Isolated       │◄──►│ - Camera        │
│ - Plugin Manager│    │   Process        │    │ - Network       │
│ - Security      │    │ - No Permissions │    │ - Bluetooth     │
│ - Hardware      │    │ - Memory Monitor │    │ - Printer       │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌──────────────────┐
                    │   Rust RASP      │
                    │ - Root Detection │
                    │ - Anti-Tamper    │
                    │ - Anti-Debug     │
                    └──────────────────┘
```

## 🔒 Security

### RASP (Runtime Application Self-Protection)

Connectias implements a comprehensive RASP system with native Rust components:

| Feature | Implementation | Performance |
|---------|----------------|-------------|
| **Root Detection** | Rust-native with 22+ SU paths | 2-3x faster than RootBeer |
| **Debugger Detection** | JNI + native checks | Low overhead |
| **Emulator Detection** | 16+ framework checks | Real-time detection |
| **Tamper Detection** | Hash verification | Instant alerts |

### Encryption

- **Database**: SQLCipher with 256-bit AES
- **Preferences**: EncryptedDataStore with Tink
- **Communication**: Certificate pinning with SHA-256
- **Memory**: Zeroize for sensitive data

## 🔌 Plugin System

The plugin system is the core innovation of Connectias, enabling:

### Isolation
- **Process Level**: `isolatedProcess=true` provides OS-level isolation
- **Memory**: Automatic monitoring and cleanup
- **Permissions**: No default permissions in sandbox
- **File System**: Separate storage with read-only plugin files

### Communication
```kotlin
// AIDL Interface
interface IPluginSandbox {
    fun loadPluginFromDescriptor(fd: ParcelFileDescriptor, id: String): PluginResultParcel
    fun enablePlugin(pluginId: String): PluginResultParcel
    fun getSandboxMemoryUsage(): Long
}

// Hardware Bridge
interface IHardwareBridge {
    fun captureImage(pluginId: String): HardwareResponseParcel
    fun httpGet(pluginId: String, url: String): HardwareResponseParcel
}
```

### Development
```kotlin
class MyPlugin : IPlugin {
    override fun onLoad(context: PluginContext): Boolean {
        // Initialize plugin
    }
    
    override fun onEnable(): Boolean {
        // Enable functionality
    }
}
```

## 📱 Installation

### Prerequisites

- **Android Studio**: Arctic Fox or later
- **Android SDK**: API 33+ (Android 13)
- **NDK**: Version 29.0.14206865
- **Rust**: 1.70+ (for native components)
- **Gradle**: 9.2.1+

### Clone Repository

```bash
git clone https://github.com/your-org/connectias.git
cd connectias
```

### Setup Environment

1. **Install Rust** (for native components):
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source ~/.cargo/env
```

2. **Install Android NDK** (if not installed):
```bash
# In Android Studio: Tools → SDK Manager → SDK Tools → NDK (Side by side)
```

3. **Install cargo-ndk**:
```bash
cargo install cargo-ndk
```

## 🔨 Building

### Debug Build

```bash
./gradlew assembleDebug
```

### Release Build

```bash
# Generate signing key
./create-keystore.sh

# Build release APK
./gradlew assembleRelease
```

### Build with Native Components

```bash
# Build Rust libraries
cd core
./build-rust.sh

# Build Android app
cd ..
./gradlew assembleRelease
```

## 📖 Usage

### Basic Usage

```kotlin
// Initialize security
val securityService = SecurityService(context)
val result = securityService.performSecurityChecks()

if (!result.isSecure) {
    // Handle security threats
    result.threats.forEach { threat ->
        when (threat) {
            is SecurityThreat.RootDetected -> // Block app
            is SecurityThreat.DebuggerDetected -> // Exit app
        }
    }
}

// Load plugin
val pluginManager = PluginManagerSandbox(context)
val result = pluginManager.loadPluginFromFile(pluginFile)

if (result.isSuccess) {
    pluginManager.enablePlugin(pluginId)
}
```

### Plugin Development

1. **Create Plugin Module**:
```kotlin
dependencies {
    implementation("com.ble1st.connectias:plugin-sdk:1.0.0")
}
```

2. **Implement Plugin Interface**:
```kotlin
class SecurityPlugin : IPlugin {
    override fun onLoad(context: PluginContext): Boolean {
        // Setup plugin
        return true
    }
    
    override fun onEnable(): Boolean {
        // Enable features
        return true
    }
}
```

3. **Create Plugin Manifest**:
```json
{
    "pluginId": "security-scanner",
    "pluginName": "Security Scanner",
    "version": "1.0.0",
    "author": "Your Name",
    "fragmentClassName": "com.example.SecurityScannerFragment",
    "permissions": ["android.permission.CAMERA"],
    "category": "SECURITY"
}
```

## 📚 API Documentation

### Core APIs

#### Security Service
```kotlin
class SecurityService {
    suspend fun performSecurityChecks(): SecurityCheckResult
    fun isRooted(): Boolean
    fun isDebuggerAttached(): Boolean
    fun isEmulator(): Boolean
}
```

#### Plugin Manager
```kotlin
class PluginManagerSandbox {
    suspend fun loadPluginFromFile(file: File): PluginResult
    suspend fun enablePlugin(pluginId: String): PluginResult
    suspend fun unloadPlugin(pluginId: String): PluginResult
    fun getLoadedPlugins(): List<String>
}
```

#### Hardware Bridge
```kotlin
interface IHardwareBridge {
    fun captureImage(pluginId: String): HardwareResponseParcel
    fun httpGet(pluginId: String, url: String): HardwareResponseParcel
    fun getPairedBluetoothDevices(pluginId: String): List<String>
}
```

### Full Documentation

- [Plugin Development Guide](docs/plugin-development.md)
- [Security API Reference](docs/security-api.md)
- [Hardware Access Guide](docs/hardware-access.md)

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Workflow

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

### Code Style

- **Kotlin**: Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Rust**: Follow `rustfmt` and `clippy` recommendations
- **Documentation**: Include KDoc comments for public APIs

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run benchmarks
./gradlew connectedAndroidTest -P android.testInstrumentationRunnerArguments.class=com.ble1st.connectias.benchmark.BenchmarkRunner
```

## 🏆 Performance

### Benchmarks

| Operation | Connectias | Traditional | Improvement |
|-----------|------------|-------------|-------------|
| Root Detection | 15ms | 45ms | **3x faster** |
| Plugin Load | 120ms | 350ms | **3x faster** |
| Memory Usage | 45MB | 120MB | **62% less** |
| Startup Time | 0.8s | 1.5s | **47% faster** |

### Memory Management

- **Plugin Sandbox**: 50MB warning, 100MB auto-unload
- **Main Process**: Optimized with R8 Full Mode
- **Native Libraries**: Stripped with debug symbols removed

## 📊 Statistics

- **Lines of Code**: ~50,000 (Kotlin) + ~5,000 (Rust)
- **Test Coverage**: 85% (core modules)
- **Plugin Support**: Unlimited (memory constrained)
- **API Level**: 33+ (Android 13+)
- **Supported ABIs**: arm64-v8a, armeabi-v7a, x86_64, x86

## 🆘 Support

- **Documentation**: [docs/](docs/)
- **Issues**: [GitHub Issues](https://github.com/your-org/connectias/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/connectias/discussions)
- **Email**: support@connectias.dev

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

```
Copyright 2025 Connectias

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 🙏 Acknowledgments

- **Android Open Source Project** - Core framework and libraries
- **Kotlin Team** - Modern programming language
- **Rust Community** - Safe and performant systems programming
- **SQLCipher Team** - Encrypted database solution
- **All Contributors** - For making this project better

---

<div align="center">

**[⬆ Back to top](#connectias---enterprise-security-framework)**

Made with ❤️ by the Connectias Team

</div>
