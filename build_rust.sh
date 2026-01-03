#!/bin/bash
# Build script for Rust native library

set -e

cd rust

# Build for Android ARM64 (aarch64)
cargo build --target aarch64-linux-android --release

# Build for Android ARM (armv7)
cargo build --target armv7-linux-androideabi --release

# Build for Android x86_64
cargo build --target x86_64-linux-android --release

# Build for Android x86
cargo build --target i686-linux-android --release

echo "Rust library built successfully!"

