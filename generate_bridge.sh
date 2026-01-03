#!/bin/bash
# Generate flutter_rust_bridge bindings

set -e

# Generate the bridge code
flutter_rust_bridge_codegen generate \
    --rust-input crate::api \
    --dart-output lib \
    --rust-output rust/src/bridge_generated.rs \
    --rust-root rust

echo "Bridge generated successfully!"

