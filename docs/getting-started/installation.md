# Installation Guide

## Prerequisites

- Rust 1.70+
- Flutter 3.0+
- Android SDK (for Android builds)

## Steps

```bash
# Clone repository
$ git clone https://github.com/connectias/connectias.git && cd connectias

# Install Rust toolchain
$ rustup default stable

# Install Flutter dependencies
$ flutter doctor

# Build Rust core (release)
$ cargo build --release

# Build Flutter (Android debug)
$ flutter build apk --debug

# Release Build (erfordert Keystore)
$ cd android/keystore
$ ./generate-keystore.sh
$ cd ../..
$ flutter build apk --release
$ flutter build appbundle --release
```

## Release-Signing Setup

Für Release-Builds muss ein Keystore generiert werden:

```bash
# Keystore generieren
$ cd android/keystore
$ ./generate-keystore.sh

# Umgebungsvariablen setzen (optional, für sichere Passwörter)
$ export CONNECTIAS_KEYSTORE_PASSWORD="IhrSicheresPasswort"
$ export CONNECTIAS_KEY_PASSWORD="IhrSicheresPasswort"

# Release-Build erstellen
$ flutter build apk --release
$ flutter build appbundle --release
```

### Sicherheitshinweise

- **Keystore sicher aufbewahren**: Der Keystore ist für alle zukünftigen Updates erforderlich
- **Backups erstellen**: Mehrere sichere Backups an verschiedenen Orten
- **Passwörter sicher**: Verwenden Sie starke, einzigartige Passwörter
- **Niemals committen**: Keystore-Dateien sind in .gitignore ausgeschlossen
