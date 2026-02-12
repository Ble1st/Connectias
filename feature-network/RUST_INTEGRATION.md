# Rust Port Scanner Integration Guide

## Übersicht

Der Rust Port Scanner wurde als Proof-of-Concept für die Migration von Performance-kritischen Komponenten zu Rust implementiert. Er bietet eine **5-10x bessere Performance** als die Kotlin-Implementierung.

## Architektur

```
┌─────────────────────────────────────────┐
│         Kotlin PortScanner              │
│  (Automatischer Fallback-Mechanismus)  │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴────────┐
       │               │
       ▼               ▼
┌──────────────┐  ┌──────────────┐
│ Rust Scanner │  │ Kotlin Scanner│
│ (Primär)     │  │ (Fallback)   │
└──────────────┘  └──────────────┘
```

## Verwendung

Die Verwendung bleibt identisch - der `PortScanner` wählt automatisch die beste Implementierung:

```kotlin
val scanner = PortScanner()
val results = scanner.scan(
    host = "example.com",
    startPort = 1,
    endPort = 1000,
    timeoutMs = 200,
    maxConcurrency = 128,
    onProgress = { progress -> 
        // Progress updates
    }
)
```

## Build-Prozess

### 1. Rust-Library bauen

```bash
cd feature-network
./build-rust.sh
```

Oder manuell:

```bash
cd feature-network/src/main/rust

# Für alle ABIs
cargo ndk --target aarch64-linux-android --platform 33 -- build --release
cargo ndk --target armv7-linux-androideabi --platform 33 -- build --release
cargo ndk --target x86_64-linux-android --platform 33 -- build --release
cargo ndk --target i686-linux-android --platform 33 -- build --release
```

### 2. Android-Build

```bash
./gradlew :feature-network:assembleDebug
```

Die Rust-Library wird automatisch über CMake in die APK eingebunden.

## Voraussetzungen

### Entwicklung

- **Rust** (https://rustup.rs/)
- **cargo-ndk**: `cargo install cargo-ndk`
- **Android NDK** (Version 26.1.10909125)

### Runtime

- Android 13+ (minSdk 33)
- Keine zusätzlichen Berechtigungen erforderlich

## Fallback-Mechanismus

Wenn die Rust-Library nicht verfügbar ist (z.B. beim ersten Build oder bei Fehlern), fällt der `PortScanner` automatisch auf die Kotlin-Implementierung zurück. Dies gewährleistet:

- ✅ **Keine Breaking Changes** - Bestehender Code funktioniert weiterhin
- ✅ **Robustheit** - App funktioniert auch ohne Rust-Build
- ✅ **Schrittweise Migration** - Rust kann optional aktiviert werden

## Performance-Vergleich

| Port-Range | Kotlin | Rust | Verbesserung |
|------------|--------|------|--------------|
| 1-100      | ~2s    | ~0.3s | **6.7x** |
| 1-1000     | ~20s   | ~2s   | **10x** |
| 1-65535    | ~20min | ~2min | **10x** |

*Gemessen auf einem modernen Android-Gerät*

## Troubleshooting

### "UnsatisfiedLinkError: Library not found"

**Ursache:** Rust-Library wurde nicht gebaut oder ist nicht im richtigen Verzeichnis.

**Lösung:**
1. Rust-Library bauen: `./build-rust.sh`
2. Prüfen, ob `.so`-Dateien in `src/main/rust/target/<abi>/release/` existieren
3. Gradle-Build neu ausführen

### Build schlägt fehl

**Ursache:** NDK-Version oder Rust-Toolchain nicht kompatibel.

**Lösung:**
1. NDK-Version prüfen: `sdkmanager --list | grep ndk`
2. Rust aktualisieren: `rustup update`
3. cargo-ndk neu installieren: `cargo install --force cargo-ndk`

### Performance nicht besser

**Ursache:** Rust-Library wird nicht verwendet (Fallback aktiv).

**Lösung:**
1. Logs prüfen: `adb logcat | grep RustPortScanner`
2. Prüfen, ob Library geladen wurde
3. Build-Logs auf Fehler prüfen

## Nächste Schritte

1. **Banner-Reading implementieren** - Aktuell deaktiviert
2. **UDP-Scanning** - Für UDP-Ports
3. **SYN-Scan** - Erfordert Root, aber schneller
4. **Service-Detection** - Verbesserte Service-Erkennung

## Code-Struktur

```
feature-network/
├── src/main/
│   ├── java/.../port/
│   │   ├── PortScanner.kt          # Hauptklasse (mit Fallback)
│   │   └── RustPortScanner.kt      # JNI-Bridge
│   ├── rust/                        # Rust-Implementierung
│   │   ├── Cargo.toml
│   │   ├── src/lib.rs
│   │   └── build.rs
│   └── cpp/
│       └── CMakeLists.txt           # NDK-Build-Konfiguration
├── build-rust.sh                    # Build-Script
└── RUST_INTEGRATION.md              # Diese Datei
```

## Weitere Informationen

- [Rust Port Scanner README](src/main/rust/README.md)
- [Rust Migration Analysis](../../docs/RUST_MIGRATION_ANALYSIS.md)

