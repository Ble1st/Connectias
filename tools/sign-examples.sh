#!/bin/bash

# Script zum Signieren aller Example-Plugins

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TOOLS_DIR="$PROJECT_ROOT/tools/plugin-signer"
EXAMPLES_DIR="$PROJECT_ROOT/examples"

echo "🔐 Signiere Example-Plugins..."
echo ""

# Prüfe ob plugin-signer gebaut wurde
if [ ! -f "$TOOLS_DIR/target/release/plugin-signer" ]; then
    echo "❌ plugin-signer nicht gefunden. Baue Tool..."
    cd "$TOOLS_DIR"
    cargo build --release
    cd "$PROJECT_ROOT"
fi

SIGNER="$TOOLS_DIR/target/release/plugin-signer"

# Generiere Test-Schlüsselpaar falls nicht vorhanden
TEST_KEY="$TOOLS_DIR/test-key.der"
TEST_PUBLIC="$TOOLS_DIR/test-key-public.der"

if [ ! -f "$TEST_KEY" ]; then
    echo "📝 Generiere Test-Schlüsselpaar..."
    "$SIGNER" generate-key \
        --private-key "$TEST_KEY" \
        --public-key "$TEST_PUBLIC" \
        --key-size 2048
    echo "✅ Test-Schlüsselpaar generiert"
    echo ""
fi

# Signiere jedes Example-Plugin
sign_plugin() {
    local plugin_dir="$1"
    local plugin_name="$2"
    
    echo "📦 Signiere $plugin_name..."
    
    cd "$plugin_dir"
    
    # Prüfe ob plugin.wasm existiert
    if [ ! -f "plugin.wasm" ]; then
        echo "  ⚠️  plugin.wasm nicht gefunden. Überspringe..."
        return 0
    fi
    
    # Erstelle temporäres ZIP
    temp_zip=$(mktemp -t plugin-XXXXXX.zip)
    zip -q "$temp_zip" plugin.json plugin.wasm 2>/dev/null || zip -q "$temp_zip" plugin.json plugin.wasm || true
    
    # Signiere Plugin
    signed_zip="$plugin_dir/${plugin_name}-signed.zip"
    "$SIGNER" sign \
        --plugin "$temp_zip" \
        --private-key "$TEST_KEY" \
        --output "$signed_zip"
    
    rm -f "$temp_zip"
    
    echo "  ✅ Signiert: $signed_zip"
    echo ""
    
    cd "$PROJECT_ROOT"
}

# Signiere alle Example-Plugins
if [ -d "$EXAMPLES_DIR/wasm-hello" ]; then
    sign_plugin "$EXAMPLES_DIR/wasm-hello" "wasm-hello"
fi

if [ -d "$EXAMPLES_DIR/wasm-chat" ]; then
    sign_plugin "$EXAMPLES_DIR/wasm-chat" "wasm-chat"
fi

if [ -d "$EXAMPLES_DIR/wasm-storage-demo" ]; then
    sign_plugin "$EXAMPLES_DIR/wasm-storage-demo" "wasm-storage-demo"
fi

if [ -d "$EXAMPLES_DIR/wasm-network-demo" ]; then
    sign_plugin "$EXAMPLES_DIR/wasm-network-demo" "wasm-network-demo"
fi

echo "✅ Alle Example-Plugins signiert!"
echo ""
echo "📝 Öffentlicher Schlüssel für Connectias:"
echo "   $TEST_PUBLIC"
echo ""
echo "💡 Fügen Sie diesen öffentlichen Schlüssel in Connectias als vertrauenswürdigen Schlüssel hinzu."

