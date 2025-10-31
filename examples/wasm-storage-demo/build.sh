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
    # Kopiere WASM-Datei mit expliziter Fehlerprüfung
    if ! cp pkg/*_bg.wasm plugin.wasm 2>/dev/null; then
        if ! cp pkg/*.wasm plugin.wasm 2>/dev/null; then
            echo "❌ Fehler: Keine WASM-Datei gefunden in pkg/"
            echo "   Erwartete Dateien: pkg/*_bg.wasm oder pkg/*.wasm"
            exit 1
        fi
    fi
    
    echo "✅ WASM Storage Demo Plugin erfolgreich kompiliert!"
    echo "📦 Output: pkg/wasm_storage_demo_plugin.wasm"
    echo "📋 Package: pkg/wasm_storage_demo_plugin.js"
    echo ""
    echo "📝 Hinweis: Plugin muss vor der Verwendung signiert werden."
    echo "   Verwenden Sie: ../../tools/sign-examples.sh"
else
    echo "❌ Failed to build WASM Storage Demo Plugin"
    exit 1
fi
