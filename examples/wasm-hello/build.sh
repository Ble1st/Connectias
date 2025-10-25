#!/bin/bash

# Build WASM Plugin Example

echo "🦀 Building WASM Hello Plugin..."

# Check if wasm-pack is installed
if ! command -v wasm-pack &> /dev/null; then
    echo "❌ wasm-pack is not installed. Installing..."
    cargo install wasm-pack
fi

# Build the WASM plugin
wasm-pack build --target web --out-dir pkg

if [ $? -ne 0 ]; then
    echo "❌ Failed to build WASM plugin"
    exit 1
fi

# Copy the generated WASM file
cp pkg/wasm_hello_plugin_bg.wasm plugin.wasm

echo "✅ WASM plugin built successfully!"
echo "📁 Output: plugin.wasm"

