#!/bin/bash

# Keystore Creation Script for Connectias
# This script interactively creates an Android keystore for signing release APKs

set -e  # Exit on error

echo "=========================================="
echo "  Connectias Keystore Creation Script"
echo "=========================================="
echo ""
echo "This script will create a keystore file for signing your Android release APKs."
echo "You will need to provide the following information:"
echo "  - Keystore password (ASCII only - letters, numbers, basic symbols)"
echo "  - Key alias"
echo "  - Key password (ASCII only)"
echo "  - Certificate information (name, organization, etc.)"
echo ""
echo "⚠️  IMPORTANT: Keep the keystore file and passwords safe!"
echo "   Without them, you cannot update your app on Google Play."
echo ""
echo "⚠️  NOTE: Passwords must contain only ASCII characters."
echo "   Non-ASCII characters (Unicode symbols, emojis, etc.) are not supported."
echo ""

# Function to securely read password
read_password() {
    local prompt="$1"
    local password
    read -s -p "$prompt: " password
    echo ""
    echo "$password"
}

# Function to validate ASCII password
validate_ascii_password() {
    local password="$1"
    # Check if password contains only ASCII characters (0-127)
    if ! echo "$password" | LC_ALL=C grep -q '^[[:print:]]*$' || ! echo "$password" | LC_ALL=C grep -q '^[[:ascii:]]*$'; then
        return 1
    fi
    # Additional check: ensure no non-ASCII bytes
    if echo "$password" | od -An -tx1 | grep -q '[89abcdef]'; then
        return 1
    fi
    return 0
}

# Function to read password with confirmation
read_password_confirm() {
    local prompt="$1"
    local password
    local password_confirm
    
    while true; do
        password=$(read_password "$prompt")
        
        # Validate ASCII
        if ! validate_ascii_password "$password"; then
            echo "❌ Error: Password must contain only ASCII characters (letters, numbers, and basic symbols)."
            echo "   Non-ASCII characters (like special Unicode symbols) are not supported by Java keytool."
            echo "   Please use only standard ASCII characters (A-Z, a-z, 0-9, and common symbols)."
            echo ""
            continue
        fi
        
        password_confirm=$(read_password "Confirm $prompt")
        
        if [ "$password" = "$password_confirm" ]; then
            echo "$password"
            break
        else
            echo "❌ Passwords do not match. Please try again."
            echo ""
        fi
    done
}

# Function to read input with default value
read_input() {
    local prompt="$1"
    local default="$2"
    local value
    
    if [ -n "$default" ]; then
        read -p "$prompt [$default]: " value
        echo "${value:-$default}"
    else
        read -p "$prompt: " value
        echo "$value"
    fi
}

echo "📝 Step 1: Keystore Configuration"
echo "-----------------------------------"

# Read keystore password
KEYSTORE_PASSWORD=$(read_password_confirm "Enter keystore password")

# Read key alias
KEY_ALIAS=$(read_input "Enter key alias" "connectias-key")

# Read key password (can be same as keystore password)
echo ""
echo "Key password can be the same as keystore password or different."
USE_SAME_PASSWORD=$(read_input "Use same password for key? (y/n)" "y")

if [[ "$USE_SAME_PASSWORD" =~ ^[Yy]$ ]]; then
    KEY_PASSWORD="$KEYSTORE_PASSWORD"
else
    KEY_PASSWORD=$(read_password_confirm "Enter key password")
fi

echo ""
echo "📝 Step 2: Certificate Information"
echo "-----------------------------------"
echo "Enter the certificate information (used for app signing)"
echo ""

# Read certificate information
CN=$(read_input "Your name or organization name (CN)" "Connectias")
OU=$(read_input "Organizational Unit (OU)" "Development")
O=$(read_input "Organization (O)" "Ble1st")
L=$(read_input "City/Location (L)" "City")
ST=$(read_input "State/Province (ST)" "State")
C=$(read_input "Country Code (2 letters, e.g., DE, US)" "DE")

# Build distinguished name
DN="CN=$CN, OU=$OU, O=$O, L=$L, ST=$ST, C=$C"

echo ""
echo "📝 Step 3: Keystore File Location"
echo "-----------------------------------"

# Read keystore filename
KEYSTORE_FILENAME=$(read_input "Keystore filename" "connectias-release.jks")
KEYSTORE_PATH="$KEYSTORE_FILENAME"

# Check if file already exists
if [ -f "$KEYSTORE_PATH" ]; then
    echo ""
    echo "⚠️  Warning: File $KEYSTORE_PATH already exists!"
    OVERWRITE=$(read_input "Overwrite existing file? (y/n)" "n")
    
    if [[ ! "$OVERWRITE" =~ ^[Yy]$ ]]; then
        echo "❌ Aborted. Keystore creation cancelled."
        exit 1
    fi
fi

echo ""
echo "📝 Step 4: Validity Period"
echo "-----------------------------------"
VALIDITY=$(read_input "Validity period in days (default: 10000 = ~27 years)" "10000")

echo ""
echo "=========================================="
echo "  Summary"
echo "=========================================="
echo "Keystore file:     $KEYSTORE_PATH"
echo "Key alias:         $KEY_ALIAS"
echo "Validity:          $VALIDITY days"
echo "Distinguished Name: $DN"
echo ""
echo "⚠️  Please verify the information above."
echo ""

CONFIRM=$(read_input "Create keystore? (y/n)" "n")

if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
    echo "❌ Aborted. Keystore creation cancelled."
    exit 1
fi

echo ""
echo "🔨 Creating keystore..."
echo ""

# Create keystore using keytool
keytool -genkey -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "$DN"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Keystore created successfully!"
    echo ""
    
    # Verify keystore
    echo "🔍 Verifying keystore..."
    keytool -list -v -keystore "$KEYSTORE_PATH" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS" > /dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        echo "✅ Keystore verification successful!"
        echo ""
        
        # Create Base64 encoded version for GitHub
        echo "📦 Creating Base64 encoded version for GitHub..."
        BASE64_FILE="${KEYSTORE_FILENAME}.base64.txt"
        base64 -i "$KEYSTORE_PATH" > "$BASE64_FILE"
        
        if [ $? -eq 0 ]; then
            echo "✅ Base64 file created: $BASE64_FILE"
            echo ""
            
            # Display summary
            echo "=========================================="
            echo "  Keystore Creation Complete!"
            echo "=========================================="
            echo ""
            echo "📁 Files created:"
            echo "  - $KEYSTORE_PATH (Keystore file - KEEP THIS SAFE!)"
            echo "  - $BASE64_FILE (Base64 encoded for GitHub Secrets)"
            echo ""
            echo "🔐 Information for GitHub Secrets:"
            echo "  KEYSTORE_BASE64:   Content of $BASE64_FILE"
            echo "  KEYSTORE_PASSWORD: [Your keystore password]"
            echo "  KEY_ALIAS:         $KEY_ALIAS"
            echo "  KEY_PASSWORD:      [Your key password]"
            echo ""
            echo "⚠️  IMPORTANT SECURITY NOTES:"
            echo "  1. Keep $KEYSTORE_PATH in a secure location"
            echo "  2. Make backups of the keystore file"
            echo "  3. Never commit the keystore to Git"
            echo "  4. Store passwords securely (password manager)"
            echo "  5. The Base64 file contains the keystore - keep it secure too"
            echo ""
            echo "📋 Next steps:"
            echo "  1. Copy the content of $BASE64_FILE to GitHub Secret: KEYSTORE_BASE64"
            echo "  2. Add KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD to GitHub Secrets"
            echo "  3. Test the release build workflow"
            echo ""
            
            # Ask if user wants to display Base64 content
            SHOW_BASE64=$(read_input "Display Base64 content for GitHub Secret? (y/n)" "n")
            if [[ "$SHOW_BASE64" =~ ^[Yy]$ ]]; then
                echo ""
                echo "=========================================="
                echo "  KEYSTORE_BASE64 Secret Content"
                echo "=========================================="
                cat "$BASE64_FILE"
                echo ""
                echo "=========================================="
                echo ""
            fi
            
        else
            echo "⚠️  Warning: Could not create Base64 file, but keystore is valid."
        fi
    else
        echo "⚠️  Warning: Keystore created but verification failed."
    fi
else
    echo ""
    echo "❌ Error: Failed to create keystore!"
    exit 1
fi

echo ""
echo "✅ Done!"
