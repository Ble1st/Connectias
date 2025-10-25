#!/bin/bash

echo "🦀 Building WASM Network Demo Plugin..."

# Prüfe ob wasm-pack installiert ist
if ! command -v wasm-pack &> /dev/null; then
    echo "❌ wasm-pack is not installed. Installing..."
    cargo install wasm-pack
fi

# Kompiliere zu WASM
wasm-pack build --target web --out-dir pkg

if [ $? -eq 0 ]; then
    echo "✅ WASM Network Demo Plugin erfolgreich kompiliert!"
    echo "📦 Output: pkg/wasm_network_demo_plugin.wasm"
    echo "📋 Package: pkg/wasm_network_demo_plugin.js"
else
    echo "❌ Failed to build WASM Network Demo Plugin"
    exit 1
fi
