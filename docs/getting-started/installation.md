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
```
