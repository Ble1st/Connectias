# Connectias

FOSS Android application for network analysis, security, privacy, and system utilities without Google dependencies. Focus: local processing, modular architecture, and optional DVD playback (LibVLC) including USB block device support.

## Overview

Connectias is a comprehensive Android security and network analysis tool built with modern Android development practices. The app features a modular architecture allowing optional features to be enabled/disabled via Gradle properties, reducing APK size and improving performance.

### Core Features

- **Security**: RASP (Runtime Application Self-Protection) monitoring with root detection, debugger protection, emulator detection, and tamper protection. Certificate and password checks, encryption tools, firewall/risk analysis.
- **Plugin System**: Dynamic plugin loading system supporting standalone APK plugins. Plugins can extend app functionality with custom features, UI components, and native libraries.
- **Logging**: Comprehensive logging system with database storage, log viewer, and filtering capabilities.
- **Settings**: Full-featured settings management with theme customization, security preferences, and module configuration.

### Optional Features (Module-based)

- **Network Tools**: Port scanner, DNS tools, traffic/WiFi analysis
- **Bluetooth Scanner**: Scan nearby Bluetooth devices with signal proximity
- **DNS Tools**: Advanced DNS query and analysis tools
- **Barcode/QR Scanner**: Scan and generate QR codes and barcodes
- **Calendar**: Calendar integration and management
- **NTP**: Network Time Protocol synchronization
- **SSH**: SSH/SCP client functionality
- **Password Tools**: Password generation and management
- **Device Info**: Detailed device information and monitoring
- **Document Scanner**: Document scanning and OCR capabilities
- **GPS/Satellite**: GPS and satellite positioning tools
- **Secure Notes**: Encrypted note storage
- **DVD Playback**: LibVLC-based DVD playback with native libdvd* libraries

## Architecture

### Core Modules (Always Included)

- `:app` - Main application host with navigation and plugin management
- `:common` - Shared UI components, theme, strings, and base models
- `:core` - Core services including:
  - Security (RASP monitoring, root/debugger/emulator/tamper detection)
  - Database (SQLCipher-encrypted Room database)
  - Logging (structured logging with database storage)
  - Dependency Injection (Hilt)
  - Settings repository
- `:feature-settings` - Settings UI and management
- `:core:model`, `:core:common`, `:core:designsystem`, `:core:ui`, `:core:database`, `:core:datastore`, `:core:network`, `:core:data`, `:core:domain`, `:core:testing` - Core sub-modules

### Optional Modules

The following modules can be enabled/disabled via `gradle.properties`:

- `:feature-network` - Network analysis tools
- `:feature-bluetooth` - Bluetooth scanning
- `:feature-dnstools` - DNS utilities
- `:feature-barcode` - Barcode/QR code scanning
- `:feature-calendar` - Calendar integration
- `:feature-ntp` - NTP synchronization
- `:feature-ssh` - SSH/SCP client
- `:feature-password` - Password tools
- `:feature-deviceinfo` - Device information
- `:feature-scanner` - Document scanner
- `:feature-satellite` - GPS/satellite tools
- `:feature-secure-notes` - Encrypted notes
- `:feature-dvd` - DVD playback (LibVLC + native libdvd*)

### Plugin System

Connectias features a dynamic plugin system that allows third-party developers to create standalone APK plugins:

- **Dynamic Loading**: Plugins are loaded at runtime using DexClassLoader
- **Native Library Support**: Plugins can include native libraries (.so files)
- **Fragment-based UI**: Plugins can provide custom UI using Jetpack Compose or traditional Fragments
- **Plugin SDK**: Standardized interface (`IPlugin`) for plugin development
- **Lifecycle Management**: Full plugin lifecycle (load, enable, disable, unload)
- **Plugin Management UI**: Built-in UI for importing, enabling, and managing plugins

Plugins are compiled as standalone APKs and can be imported into the app at runtime. The plugin system uses reflection-based loading and requires compatible Kotlin and AndroidX library versions.

## Build Requirements

- **Android Studio**: Hedgehog+ (latest stable version)
- **JDK**: 17 or higher
- **Android SDK**: 
  - minSdk: 33
  - targetSdk: 36
  - compileSdk: 36
- **Kotlin**: 2.3.0
- **Gradle**: 9.2.1+

## Building

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### Running Tests
```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

### Enabling/Disabling Optional Modules

Edit `gradle.properties` and set feature flags:

```properties
# Enable a feature module
feature.network.enabled=true
feature.dvd.enabled=true

# Disable a feature module
feature.bluetooth.enabled=false
feature.barcode.enabled=false
```

### Building Plugins

Plugins are built separately. See the plugin development guide in `docs/PLUGIN_DEVELOPMENT_GUIDE.md`.

## Technology Stack

- **Language**: Kotlin 2.3.0
- **UI**: Jetpack Compose + Material 3 (with XML/Fragment hybrid support)
- **Architecture**: MVVM with Clean Architecture principles
- **Dependency Injection**: Hilt
- **Database**: Room with SQLCipher encryption
- **Networking**: OkHttp
- **Coroutines**: Kotlin Coroutines for asynchronous operations
- **Navigation**: Navigation Component
- **Security**: 
  - RASP (Runtime Application Self-Protection)
  - Rust-based native security detectors
  - SSL Pinning
  - Encrypted storage (SQLCipher)

## Third-Party Licenses

| Component | Version | License | Source |
|-----------|---------|---------|--------|
| LibVLC | 3.6.5 | LGPL-2.1+ (some modules GPL) | https://www.videolan.org/vlc/libvlc.html |
| libdvdcss | Source in repo | GPL-2.0+ | https://code.videolan.org/videolan/libdvdcss |
| libdvdnav | Source in repo | GPL-2.0+ | https://code.videolan.org/videolan/libdvdnav |
| libdvdread | Source in repo | GPL-2.0+ | https://code.videolan.org/videolan/libdvdread |
| ZXing | 3.5.4 | Apache-2.0 | https://github.com/zxing/zxing |
| BouncyCastle | 1.83 | MIT-like (Bouncy Castle License) | https://www.bouncycastle.org/ |
| dnsjava | 3.6.3 | BSD-2-Clause | https://github.com/dnsjava/dnsjava |
| OkHttp | 5.3.2 | Apache-2.0 | https://square.github.io/okhttp |
| iText 7 Core | 9.4.0 | AGPL-3.0 | https://itextpdf.com/ |
| MPAndroidChart | 3.1.0 | Apache-2.0 | https://github.com/PhilJay/MPAndroidChart |
| SQLCipher | 4.12.0 | BSD-like | https://www.zetetic.net/sqlcipher/ |
| Timber | 5.0.1 | Apache-2.0 | https://github.com/JakeWharton/timber |
| libaums | 0.10.0 | Apache-2.0 | https://github.com/magnusja/libaums |
| RootBeer | 0.1.1 | Apache-2.0 | https://github.com/scottyab/rootbeer |
| AndroidX/Jetpack | See `gradle/libs.versions.toml` | Apache-2.0 | https://developer.android.com/jetpack |

**Note on GPL Components (libdvd*):**
- Source code is located in the repository under `feature-dvd/src/main/cpp/external/`
- Static or shared linking with GPL libraries may trigger copyleft requirements for the entire binary. Please ensure license compliance (source disclosure, notice text, potentially dynamic loading).

## Security Features

### RASP (Runtime Application Self-Protection)

- **Root Detection**: Rust-based native root detection
- **Debugger Protection**: Prevents debugging in production builds
- **Emulator Detection**: Blocks execution in emulators
- **Tamper Protection**: Continuous integrity monitoring
- **SSL Pinning**: Certificate pinning for secure connections

### Data Protection

- **Encrypted Database**: SQLCipher for all local data storage
- **Secure Key Management**: Android Keystore for encryption keys
- **No Google Services**: Complete independence from Google Play Services
- **Local Processing**: All data processing happens locally

## Resources

- **Plugin Development**: `docs/PLUGIN_DEVELOPMENT_GUIDE.md`
- **LibVLC Integration**: `docs/VLC_INTEGRATION.md` (if exists)
- **License Files**: See table above / subdirectories in `feature-dvd/src/main/cpp/external/`
- **Module and Build Notes**: `gradle.properties`, `build.gradle.kts` (project & modules)

## Contributing

Pull requests are welcome. Please:
- Follow coding guidelines and security requirements
- Never commit sensitive data in logs or repositories
- Ensure all tests pass
- Update documentation for new features
- Follow the existing code style and architecture patterns

## Changelog

See project Git history. A dedicated changelog file may be added in the future.

## License

[Add your project license here]
