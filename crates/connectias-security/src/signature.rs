use ring::signature::{UnparsedPublicKey, RSA_PKCS1_2048_8192_SHA256};
use sha2::{Sha256, Digest};
use std::path::Path;
use std::fs::File;
use zip::ZipArchive;
use std::io::Read;

/// Liste der Signatur-Dateinamen, die beim Hashing ausgeschlossen werden
const SIGNATURE_FILES: &[&str] = &[
    "META-INF/SIGNATURE.RSA",
    "META-INF/SIGNATURE.SF",
    "META-INF/SIGNATURE.DSA",
    "signature.pem",
    "signature.sig"
];

/// Public Key im DER-Format (PKCS#1 SubjectPublicKeyInfo)
#[derive(Clone)]
pub struct PublicKey {
    der: Vec<u8>,
}

impl PublicKey {
    /// Erstellt einen neuen Public Key aus DER-encoded Daten
    pub fn from_der(der: Vec<u8>) -> Self {
        Self { der }
    }

    /// Gibt den DER-encoded Public Key zurück
    pub fn as_der(&self) -> &[u8] {
        &self.der
    }
}

pub struct SignatureVerifier {
    public_keys: Vec<PublicKey>,
}

impl SignatureVerifier {
    pub fn new() -> Self {
        Self {
            public_keys: Vec::new(),
        }
    }

    /// Fügt einen vertrauenswürdigen Public Key hinzu
    /// Der Key muss im DER-Format (PKCS#1 SubjectPublicKeyInfo) sein
    pub fn add_trusted_key(&mut self, public_key: PublicKey) {
        self.public_keys.push(public_key);
    }

    /// Fügt einen vertrauenswürdigen Public Key aus DER-Format hinzu
    pub fn add_trusted_key_der(&mut self, der: Vec<u8>) {
        self.public_keys.push(PublicKey::from_der(der));
    }

    /// Gibt eine Kopie der vertrauenswürdigen Schlüssel zurück
    pub fn trusted_keys(&self) -> Vec<PublicKey> {
        self.public_keys.clone()
    }

    /// Gibt die Anzahl der vertrauenswürdigen Schlüssel zurück
    pub fn trusted_keys_count(&self) -> usize {
        self.public_keys.len()
    }

    /// Gibt eine Referenz auf die vertrauenswürdigen Schlüssel zurück
    pub fn trusted_keys_ref(&self) -> &[PublicKey] {
        &self.public_keys
    }

    pub fn verify_plugin(&self, plugin_path: &Path) -> Result<bool, super::SecurityError> {
        // 1. ZIP öffnen und Signatur extrahieren
        let file = File::open(plugin_path)
            .map_err(|e| super::SecurityError::SignatureVerificationFailed(format!("Failed to open plugin file: {}", e)))?;
        
        let mut archive = ZipArchive::new(file)
            .map_err(|e| super::SecurityError::SignatureVerificationFailed(format!("Failed to read ZIP archive: {}", e)))?;
        
        // 2. Signatur-Datei finden
        let signature_data = self.extract_signature_from_zip(&mut archive)?;
        
        // 3. Hash über alle Dateien außer Signatur berechnen (deterministisch)
        // Sammle alle Dateien mit Metadaten
        let mut file_entries: Vec<(String, usize, usize)> = Vec::new();
        for i in 0..archive.len() {
            let file = archive.by_index(i)
                .map_err(|e| super::SecurityError::SignatureVerificationFailed(format!("Failed to read file from ZIP: {}", e)))?;
            
            let file_name = file.name().to_string();
            // Skip signature files
            if SIGNATURE_FILES.contains(&file_name.as_str()) {
                continue;
            }
            
            // Normalisiere Pfad (entferne ./ und \\ )
            let normalized_path = file_name.replace("\\", "/").trim_start_matches("./").to_string();
            let size = file.size() as usize;
            file_entries.push((normalized_path, size, i));
        }
        
        // Sortiere nach Pfadnamen für Determinismus
        file_entries.sort_by(|a, b| a.0.cmp(&b.0));
        
        // Validiere Required Files
        let has_plugin_json = file_entries.iter().any(|(path, _, _)| path == "plugin.json");
        let has_plugin_wasm = file_entries.iter().any(|(path, _, _)| path.ends_with(".wasm") || path == "plugin.wasm");
        
        if !has_plugin_json {
            return Err(super::SecurityError::SignatureVerificationFailed("Missing required file: plugin.json".to_string()));
        }
        if !has_plugin_wasm {
            return Err(super::SecurityError::SignatureVerificationFailed("Missing required file: plugin.wasm or *.wasm".to_string()));
        }
        
        // Berechne Hash über alle Dateien (OPTION A: Signatur-Format geändert)
        // Für ring müssen wir die Nachricht berechnen, da ring intern hasht
        // ABER: Da rsa den Hash signiert, müssen wir einen Workaround verwenden:
        // Wir berechnen die Nachricht UND den Hash, und verwenden die Nachricht für ring
        let mut message = Vec::new();
        let mut hasher = Sha256::new();
        
        for (path, size, index) in &file_entries {
            // Nachricht: Pfad + Separator + Größe + Separator + Content
            message.extend_from_slice(path.as_bytes());
            message.extend_from_slice(b"|");
            message.extend_from_slice(size.to_string().as_bytes());
            message.extend_from_slice(b"|");
            
            let mut file = archive.by_index(*index)
                .map_err(|e| super::SecurityError::SignatureVerificationFailed(format!("Failed to reopen file: {}", e)))?;
            let mut content = Vec::new();
            file.read_to_end(&mut content)
                .map_err(|e| super::SecurityError::SignatureVerificationFailed(format!("Failed to read file content: {}", e)))?;
            
            if content.is_empty() && path == "plugin.json" {
                return Err(super::SecurityError::SignatureVerificationFailed("plugin.json is empty".to_string()));
            }
            
            message.extend_from_slice(&content);
            
            // Hash für Debugging (ring hash intern)
            hasher.update(path.as_bytes());
            hasher.update(b"|");
            hasher.update(size.to_string().as_bytes());
            hasher.update(b"|");
            hasher.update(&content);
        }
        
        // 4. Mit jedem trusted key verifizieren
        for public_key in &self.public_keys {
            // Versuche Signatur zu dekodieren (Base64)
            let signature_bytes = match base64::Engine::decode(&base64::engine::general_purpose::STANDARD, &signature_data) {
                Ok(bytes) => bytes,
                Err(e) => {
                    log::warn!("Failed to decode signature as base64: {}", e);
                    continue;
                }
            };
            
            // Verifiziere Signatur gegen Nachricht mit PKCS1v15 SHA-256 (ring verwendet constant-time)
            // ring's UnparsedPublicKey verwendet constant-time Verifikation, was sicher gegen Timing-Angriffe ist
            // OPTION A: Signatur-Format geändert - Plugins signieren die Nachricht direkt (ring hash intern)
            let public_key_der = public_key.as_der();
            
            // Erstelle UnparsedPublicKey für RSA PKCS#1 v1.5 mit SHA-256
            let public_key_unparsed = UnparsedPublicKey::new(&RSA_PKCS1_2048_8192_SHA256, public_key_der);
            
            // ring's verify() erwartet die Nachricht (ring hash intern automatisch mit SHA-256)
            match public_key_unparsed.verify(&message, &signature_bytes) {
                Ok(_) => {
                    log::info!("Signature verification successful (ring constant-time)");
                    return Ok(true);
                }
                Err(e) => {
                    log::debug!("Signature verification failed: {:?}, message_len: {}, sig_len: {}, key_der_len: {}", 
                               e, message.len(), signature_bytes.len(), public_key_der.len());
                    continue;
                }
            }
        }
        
        Err(super::SecurityError::SignatureVerificationFailed("No trusted signature found".to_string()))
    }

    fn extract_signature_from_zip(&self, archive: &mut ZipArchive<File>) -> Result<Vec<u8>, super::SecurityError> {
        // Versuche verschiedene Signatur-Dateien zu finden
        for sig_file in SIGNATURE_FILES {
            if let Ok(mut file) = archive.by_name(sig_file) {
                let mut signature_data = Vec::new();
                file.read_to_end(&mut signature_data)
                    .map_err(|e| super::SecurityError::SignatureVerificationFailed(format!("Failed to read signature file {}: {}", sig_file, e)))?;
                
                return Ok(signature_data);
            }
        }

        Err(super::SecurityError::SignatureVerificationFailed("No signature file found in plugin".to_string()))
    }
}
