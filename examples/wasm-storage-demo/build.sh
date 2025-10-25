#!/bin/bash

echo "🦀 Building WASM Storage Demo Plugin..."

# Prüfe ob wasm-pack installiert ist
if ! command -v wasm-pack &> /dev/null; then
    echo "❌ wasm-pack is not installed. Installing..."
    cargo install wasm-pack
fi

# Kompiliere zu WASM
wasm-pack build --target web --out-dir pkg

if [ $? -eq 0 ]; then
    echo "✅ WASM Storage Demo Plugin erfolgreich kompiliert!"
    echo "📦 Output: pkg/wasm_storage_demo_plugin.wasm"
    echo "📋 Package: pkg/wasm_storage_demo_plugin.js"
else
    echo "❌ Failed to build WASM Storage Demo Plugin"
    exit 1
fi
