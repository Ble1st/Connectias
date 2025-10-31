#!/bin/bash

# Connectias Release Keystore Generator
# Generiert einen sicheren Keystore für Release-Builds
#
# Benötigte Environment Variables:
#   CONNECTIAS_KEYSTORE_PASSWORD - Passwort für den Keystore
#   CONNECTIAS_KEY_PASSWORD - Passwort für den privaten Schlüssel
#
# Beispiel:
#   export CONNECTIAS_KEYSTORE_PASSWORD='your_keystore_password'
#   export CONNECTIAS_KEY_PASSWORD='your_key_password'
#   ./generate-keystore.sh

set -e

KEYSTORE_DIR="$(dirname "$0")"
KEYSTORE_FILE="$KEYSTORE_DIR/connectias-release-key.jks"
KEY_ALIAS="connectias-release"

echo "🔐 Connectias Release Keystore Generator"
echo "========================================"

# Prüfe ob Keystore bereits existiert
if [ -f "$KEYSTORE_FILE" ]; then
    echo "⚠️  Keystore bereits vorhanden: $KEYSTORE_FILE"
    echo "   Möchten Sie ihn überschreiben? (y/N)"
    read -r response
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo "❌ Abgebrochen."
        exit 1
    fi
    rm -f "$KEYSTORE_FILE"
fi

echo "📝 Generiere neuen Keystore..."

# Validiere Environment Variables
if [ -z "$CONNECTIAS_KEYSTORE_PASSWORD" ]; then
    echo "❌ Fehler: CONNECTIAS_KEYSTORE_PASSWORD Environment Variable ist nicht gesetzt"
    echo "   Bitte setzen Sie: export CONNECTIAS_KEYSTORE_PASSWORD='your_keystore_password'"
    exit 1
fi

if [ -z "$CONNECTIAS_KEY_PASSWORD" ]; then
    echo "❌ Fehler: CONNECTIAS_KEY_PASSWORD Environment Variable ist nicht gesetzt"
    echo "   Bitte setzen Sie: export CONNECTIAS_KEY_PASSWORD='your_key_password'"
    exit 1
fi

# Generiere Keystore mit sicheren Einstellungen
keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storetype JKS \
    -dname "CN=Connectias, OU=Security, O=Connectias, L=Berlin, ST=Berlin, C=DE" \
    -storepass "$CONNECTIAS_KEYSTORE_PASSWORD" \
    -keypass "$CONNECTIAS_KEY_PASSWORD"

echo "✅ Keystore erfolgreich generiert!"
echo ""
echo "📋 Keystore-Details:"
echo "   Datei: $KEYSTORE_FILE"
echo "   Alias: $KEY_ALIAS"
echo "   Algorithmus: RSA 4096-bit"
echo "   Gültigkeit: 10000 Tage (~27 Jahre)"
echo ""
echo "🔒 Sicherheitshinweise:"
echo "   1. Bewahren Sie den Keystore sicher auf!"
echo "   2. Erstellen Sie Backups an sicheren Orten"
echo "   3. Verwenden Sie Umgebungsvariablen für Passwörter:"
echo "      export CONNECTIAS_KEYSTORE_PASSWORD='IhrSicheresPasswort'"
echo "      export CONNECTIAS_KEY_PASSWORD='IhrSicheresPasswort'"
echo ""
echo "🚀 Sie können jetzt Release-Builds erstellen:"
echo "   flutter build apk --release"
echo "   flutter build appbundle --release"
