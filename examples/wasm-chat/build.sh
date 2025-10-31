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
    # Kopiere WASM-Datei
    cp pkg/*_bg.wasm plugin.wasm 2>/dev/null || cp pkg/*.wasm plugin.wasm 2>/dev/null || true
    
    echo "✅ WASM Chat Plugin erfolgreich kompiliert!"
    echo "📦 Output: pkg/wasm_chat_plugin.wasm"
    echo "📋 Package: pkg/wasm_chat_plugin.js"
    echo ""
    echo "📝 Hinweis: Plugin muss vor der Verwendung signiert werden."
    echo "   Verwenden Sie: ../../tools/sign-examples.sh"
else
    echo "❌ Failed to build WASM Chat Plugin"
    exit 1
fi
