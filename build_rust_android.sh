#!/bin/bash
# Build Rust library for Android and copy to jniLibs

set -e

echo "Building Rust library for Android..."

cd rust

# Build for all Android architectures
echo "Building for arm64-v8a (aarch64-linux-android)..."
cargo ndk -t aarch64-linux-android build --release

echo "Building for armeabi-v7a (armv7-linux-androideabi)..."
cargo ndk -t armv7-linux-androideabi build --release

echo "Building for x86_64..."
cargo ndk -t x86_64-linux-android build --release

echo "Building for x86..."
cargo ndk -t i686-linux-android build --release

cd ..

# Create jniLibs directories
mkdir -p android/app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64,x86}

# Copy libraries
echo "Copying libraries to jniLibs..."
cp rust/target/aarch64-linux-android/release/libconnectias_rust.so android/app/src/main/jniLibs/arm64-v8a/
cp rust/target/armv7-linux-androideabi/release/libconnectias_rust.so android/app/src/main/jniLibs/armeabi-v7a/
cp rust/target/x86_64-linux-android/release/libconnectias_rust.so android/app/src/main/jniLibs/x86_64/
cp rust/target/i686-linux-android/release/libconnectias_rust.so android/app/src/main/jniLibs/x86/

# Also copy to rust/target/release for flutter_rust_bridge default path
mkdir -p rust/target/release
cp rust/target/aarch64-linux-android/release/libconnectias_rust.so rust/target/release/

echo "✅ Rust libraries built and copied successfully!"
echo "Libraries are in:"
ls -lh android/app/src/main/jniLibs/*/libconnectias_rust.so

