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

### 4. Load in Connectias

```dart
final service = ConnectiasService();
await service.init();

// Load the plugin
final pluginId = await service.loadPlugin('/path/to/my-plugin.wasm');

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

## Next Steps

- [Plugin Development Guide](../guides/plugin-development.md)
- [Security Guidelines](../security/security-guidelines.md)
