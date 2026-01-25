# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Connectias is an enterprise-grade Android security framework with a sandbox-isolated plugin system. The architecture uses true OS-level process isolation (`isolatedProcess=true`) to run untrusted third-party plugins safely, with multi-layered security including Rust-native RASP, ClassLoader restrictions, IPC rate limiting, and permission pre-checking.

**Key Technologies:**
- Kotlin 2.3.0 / Android API 33+ (Gradle 9.0.0)
- Rust 1.70+ for native RASP implementation
- Jetpack Compose + Material 3
- Hilt dependency injection
- SQLCipher for encrypted database
- AIDL for inter-process communication

## Build Commands

### Standard Development

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore)
./create-keystore.sh  # First time only
./gradlew assembleRelease

# Run tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Code coverage report
./gradlew jacocoTestReport
./gradlew jacocoTestCoverageVerification  # Enforces 50% minimum
```

### Rust Native Components

```bash
# Build Rust libraries for all ABIs (arm64-v8a, armeabi-v7a, x86_64, x86)
./build-rust-libs.sh

# Or build from core directory
cd core && ./build-rust.sh

# Verify Rust builds
find core/src/main/jniLibs -name "*.so"
```

**Important:** Always rebuild Rust libraries after modifying `core/src/main/rust/src/lib.rs` before running Gradle builds.

### Plugin Development

```bash
# Build plugin SDK (integrated module)
./gradlew :plugin-sdk:assembleRelease
```

### Performance & Benchmarks

```bash
# Run baseline profile generation
./gradlew connectedAndroidTest -P android.testInstrumentationRunnerArguments.class=com.ble1st.connectias.benchmark.BenchmarkRunner

# Memory leak detection (debug builds only)
# LeakCanary automatically instruments debug builds
```

## High-Level Architecture

### Dual-Process Plugin Isolation

The plugin system uses a **two-tier architecture** with complete process isolation:

```
[Main App Process]                    [Sandbox Isolated Process (isolatedProcess=true)]
├─ PluginSandboxProxy                 ├─ PluginSandboxService
│  (Handles IPC routing)              │  (Manages isolated plugins)
├─ HardwareBridgeService              ├─ RestrictedClassLoader
│  (Grants hardware access)           │  (Blocks reflection attacks)
├─ PluginMessagingService             ├─ FilteredParentClassLoader
│  (Routes inter-plugin messages)     │  (Filters SDK access)
├─ FileSystemBridgeService            ├─ SandboxPluginContext
│  (Provides file access)             │  (Limited permissions)
└─ UI rendering hosts                 └─ Loaded plugin instances
   (VirtualDisplay surface)              (Run isolated)
```

**Critical Files:**
- `app/src/main/aidl/com/ble1st/connectias/plugin/IPluginSandbox.aidl` - Main sandbox IPC contract
- `app/src/main/java/com/ble1st/connectias/core/plugin/PluginSandboxService.kt` - Sandbox process service (runs isolated)
- `app/src/main/java/com/ble1st/connectias/core/plugin/PluginSandboxProxy.kt` - Main process proxy
- `app/src/main/java/com/ble1st/connectias/plugin/PluginManagerSandbox.kt` - Plugin lifecycle orchestrator

### Multi-Bridge AIDL Architecture

The system uses **6 specialized AIDL bridges** for different subsystems (not one monolithic bridge):

| Bridge | Purpose | Security |
|--------|---------|----------|
| **IPluginSandbox** | Plugin lifecycle (load/enable/disable/unload) | Rate-limited, identity-verified |
| **IHardwareBridge** | Camera, Bluetooth, Printer, Network | Permission-checked via SecureHardwareBridgeWrapper |
| **IPluginMessaging** | Inter-plugin communication | Rate-limited (100 msg/sec), 1MB payload cap |
| **IFileSystemBridge** | File access operations | Permission-checked, path-validated |
| **IPluginUIBridge** | VirtualDisplay UI rendering | Touch event dispatch, surface management |
| **IPermissionCallback** | Async permission results | Callback-based result delivery |

**AIDL Location:** `app/src/main/aidl/com/ble1st/connectias/`

**Connection Flow:**
1. Main process calls `PluginSandboxProxy.connect()` → binds to PluginSandboxService
2. Sandbox service connects to HardwareBridgeService, MessagingService, etc.
3. Bridges set via IPC (`setHardwareBridge()`, `setMessagingBridge()`)
4. Plugins load in sandbox with bridge references

### Security Layer Architecture (Phase-Based)

Security is enforced through **5 completed phases** (Phase 4 SELinux deferred):

| Phase | Component | Implementation |
|-------|-----------|----------------|
| **Phase 1** | UI-Isolation | VirtualDisplay rendering in sandbox, main process only displays surface |
| **Phase 2** | ClassLoader-Isolation | RestrictedClassLoader + FilteredParentClassLoader block reflection attacks |
| **Phase 3** | IPC Rate-Limiting | IPCRateLimiter prevents DoS (1-60 calls/sec per method) |
| **Phase 5** | Permission Pre-Check | PermissionPreChecker validates before API execution |
| **Phase 4** | SELinux Enforcement | Documented but not implemented (deferred) |

**Files:**
- `core/plugin/security/RestrictedClassLoader.kt` - Forbids ClassLoader, Runtime, ProcessBuilder, DexClassLoader
- `core/plugin/security/FilteredParentClassLoader.kt` - Whitelist: android.*, androidx.*, kotlin.*, SDK only
- `plugin/security/SecureHardwareBridgeWrapper.kt` - Wraps hardware bridge with identity + permission checks
- `plugin/security/PermissionPreChecker.kt` - API→Permission mapping (captureImage→CAMERA, etc.)
- `plugin/security/PluginIdentitySession.kt` - Prevents pluginId spoofing via session tokens

**Pattern:** Identity verification → Permission pre-check → Audit logging → API execution

### Plugin Lifecycle & Context Management

**Three-Layer Context Hierarchy:**
```
IsolatedPluginContext (Sandbox, Phase 1 UI-isolation)
    ↓ extends
SandboxPluginContext (Minimal, isolated process)
    ↓ wrapped by
SecureContextWrapper (Permission enforcement)
```

**Lifecycle (in sandbox process):**
1. `loadPluginFromDescriptor()` - Reads APK via ParcelFileDescriptor, extracts DEX into memory
2. `RestrictedClassLoader` created with filtered parent
3. Plugin class loaded, `onLoad()` called with SandboxPluginContext
4. `onEnable()` activates plugin functionality
5. **Memory monitoring** detects >300MB warning, >400MB auto-unload (PSS-based, not heap)
6. `onDisable()` → `onUnload()` cleanup + session removal

**Files:**
- `core/plugin/PluginSandboxService.kt` - Service lifecycle + memory monitor
- `core/plugin/SandboxPluginContext.kt` - Minimal isolated context
- `plugin/PluginContextImpl.kt` - Main process context (full capabilities)

### Rust Native RASP Implementation

**Native Security Detection** (replaces RootBeer library):
- `core/src/main/rust/src/lib.rs` - Rust implementation with JNI bindings
- Root Detection: Checks 20+ su binary paths (su, magisk, xposed)
- Tamper Detection: Detects Xposed (17 paths), Frida, Substrate
- Debugger Detection: Checks for attached debuggers
- Emulator Detection: QEMU paths, build properties, CPU capabilities

**Build:** Use `./build-rust-libs.sh` to compile for all ABIs (arm64-v8a, armeabi-v7a, x86_64, x86)

**Integration:** JNI bindings called from `RustRootDetector`, `RustTamperDetector`, `RustDebuggerDetector` in main process

**Performance:** 3x faster than RootBeer library (15ms vs 45ms for root detection)

### Module Structure (14 Core Modules)

The project uses **modular architecture** with clear separation:

```
Connectias/
├─ app/                      # Main container app (PluginSandboxProxy, MainActivity)
├─ core/                     # Legacy core module (being migrated)
│  ├─ src/main/rust/        # Rust RASP implementation
│  └─ src/main/java/...     # Security, services, plugin system
├─ core/common/             # Shared utilities
├─ core/data/               # Repository implementations
├─ core/database/           # SQLCipher database setup
├─ core/datastore/          # EncryptedDataStore preferences
├─ core/domain/             # Use cases and business rules
├─ core/model/              # Data models
├─ core/network/            # Network layer
├─ core/security/           # RASP, SSL pinning (new, being migrated)
├─ core/testing/            # Test utilities
├─ core/ui/                 # Shared UI components
├─ core/designsystem/       # Material 3 theme, components
├─ common/                  # Shared UI logic
├─ feature-settings/        # Settings feature module
├─ benchmark/               # Performance benchmarks
└─ plugin-sdk/              # Plugin SDK for third-party developers (Gradle module)
```

**Dependency Injection:** Hilt/Dagger throughout
- `core/di/` - CoreModule, NetworkModule, ServiceModule, PluginModule
- All major components (PluginManagerSandbox, SecurityService, etc.) are @Singleton injected

### Inter-Plugin Messaging System

**Request/Response Message Broker Architecture:**
```
Plugin A (Sandbox) → IPluginMessaging.sendMessage() → Main Process PluginMessageBroker
    ↓ routes by receiverId
Plugin B (Sandbox) ← IPluginMessaging.receiveMessages() ← MessageResponse
```

**Features:**
- Rate limited: 100 messages/sec per plugin
- Payload capped: 1MB max
- Request timeout: 5 seconds default
- Message types: DATA_REQUEST, DATA_RESPONSE, EVENT_NOTIFICATION, COMMAND_EXECUTE, STATUS_UPDATE
- Security: Only registered plugins can participate

**Files:**
- `plugin/messaging/PluginMessageBroker.kt` - Central routing in main process
- `plugin/messaging/PluginMessagingService.kt` - Main process service
- See `docs/PLUGIN_MESSAGING.md` for complete usage

### Permission & Security Audit

**Three-Layer Enforcement:**
1. **Pre-Check Layer** - PermissionPreChecker validates before API execution
2. **Identity Layer** - PluginIdentitySession prevents spoofing (session tokens)
3. **Audit Layer** - SecurityAuditManager logs all security events

**Files:**
- `plugin/security/PermissionPreChecker.kt` - API→Permission mapping
- `plugin/PluginPermissionManager.kt` - Permission state management
- `plugin/security/SecurityAuditManager.kt` - Event logging (plugin_reflection_blocked, plugin_rate_limited, etc.)

### Hardware Bridge Delegation

Plugins in sandbox process **cannot directly access hardware** (no permissions). Hardware access is delegated via bridge:

```
Plugin Request (sandbox) → IHardwareBridge IPC → HardwareBridgeService (main)
    ↓ identity verified + permission checked
Android Hardware APIs (camera, bluetooth, network, printer)
    ↓
HardwareResponseParcel returned via IPC
```

**Files:**
- `hardware/HardwareBridgeService.kt` - Main process service with app permissions
- `plugin/security/SecureHardwareBridgeWrapper.kt` - Security wrapper
- `hardware/network/EnhancedPluginNetworkPolicy.kt` - Network bandwidth tracking per plugin

## Key Development Patterns

### When Modifying Plugin System

1. **AIDL Changes:** After modifying `.aidl` files, rebuild with `./gradlew assembleDebug` (AIDL compiler runs automatically)
2. **Security Wrappers:** ALL bridge methods MUST go through `SecureHardwareBridgeWrapper` or `SecureFileSystemBridgeWrapper`
3. **Identity Verification:** Use `PluginIdentitySession.verifyIdentity(pluginId)` before ANY privileged operation
4. **Audit Logging:** Log security events via `SecurityAuditManager.logEvent()`
5. **Rate Limiting:** Add new IPC methods to `IPCRateLimiter` configuration

### When Adding New Hardware APIs

1. Add method to `IHardwareBridge.aidl`
2. Implement in `HardwareBridgeService.kt` (main process)
3. Wrap in `SecureHardwareBridgeWrapper.kt` with permission check
4. Add permission mapping to `PermissionPreChecker.kt`
5. Update `docs/hardware-access.md`

### ClassLoader Security

**Blocked Classes:** `RestrictedClassLoader` forbids loading:
- `java.lang.ClassLoader` (prevents bypass)
- `java.lang.Runtime` (prevents shell execution)
- `java.lang.ProcessBuilder` (prevents process spawning)
- `dalvik.system.DexClassLoader` (prevents dynamic loading)

**Allowed Packages:** `FilteredParentClassLoader` whitelists:
- `android.*`, `androidx.*`, `kotlin.*` (Android SDK)
- `com.ble1st.connectias.plugin.sdk.*` (Plugin SDK only)

**Blocked Internal Packages:**
- `com.ble1st.connectias.core.*` (App internals)
- `com.ble1st.connectias.ui.*` (UI internals)
- `com.ble1st.connectias.data.*` (Data layer)

### Memory Management

- **Monitoring:** PSS (Proportional Set Size) tracking, not just heap
- **Trends:** Tracks bytes/sec growth rate
- **Thresholds:** 300MB warning, 400MB auto-unload
- **Implementation:** `PluginSandboxService.startMemoryMonitor()` with ActivityManager.getProcessMemoryInfo()

### Testing Strategy

```bash
# Unit tests (local JVM)
./gradlew test

# RestrictedClassLoader tests
./gradlew :app:testDebugUnitTest --tests "*RestrictedClassLoaderTest"

# Integration tests (device/emulator required)
./gradlew connectedAndroidTest

# Security tests (reflection attacks, ClassLoader bypass)
./gradlew connectedAndroidTest --tests "*SecurityTest"

# Coverage report (50% minimum enforced)
./gradlew jacocoTestReport jacocoTestCoverageVerification
```

## Important Notes

### Rust Native Builds

- **Always rebuild Rust** after modifying `core/src/main/rust/src/lib.rs`
- Run `./build-rust-libs.sh` before Gradle builds
- Verify with: `find core/src/main/jniLibs -name "*.so"`
- Libraries are copied to `core/src/main/jniLibs/{abi}/libconnectias_root_detector.so`

### Gradle Configuration

- **R8 Full Mode:** Enabled in release builds (`android.enableR8.fullMode=true`)
- **Configuration Cache:** Enabled for faster builds (`org.gradle.configuration-cache=true`)
- **JVM Args:** 4GB heap for Gradle daemon (optimized for large project)
- **Version Catalogs:** All dependencies managed in `gradle/libs.versions.toml`

### Hilt/DI

- **JavaPoet Compatibility:** Hilt downgraded to 2.56.1 due to JavaPoet 1.13.0 requirement
- **Aggregating Task:** Disabled (`hilt.enableAggregatingTask=false`) to avoid JavaPoet issues
- **WorkManager:** Hilt WorkManager integration requires `@HiltWorker` annotation

### Plugin Development

- **SDK Location:** `plugin-sdk/`
- **Plugin Manifest:** JSON file with pluginId, version, permissions, fragmentClassName
- **Sandboxing:** Plugins run in isolated process with NO default permissions

### Security Implementation Status

See `docs/SECURITY_IMPLEMENTATION_STATUS.md` for detailed status of security phases:
- ✅ Phase 1 (UI-Isolation): COMPLETED
- ✅ Phase 2 (ClassLoader-Isolation): COMPLETED
- ✅ Phase 3 (IPC Rate-Limiting): COMPLETED
- ✅ Phase 5 (Permission Pre-Check): COMPLETED
- ⏭️ Phase 4 (SELinux Enforcement): DEFERRED

### Performance Targets

| Metric | Target | Current |
|--------|--------|---------|
| Root Detection | <20ms | 15ms |
| Plugin Load | <150ms | 120ms |
| Memory Usage | <60MB | 45MB |
| Startup Time | <1s | 0.8s |

### Code Style

- **Kotlin:** Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Rust:** Follow `rustfmt` and `clippy` recommendations (run `cargo fmt` and `cargo clippy`)
- **Documentation:** Include KDoc comments for public APIs
- **AIDL:** Include comments in `.aidl` files for IPC contract documentation
