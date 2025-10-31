# Plugin Signer Tool

Tool zum Signieren von Connectias-Plugins mit RSA PKCS#1 v1.5 + SHA-256.

## Installation

```bash
cd tools/plugin-signer
cargo build --release
```

Das kompilierte Tool befindet sich unter `target/release/plugin-signer`.

## Verwendung

### 1. RSA-Schlüsselpaar generieren

```bash
./target/release/plugin-signer generate-key \
    --private-key example-private-key.der \
    --public-key example-public-key.der \
    --key-size 2048
```

### 2. Plugin signieren

```bash
./target/release/plugin-signer sign \
    --plugin my-plugin.zip \
    --private-key example-private-key.der \
    --output my-plugin-signed.zip
```

### 3. Öffentlichen Schlüssel extrahieren

```bash
./target/release/plugin-signer extract-key \
    --private-key example-private-key.der \
    --output public-key.der
```

Der extrahierte öffentliche Schlüssel kann dann in Connectias als vertrauenswürdiger Schlüssel hinzugefügt werden.

## Signatur-Format

Seit Version 2.0 verwendet Connectias das neue Signatur-Format:

- **Nachricht**: Konkatenation aller Plugin-Dateien (sortiert nach Pfad)
  - Format: `Pfad|Größe|Inhalt` für jede Datei
- **Signatur**: RSA PKCS#1 v1.5 mit SHA-256
  - `ring` signiert die **Message direkt** (hash intern)
  - Base64-kodiert in `META-INF/SIGNATURE.RSA` gespeichert

## Beispiel-Workflow

```bash
# 1. Schlüsselpaar generieren (einmalig)
./target/release/plugin-signer generate-key \
    --private-key dev-key.der \
    --public-key dev-key-public.der

# 2. Plugin als ZIP erstellen
cd ../../examples/wasm-hello
zip -r plugin.zip plugin.json plugin.wasm

# 3. Plugin signieren
../../tools/plugin-signer/target/release/plugin-signer sign \
    --plugin plugin.zip \
    --private-key ../../tools/plugin-signer/dev-key.der \
    --output plugin-signed.zip

# 4. Öffentlichen Schlüssel in Connectias hinzufügen
# (Siehe Connectias-Dokumentation für Details)
```

## Kompatibilität

- **Connectias Core Version 2.0+**: Unterstützt das neue Signatur-Format
- **Ältere Versionen**: Nicht kompatibel (Breaking Change)

Plugins müssen mit diesem Tool (oder kompatiblem Tool) neu signiert werden, um mit Connectias 2.0+ zu funktionieren.

