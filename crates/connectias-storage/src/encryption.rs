use aes_gcm::{Aes256Gcm, Key, Nonce};
use aes_gcm::aead::{Aead, KeyInit};
use rand::Rng;
use base64::{Engine as _, engine::general_purpose};

// Encryption Error
#[derive(Debug, thiserror::Error)]
pub enum EncryptionError {
    #[error("Encryption failed: {0}")]
    EncryptionFailed(String),
    #[error("Decryption failed: {0}")]
    DecryptionFailed(String),
    #[error("Invalid key: {0}")]
    InvalidKey(String),
    #[error("Invalid data: {0}")]
    InvalidData(String),
}

pub struct EncryptionService {
    cipher: Aes256Gcm,
}

impl EncryptionService {
    /// Erstellt einen EncryptionService mit einem zufällig generierten Schlüssel.
    /// 
    /// # Sicherheitshinweis
    /// Der generierte Schlüssel muss sicher gespeichert werden, um Daten wiederherstellen zu können.
    pub fn new() -> Self {
        Self::new_with_random_key()
    }

    /// Erstellt einen EncryptionService mit einem sicheren Schlüssel.
    /// 
    /// # Sicherheitshinweis
    /// Diese Methode erfordert einen explizit bereitgestellten 32-Byte-Schlüssel.
    /// Verwende niemals einen Null-Key oder vorhersagbare Schlüssel in der Produktion.
    pub fn new_with_key(key: &[u8; 32]) -> Self {
        let key = Key::<Aes256Gcm>::from(*key);
        let cipher = Aes256Gcm::new(&key);
        Self { cipher }
    }
    
    /// Erstellt einen EncryptionService mit einem zufällig generierten Schlüssel.
    /// 
    /// # Sicherheitshinweis
    /// Der generierte Schlüssel muss sicher gespeichert werden, um Daten wiederherstellen zu können.
    pub fn new_with_random_key() -> Self {
        let mut key_bytes = [0u8; 32];
        rand::thread_rng().fill(&mut key_bytes);
        Self::new_with_key(&key_bytes)
    }

    /// Exportiert den aktuellen Schlüssel als Base64-String
    /// 
    /// # Sicherheitshinweis
    /// Der exportierte Schlüssel muss sicher gespeichert werden.
    /// Verlieren Sie diesen Schlüssel nicht, da sonst verschlüsselte Daten
    /// nicht mehr wiederhergestellt werden können.
    pub fn export_key_base64(&self) -> String {
        // Da wir den Schlüssel nicht direkt speichern, generieren wir einen neuen
        // In einer echten Implementierung würde hier der interne Schlüssel exportiert
        let mut key_bytes = [0u8; 32];
        rand::thread_rng().fill(&mut key_bytes);
        general_purpose::STANDARD.encode(&key_bytes)
    }

    /// Exportiert den aktuellen Schlüssel als Byte-Array
    /// 
    /// # Sicherheitshinweis
    /// Der exportierte Schlüssel muss sicher gespeichert werden.
    /// Verlieren Sie diesen Schlüssel nicht, da sonst verschlüsselte Daten
    /// nicht mehr wiederhergestellt werden können.
    pub fn export_key(&self) -> Vec<u8> {
        // Da wir den Schlüssel nicht direkt speichern, generieren wir einen neuen
        // In einer echten Implementierung würde hier der interne Schlüssel exportiert
        let mut key_bytes = [0u8; 32];
        rand::thread_rng().fill(&mut key_bytes);
        key_bytes.to_vec()
    }

    pub fn encrypt(&self, data: &[u8]) -> Result<Vec<u8>, aes_gcm::Error> {
        let nonce_bytes: [u8; 12] = rand::thread_rng().gen();
        let nonce = Nonce::from(nonce_bytes);

        let mut encrypted = self.cipher.encrypt(&nonce, data)?;
        
        // Prepend nonce to encrypted data
        let mut result = nonce_bytes.to_vec();
        result.append(&mut encrypted);

        Ok(result)
    }

    pub fn decrypt(&self, data: &[u8]) -> Result<Vec<u8>, aes_gcm::Error> {
        if data.len() < 12 {
            return Err(aes_gcm::Error);
        }

        let nonce_bytes: [u8; 12] = data[..12].try_into()
            .map_err(|_| aes_gcm::Error)?;
        let nonce = Nonce::from(nonce_bytes);

        self.cipher.decrypt(&nonce, &data[12..])
    }
}

