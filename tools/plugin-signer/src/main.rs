use clap::{Parser, Subcommand};
use ring::signature::{RsaKeyPair, RSA_PKCS1_SHA256, KeyPair};
use ring::rand::SystemRandom;
use std::fs::File;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use zip::{ZipArchive, ZipWriter, FileOptions};
use anyhow::{Context, Result};

#[derive(Parser)]
#[command(name = "plugin-signer")]
#[command(about = "Tool zum Signieren von Connectias-Plugins", long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Signiert ein Plugin mit einem privaten RSA-Schlüssel
    Sign {
        /// Pfad zum Plugin-ZIP (unsigniert)
        #[arg(short, long)]
        plugin: PathBuf,
        
        /// Pfad zum privaten RSA-Schlüssel (PKCS#8 DER Format)
        #[arg(short = 'k', long)]
        private_key: PathBuf,
        
        /// Ausgabe-Pfad für signiertes Plugin
        #[arg(short, long)]
        output: PathBuf,
    },
    
    /// Extrahiert den öffentlichen Schlüssel aus einem privaten Schlüssel
    ExtractKey {
        /// Pfad zum privaten RSA-Schlüssel
        #[arg(short = 'k', long)]
        private_key: PathBuf,
        
        /// Ausgabe-Pfad für öffentlichen Schlüssel (DER Format)
        #[arg(short, long)]
        output: PathBuf,
    },
    
    /// Erstellt ein neues RSA-Schlüsselpaar für Plugin-Signierung
    GenerateKey {
        /// Ausgabe-Pfad für privaten Schlüssel (PKCS#8 DER)
        #[arg(short = 'p', long)]
        private_key: PathBuf,
        
        /// Ausgabe-Pfad für öffentlichen Schlüssel (DER)
        #[arg(short = 'u', long)]
        public_key: PathBuf,
        
        /// RSA-Schlüssellänge (2048 oder 4096)
        #[arg(short = 's', long, default_value = "2048")]
        key_size: usize,
    },
}

/// Liste der Signatur-Dateinamen, die beim Hashing ausgeschlossen werden
const SIGNATURE_FILES: &[&str] = &[
    "META-INF/SIGNATURE.RSA",
    "META-INF/SIGNATURE.SF",
    "META-INF/SIGNATURE.DSA",
    "signature.pem",
    "signature.sig",
];

fn main() -> Result<()> {
    let cli = Cli::parse();
    
    match &cli.command {
        Commands::Sign { plugin, private_key, output } => {
            sign_plugin(plugin, private_key, output)
        }
        Commands::ExtractKey { private_key, output } => {
            extract_public_key(private_key, output)
        }
        Commands::GenerateKey { private_key, public_key, key_size } => {
            generate_key_pair(private_key, public_key, *key_size)
        }
    }
}

fn sign_plugin(plugin_path: &Path, private_key_path: &Path, output_path: &Path) -> Result<()> {
    println!("Signiere Plugin: {:?}", plugin_path);
    
    // 1. Private Key laden
    let private_key_data = std::fs::read(private_key_path)
        .with_context(|| format!("Fehler beim Lesen des privaten Schlüssels: {:?}", private_key_path))?;
    
    let key_pair = RsaKeyPair::from_pkcs8(&private_key_data)
        .context("Ungültiges PKCS#8 Format für privaten Schlüssel")?;
    
    // 2. ZIP öffnen
    let file = File::open(plugin_path)
        .with_context(|| format!("Fehler beim Öffnen des Plugins: {:?}", plugin_path))?;
    let mut archive = ZipArchive::new(file)
        .context("Fehler beim Lesen des ZIP-Archivs")?;
    
    // 3. Dateien sammeln und sortieren
    let mut file_entries: Vec<(String, usize, usize, Vec<u8>)> = Vec::new();
    
    for i in 0..archive.len() {
        let mut file = archive.by_index(i)
            .with_context(|| format!("Fehler beim Lesen der Datei #{}", i))?;
        
        let file_name = file.name().to_string();
        
        // Skip Signatur-Dateien
        if SIGNATURE_FILES.iter().any(|sig| file_name.contains(sig)) {
            println!("  Überspringe Signatur-Datei: {}", file_name);
            continue;
        }
        
        let normalized_path = file_name.replace("\\", "/").trim_start_matches("./").to_string();
        let size = file.size() as usize;
        let mut content = Vec::new();
        file.read_to_end(&mut content)
            .with_context(|| format!("Fehler beim Lesen des Inhalts von: {}", normalized_path))?;
        
        file_entries.push((normalized_path, size, i, content));
    }
    
    // Sortiere nach Pfad für Determinismus
    file_entries.sort_by(|a, b| a.0.cmp(&b.0));
    
    println!("  Gefundene Dateien:");
    for (path, size, _, _) in &file_entries {
        println!("    {} ({} bytes)", path, size);
    }
    
    // 4. Nachricht erstellen: Pfad|Größe|Inhalt
    let mut message = Vec::new();
    for (path, size, _, content) in &file_entries {
        message.extend_from_slice(path.as_bytes());
        message.extend_from_slice(b"|");
        message.extend_from_slice(size.to_string().as_bytes());
        message.extend_from_slice(b"|");
        message.extend_from_slice(content);
    }
    
    println!("  Nachricht-Länge: {} bytes", message.len());
    
    // 5. Signatur erstellen (ring signiert die Message direkt)
    let rng = SystemRandom::new();
    let signature_len = key_pair.public().modulus_len();
    let mut signature = vec![0u8; signature_len];
    key_pair.sign(&RSA_PKCS1_SHA256, &rng, &message, &mut signature)
        .context("Fehler beim Signieren der Nachricht")?;
    
    println!("  Signatur erstellt: {} bytes", signature.len());
    
    // 6. Neues ZIP mit Signatur erstellen
    let output_file = File::create(output_path)
        .with_context(|| format!("Fehler beim Erstellen der Ausgabe: {:?}", output_path))?;
    let mut zip_writer = ZipWriter::new(output_file);
    
    // Kopiere alle Dateien
    let file = File::open(plugin_path)?;
    let mut archive = ZipArchive::new(file)?;
    for (path, _size, index, _) in &file_entries {
        let mut file = archive.by_index(*index)?;
        let file_name = file.name().to_string();
        
        // Skip alte Signatur-Dateien
        if SIGNATURE_FILES.iter().any(|sig| file_name.contains(sig)) {
            continue;
        }
        
        zip_writer.start_file(&file_name, FileOptions::default())?;
        std::io::copy(&mut file, &mut zip_writer)?;
    }
    
    // Füge Signatur hinzu
    let signature_b64 = base64::Engine::encode(&base64::engine::general_purpose::STANDARD, &signature);
    zip_writer.start_file("META-INF/SIGNATURE.RSA", FileOptions::default())?;
    zip_writer.write_all(signature_b64.as_bytes())?;
    
    zip_writer.finish()
        .context("Fehler beim Finalisieren des ZIP-Archivs")?;
    
    println!("✅ Plugin erfolgreich signiert: {:?}", output_path);
    Ok(())
}

fn extract_public_key(private_key_path: &Path, output_path: &Path) -> Result<()> {
    println!("Extrahiere öffentlichen Schlüssel aus: {:?}", private_key_path);
    
    let private_key_data = std::fs::read(private_key_path)
        .with_context(|| format!("Fehler beim Lesen des privaten Schlüssels: {:?}", private_key_path))?;
    
    let key_pair = RsaKeyPair::from_pkcs8(&private_key_data)
        .context("Ungültiges PKCS#8 Format für privaten Schlüssel")?;
    
    let public_key_der = key_pair.public().as_ref().to_vec();
    
    std::fs::write(output_path, &public_key_der)
        .with_context(|| format!("Fehler beim Schreiben des öffentlichen Schlüssels: {:?}", output_path))?;
    
    println!("✅ Öffentlicher Schlüssel extrahiert: {:?}", output_path);
    println!("  Schlüssellänge: {} bytes", public_key_der.len());
    
    Ok(())
}

fn generate_key_pair(private_key_path: &Path, public_key_path: &Path, key_size: usize) -> Result<()> {
    if key_size != 2048 && key_size != 4096 {
        anyhow::bail!("Ungültige Schlüssellänge. Nur 2048 oder 4096 werden unterstützt.");
    }
    
    println!("Erstelle RSA-Schlüsselpaar ({} bits)...", key_size);
    
    let rng = SystemRandom::new();
    
    // Generiere Schlüsselpaar
    let private_key_der = RsaKeyPair::generate_pkcs8(&rng, key_size)
        .context("Fehler beim Generieren des Schlüsselpaars")?;
    
    let key_pair = RsaKeyPair::from_pkcs8(private_key_der.as_ref())
        .context("Fehler beim Laden des generierten Schlüsselpaars")?;
    
    let public_key_der = key_pair.public().as_ref().to_vec();
    
    // Schreibe private key
    std::fs::write(private_key_path, private_key_der.as_ref())
        .with_context(|| format!("Fehler beim Schreiben des privaten Schlüssels: {:?}", private_key_path))?;
    
    // Schreibe public key
    std::fs::write(public_key_path, &public_key_der)
        .with_context(|| format!("Fehler beim Schreiben des öffentlichen Schlüssels: {:?}", public_key_path))?;
    
    println!("✅ Schlüsselpaar erfolgreich erstellt:");
    println!("  Privater Schlüssel: {:?}", private_key_path);
    println!("  Öffentlicher Schlüssel: {:?}", public_key_path);
    println!("  Schlüssellänge: {} bits", key_size);
    
    Ok(())
}

