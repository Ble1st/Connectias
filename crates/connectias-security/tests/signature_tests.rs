use connectias_security::signature::{SignatureVerifier, PublicKey};
use std::path::Path;
use tempfile::TempDir;
use std::fs::File;
use std::io::Write;
use zip::ZipWriter;
use zip::write::FileOptions;
use ring::signature::{RsaKeyPair, RSA_PKCS1_SHA256, KeyPair};
use ring::rand::SystemRandom;

// Helper: Erstellt ein RSA Key Pair mit ring (für Option A - komplett ring)
#[cfg(test)]
fn create_ring_key_pair() -> (RsaKeyPair, Vec<u8>) {
    // Generiere RSA Key Pair mit ring
    let rng = SystemRandom::new();
    
    // ring 0.17 API: generate_pkcs8 benötigt eine Closure für Random
    let mut pkcs8_vec = vec![0u8; 2048 * 3]; // Genug Platz für PKCS#8
    let mut filled = 0;
    
    // Generiere Key Pair - verwende rsa Library als Fallback für Tests wenn ring zu komplex ist
    // ABER: Für Option A wollen wir komplett ring verwenden
    // Workaround: Verwende einen statischen Test-Key oder generiere mit rsa und konvertiere
    
    // Einfacherer Ansatz für Tests: Verwende rsa zum Generieren, aber signiere/verifiziere mit ring
    let mut rng_rsa = rand::thread_rng();
    let private_key_rsa = rsa::RsaPrivateKey::new(&mut rng_rsa, 2048).expect("Failed to generate key");
    
    // Konvertiere rsa Private Key zu PKCS#8 DER
    use rsa::pkcs8::EncodePrivateKey;
    let pkcs8_doc = private_key_rsa.to_pkcs8_der().expect("Failed to encode to PKCS#8");
    let pkcs8_bytes = pkcs8_doc.as_bytes();
    
    // Lade als ring Key Pair
    let key_pair = RsaKeyPair::from_pkcs8(pkcs8_bytes).expect("Failed to create ring key pair");
    
    // Extrahiere Public Key (SubjectPublicKeyInfo DER Format)
    let public_key_bytes = key_pair.public_key().as_ref().to_vec();
    
    (key_pair, public_key_bytes)
}

#[test]
fn test_signature_verifier_creation() {
    let verifier = SignatureVerifier::new();
    assert_eq!(verifier.trusted_keys_count(), 0);
}

#[test]
fn test_add_trusted_key() {
    let mut verifier = SignatureVerifier::new();
    
    // Generate test key pair mit ring (Option A)
    let (_key_pair, public_key_der) = create_ring_key_pair();
    let public_key_ring = PublicKey::from_der(public_key_der);
    
    verifier.add_trusted_key(public_key_ring);
    assert_eq!(verifier.trusted_keys_count(), 1);
}

#[test]
fn test_verify_plugin_with_valid_signature() {
    let mut verifier = SignatureVerifier::new();
    
    // Generate test key pair mit ring (Option A)
    let (key_pair, public_key_der) = create_ring_key_pair();
    let public_key_ring = PublicKey::from_der(public_key_der);
    verifier.add_trusted_key(public_key_ring);
    
    // Create a test plugin ZIP with signature
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let plugin_path = temp_dir.path().join("test_plugin.zip");
    
    create_signed_plugin_zip(&plugin_path, &key_pair).expect("Failed to create signed plugin");
    
    // Verify the plugin
    let result = verifier.verify_plugin(&plugin_path);
    if result.is_err() {
        eprintln!("Verification error: {:?}", result.as_ref().unwrap_err());
    }
    assert!(result.is_ok(), "Verification failed: {:?}", result.as_ref().unwrap_err());
    assert!(result.unwrap());
}

#[test]
fn test_verify_plugin_with_invalid_signature() {
    let mut verifier = SignatureVerifier::new();
    
    // Generate test key pair mit ring (Option A)
    let (_key_pair, public_key_der) = create_ring_key_pair();
    let public_key_ring = PublicKey::from_der(public_key_der);
    verifier.add_trusted_key(public_key_ring);
    
    // Create a test plugin ZIP without signature
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let plugin_path = temp_dir.path().join("test_plugin.zip");
    
    create_unsigned_plugin_zip(&plugin_path).expect("Failed to create unsigned plugin");
    
    // Verify the plugin should fail
    let result = verifier.verify_plugin(&plugin_path);
    assert!(result.is_err());
}

#[test]
fn test_verify_plugin_with_wrong_signature() {
    let mut verifier = SignatureVerifier::new();
    
    // Generate two different key pairs mit ring (Option A)
    let (key_pair1, public_key_der1) = create_ring_key_pair();
    let (key_pair2, _) = create_ring_key_pair(); // Different key
    
    let public_key_ring1 = PublicKey::from_der(public_key_der1);
    verifier.add_trusted_key(public_key_ring1);
    
    // Create a test plugin ZIP signed with different key
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let plugin_path = temp_dir.path().join("test_plugin.zip");
    
    // Sign with key_pair2 (different from trusted key_pair1)
    create_signed_plugin_zip(&plugin_path, &key_pair2).expect("Failed to create signed plugin");
    
    // Verify the plugin should fail
    let result = verifier.verify_plugin(&plugin_path);
    assert!(result.is_err());
}

#[test]
fn test_verify_plugin_nonexistent_file() {
    let verifier = SignatureVerifier::new();
    let result = verifier.verify_plugin(Path::new("/nonexistent/plugin.zip"));
    assert!(result.is_err());
}

#[test]
fn test_verify_plugin_invalid_zip() {
    let verifier = SignatureVerifier::new();
    
    // Create a file that's not a valid ZIP
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let plugin_path = temp_dir.path().join("invalid.zip");
    std::fs::write(&plugin_path, "This is not a ZIP file").expect("Failed to write test file");
    
    let result = verifier.verify_plugin(&plugin_path);
    assert!(result.is_err());
}

// Helper functions for creating test plugin ZIPs

fn create_signed_plugin_zip(path: &Path, key_pair: &RsaKeyPair) -> Result<(), Box<dyn std::error::Error>> {
    let plugin_json = r#"{
        "id": "com.test.plugin",
        "name": "Test Plugin",
        "version": "1.0.0",
        "author": "Test Author",
        "description": "Test plugin for signature verification",
        "min_core_version": "1.0.0",
        "max_core_version": null,
        "permissions": ["Storage"],
        "entry_point": "plugin.wasm",
        "dependencies": null
    }"#;
    let wasm_content = b"dummy wasm content";
    
    // OPTION A: Signatur-Format geändert - signiere die Nachricht direkt (nicht den Hash)
    // ring erwartet die Nachricht und hash intern, also müssen wir die Nachricht signieren
    let mut message = Vec::new();
    
    // Sortiert nach Pfad: plugin.json kommt vor plugin.wasm
    // Nachricht: Pfad + | + Größe + | + Content (gleiches Format wie Verifikation)
    message.extend_from_slice(b"plugin.json");
    message.extend_from_slice(b"|");
    message.extend_from_slice(plugin_json.len().to_string().as_bytes());
    message.extend_from_slice(b"|");
    message.extend_from_slice(plugin_json.as_bytes());
    
    message.extend_from_slice(b"plugin.wasm");
    message.extend_from_slice(b"|");
    message.extend_from_slice(wasm_content.len().to_string().as_bytes());
    message.extend_from_slice(b"|");
    message.extend_from_slice(wasm_content);
    
    // Create signature mit ring (Option A) - signiere die Nachricht direkt
    // ring's sign() erwartet die Nachricht und hash intern mit SHA-256 (RSA_PKCS1_SHA256)
    // Das ist kompatibel mit ring's verify()!
    let rng = SystemRandom::new();
    let signature_len = key_pair.public().modulus_len();
    let mut signature = vec![0u8; signature_len];
    key_pair.sign(&RSA_PKCS1_SHA256, &rng, &message, &mut signature)
        .map_err(|e| Box::new(std::io::Error::new(std::io::ErrorKind::Other, format!("Ring signing failed: {:?}", e))))?;
    let signature_b64 = base64::Engine::encode(&base64::engine::general_purpose::STANDARD, &signature);
    
    // Create ZIP with all files
    let file = File::create(path)?;
    let mut zip = ZipWriter::new(file);
    
    // Add plugin.json
    zip.start_file("plugin.json", FileOptions::default())?;
    zip.write_all(plugin_json.as_bytes())?;
    
    // Add plugin.wasm
    zip.start_file("plugin.wasm", FileOptions::default())?;
    zip.write_all(wasm_content)?;
    
    // Add signature
    zip.start_file("META-INF/SIGNATURE.RSA", FileOptions::default())?;
    zip.write_all(signature_b64.as_bytes())?;
    
    zip.finish()?;
    Ok(())
}

fn create_unsigned_plugin_zip(path: &Path) -> Result<(), Box<dyn std::error::Error>> {
    let file = File::create(path)?;
    let mut zip = ZipWriter::new(file);
    
    // Add plugin.json
    zip.start_file("plugin.json", FileOptions::default())?;
    let plugin_json = r#"{
        "id": "com.test.plugin",
        "name": "Test Plugin",
        "version": "1.0.0",
        "author": "Test Author",
        "description": "Test plugin without signature",
        "min_core_version": "1.0.0",
        "max_core_version": null,
        "permissions": ["Storage"],
        "entry_point": "plugin.wasm",
        "dependencies": null
    }"#;
    zip.write_all(plugin_json.as_bytes())?;
    
    // Add plugin.wasm (dummy content)
    zip.start_file("plugin.wasm", FileOptions::default())?;
    zip.write_all(b"dummy wasm content")?;
    
    zip.finish()?;
    Ok(())
}
