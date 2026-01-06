# Rust Port Scanner

High-performance port scanner implementation in Rust for Android.

## Overview

This Rust implementation provides a significant performance improvement over the Kotlin implementation, especially for large port ranges. It uses `tokio` for async I/O and provides JNI bindings for Android integration.

## Performance

- **5-10x faster** than Kotlin implementation for large port ranges
- **Lower memory usage** due to zero-cost abstractions
- **Better concurrency** with async/await

## Building

### Prerequisites

1. **Rust toolchain** (install from https://rustup.rs/)
2. **cargo-ndk** for Android builds:
   ```bash
   cargo install cargo-ndk
   ```
3. **Android NDK** (version 26.1.10909125 or compatible)

### Build for Android

```bash
cd feature-network
./build-rust.sh
```

Or manually:

```bash
cd src/main/rust

# Build for specific ABI
cargo ndk --target aarch64-linux-android --platform 33 -- build --release

# Build for all ABIs
cargo ndk --target aarch64-linux-android --platform 33 -- build --release
cargo ndk --target armv7-linux-androideabi --platform 33 -- build --release
cargo ndk --target x86_64-linux-android --platform 33 -- build --release
cargo ndk --target i686-linux-android --platform 33 -- build --release
```

The built libraries will be in `target/<abi>/release/libconnectias_port_scanner.so`

## Integration

The Rust library is automatically integrated via:
1. **JNI bindings** in `RustPortScanner.kt`
2. **CMake** configuration in `src/main/cpp/CMakeLists.txt`
3. **Gradle** build configuration in `build.gradle.kts`

The Kotlin `PortScanner` class automatically falls back to the Kotlin implementation if Rust is not available.

## Architecture

```
┌─────────────────┐
│  Kotlin/Android │
│  PortScanner    │
└────────┬────────┘
         │
         ├─► RustPortScanner (JNI Bridge)
         │        │
         │        ▼
         │   ┌─────────────┐
         │   │ Rust Library│
         │   │ (tokio)     │
         │   └─────────────┘
         │
         └─► Kotlin Implementation (Fallback)
```

## Testing

Run Rust tests:

```bash
cd src/main/rust
cargo test
```

## Dependencies

- `tokio` - Async runtime
- `jni` - JNI bindings
- `serde` / `serde_json` - JSON serialization
- `android_logger` - Android logging (Android only)

## Troubleshooting

### Library not found

If you get `UnsatisfiedLinkError`, make sure:
1. Rust library is built for the correct ABI
2. Library is in `src/main/jniLibs/<abi>/`
3. CMake build completed successfully

### Build fails

- Check NDK version matches (26.1.10909125)
- Ensure `cargo-ndk` is installed and in PATH
- Verify Rust toolchain is up to date: `rustup update`

## Future Improvements

- [ ] Banner grabbing implementation
- [ ] Service detection improvements
- [ ] UDP port scanning
- [ ] SYN scan support (requires root)

