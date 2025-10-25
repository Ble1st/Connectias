use connectias_security::signature::SignatureVerifier;
use sha2::Digest;
use std::path::Path;
use tempfile::TempDir;
use std::fs::File;
use std::io::Write;
use zip::ZipWriter;
use zip::write::FileOptions;

#[test]
fn test_signature_verifier_creation() {
    let verifier = SignatureVerifier::new();
    assert_eq!(verifier.trusted_keys_ref().len(), 0);
}

#[test]
fn test_add_trusted_key() {
    let mut verifier = SignatureVerifier::new();
    
    // Generate a test RSA key pair
    let mut rng = rand::thread_rng();
    let bits = 2048;
    let private_key = rsa::RsaPrivateKey::new(&mut rng, bits).expect("Failed to generate private key");
    let public_key = private_key.to_public_key();
    
    verifier.add_trusted_key(public_key);
    assert_eq!(verifier.trusted_keys_ref().len(), 1);
}

#[test]
fn test_verify_plugin_with_valid_signature() {
    let mut verifier = SignatureVerifier::new();
    
    // Generate test key pair
    let mut rng = rand::thread_rng();
    let bits = 2048;
    let private_key = rsa::RsaPrivateKey::new(&mut rng, bits).expect("Failed to generate private key");
    let public_key = private_key.to_public_key();
    verifier.add_trusted_key(public_key);
    
    // Create a test plugin ZIP with signature
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let plugin_path = temp_dir.path().join("test_plugin.zip");
    
    create_signed_plugin_zip(&plugin_path, &private_key).expect("Failed to create signed plugin");
    
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
    
    // Generate test key pair
    let mut rng = rand::thread_rng();
    let bits = 2048;
    let private_key = rsa::RsaPrivateKey::new(&mut rng, bits).expect("Failed to generate private key");
    let public_key = private_key.to_public_key();
    verifier.add_trusted_key(public_key);
    
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
    
    // Generate two different key pairs
    let mut rng = rand::thread_rng();
    let bits = 2048;
    let private_key1 = rsa::RsaPrivateKey::new(&mut rng, bits).expect("Failed to generate private key 1");
    let private_key2 = rsa::RsaPrivateKey::new(&mut rng, bits).expect("Failed to generate private key 2");
    let public_key1 = private_key1.to_public_key();
    
    verifier.add_trusted_key(public_key1);
    
    // Create a test plugin ZIP signed with different key
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let plugin_path = temp_dir.path().join("test_plugin.zip");
    
    create_signed_plugin_zip(&plugin_path, &private_key2).expect("Failed to create signed plugin");
    
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

fn create_signed_plugin_zip(path: &Path, private_key: &rsa::RsaPrivateKey) -> Result<(), Box<dyn std::error::Error>> {
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
    
    // Calculate hash over content (matching verification logic - deterministisch)
    let mut hasher = sha2::Sha256::new();
    
    // Sortiert nach Pfad: plugin.json kommt vor plugin.wasm
    // Hash: Pfad + | + Größe + | + Content
    hasher.update(b"plugin.json");
    hasher.update(b"|");
    hasher.update(plugin_json.len().to_string().as_bytes());
    hasher.update(b"|");
    hasher.update(plugin_json.as_bytes());
    
    hasher.update(b"plugin.wasm");
    hasher.update(b"|");
    hasher.update(wasm_content.len().to_string().as_bytes());
    hasher.update(b"|");
    hasher.update(wasm_content);
    
    let hash = hasher.finalize();
    
    // Create signature
    let padding = rsa::Pkcs1v15Sign::new::<sha2::Sha256>();
    let signature = private_key.sign(padding, &hash)?;
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
