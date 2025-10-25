use rsa::RsaPublicKey;
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

pub struct SignatureVerifier {
    public_keys: Vec<RsaPublicKey>,
}

impl SignatureVerifier {
    pub fn new() -> Self {
        Self {
            public_keys: Vec::new(),
        }
    }

    pub fn add_trusted_key(&mut self, public_key: RsaPublicKey) {
        self.public_keys.push(public_key);
    }

    /// Gibt eine Kopie der vertrauenswürdigen Schlüssel zurück
    pub fn trusted_keys(&self) -> Vec<RsaPublicKey> {
        self.public_keys.clone()
    }

    /// Gibt eine Referenz auf die vertrauenswürdigen Schlüssel zurück
    pub fn trusted_keys_ref(&self) -> &[RsaPublicKey] {
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
        
        // Berechne deterministischen Hash
        let mut hasher = Sha256::new();
        for (path, size, index) in file_entries {
            // Hash: Pfad + Separator + Größe + Separator + Content
            hasher.update(path.as_bytes());
            hasher.update(b"|");
            hasher.update(size.to_string().as_bytes());
            hasher.update(b"|");
            
            let mut file = archive.by_index(index)
                .map_err(|e| super::SecurityError::SignatureVerificationFailed(format!("Failed to reopen file: {}", e)))?;
            let mut content = Vec::new();
            file.read_to_end(&mut content)
                .map_err(|e| super::SecurityError::SignatureVerificationFailed(format!("Failed to read file content: {}", e)))?;
            
            if content.is_empty() && path == "plugin.json" {
                return Err(super::SecurityError::SignatureVerificationFailed("plugin.json is empty".to_string()));
            }
            
            hasher.update(&content);
        }
        
        let hash = hasher.finalize();
        
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
            
            // Verifiziere Signatur gegen Hash mit PKCS1v15
            use rsa::Pkcs1v15Sign;
            
            let padding = Pkcs1v15Sign::new::<Sha256>();
            match public_key.verify(padding, &hash, &signature_bytes) {
                Ok(_) => {
                    log::info!("Signature verification successful");
                    return Ok(true);
                }
                Err(e) => {
                    log::debug!("Signature verification failed with key: {}", e);
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

//ich diene der aktualisierung wala
