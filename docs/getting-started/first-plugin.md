# First Plugin Tutorial

## Creating a WASM Plugin

### 1. Basic Plugin Structure

```rust
// src/lib.rs
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub fn plugin_init() -> i32 {
    0 // Success
}

#[wasm_bindgen]
pub fn plugin_execute(command: &str, args: &str) -> String {
    match command {
        "hello" => "Hello from WASM plugin!".to_string(),
        "add" => {
            // Parse args and perform addition
            "Result: 42".to_string()
        },
        _ => "Unknown command".to_string(),
    }
}

#[wasm_bindgen]
pub fn plugin_cleanup() {
    // Cleanup resources
}
```

### 2. Build Configuration

```toml
# Cargo.toml
[package]
name = "my-connectias-plugin"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib"]

[dependencies]
wasm-bindgen = "0.2"
```

### 3. Build the Plugin

```bash
# Install wasm-pack
cargo install wasm-pack

# Build for wasm32-unknown-unknown
wasm-pack build --target web --out-dir pkg
```

### 4. Build and Sign Plugin

```bash
# Build the plugin
wasm-pack build --target web --out-dir pkg

# Create ZIP package
zip plugin.zip plugin.json pkg/*.wasm

# Sign the plugin (required for Connectias 2.0+)
cd ../../tools/plugin-signer
cargo build --release
./target/release/plugin-signer sign \
    --plugin ../../examples/my-plugin/plugin.zip \
    --private-key dev-key.der \
    --output ../../examples/my-plugin/plugin-signed.zip
```

**Wichtig**: Alle Plugins müssen vor der Verwendung signiert werden. Siehe [Plugin Development Guide](../guides/plugin-development.md#2-plugin-signing) für Details.

### 5. Load in Connectias

```dart
final service = ConnectiasService();
await service.init();

// Load the signed plugin (must be a ZIP file)
final pluginId = await service.loadPlugin('/path/to/plugin-signed.zip');

// Execute commands
final result1 = await service.executePlugin(pluginId, 'hello', {});
final result2 = await service.executePlugin(pluginId, 'add', {'a': '20', 'b': '22'});

print('Hello result: $result1');
print('Add result: $result2');

// Cleanup
await service.dispose();
```

## Plugin Best Practices

- Always implement `plugin_init()`, `plugin_execute()`, and `plugin_cleanup()`
- Handle errors gracefully
- Use minimal memory footprint
- Follow security guidelines
- **Sign all plugins** before distribution (required for Connectias 2.0+)

## Next Steps

- [Plugin Development Guide](../guides/plugin-development.md)
- [Security Guidelines](../security/security-guidelines.md)
