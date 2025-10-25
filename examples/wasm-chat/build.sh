#!/bin/bash

echo "🦀 Building WASM Chat Plugin..."

# Prüfe ob wasm-pack installiert ist
if ! command -v wasm-pack &> /dev/null; then
    echo "❌ wasm-pack is not installed. Installing..."
    cargo install wasm-pack
fi

# Kompiliere zu WASM
wasm-pack build --target web --out-dir pkg

if [ $? -eq 0 ]; then
    echo "✅ WASM Chat Plugin erfolgreich kompiliert!"
    echo "📦 Output: pkg/wasm_chat_plugin.wasm"
    echo "📋 Package: pkg/wasm_chat_plugin.js"
else
    echo "❌ Failed to build WASM Chat Plugin"
    exit 1
fi
