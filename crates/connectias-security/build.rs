use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use sha2::{Sha256, Digest};

fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-env-changed=CONNECTIAS_ENABLE_COMPILE_TIME_SIGNATURE");
    
    // Nur wenn explizit aktiviert (für Production Builds)
    let enable_compile_time = env::var("CONNECTIAS_ENABLE_COMPILE_TIME_SIGNATURE").is_ok();
    
    if !enable_compile_time {
        // Erstelle leere Placeholder-Dateien für Development
        let out_dir = env::var("OUT_DIR").unwrap();
        let signature_path = Path::new(&out_dir).join("embedded_signature.txt");
        let checksum_path = Path::new(&out_dir).join("embedded_checksum.bin");
        
        // SECURITY FIX: Emitiere Warnings statt Fehler zu verschlucken
        if let Err(e) = fs::write(&signature_path, "") {
            println!("cargo:warning=Failed to write embedded_signature.txt: {} (path: {:?})", e, signature_path);
        }
        if let Err(e) = fs::write(&checksum_path, b"") {
            println!("cargo:warning=Failed to write embedded_checksum.bin: {} (path: {:?})", e, checksum_path);
        }
        return;
    }
    
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    
    // Versuche Binary zu finden (wird nach dem Linken verfügbar sein)
    // Für Android: suche nach .so Dateien
    // Für andere: suche nach executables
    
    let binary_path = find_binary_artifact();
    
    let signature_hash = if let Some(path) = binary_path {
        if let Ok(hash) = compute_file_hash(&path) {
            hash
        } else {
            // Fallback: Leerer Hash (wird zur Runtime berechnet)
            String::new()
        }
    } else {
        // Keine Binary gefunden - leerer Hash für Runtime-Fallback
        String::new()
    };
    
    // Schreibe Signatur in generated file
    let signature_file = out_dir.join("embedded_signature.txt");
    fs::write(&signature_file, signature_hash.clone())
        .expect("Failed to write embedded signature");
    
    // Für Checksum: Berechne nur /proc/self/exe (wird zur Runtime genutzt)
    // Schreibe leere Checksum (wird zur Runtime berechnet für kritische Pfade)
    let checksum_file = out_dir.join("embedded_checksum.bin");
    fs::write(&checksum_file, b"")
        .expect("Failed to write embedded checksum placeholder");
    
    println!("cargo:warning=Compile-time signature embedding enabled");
}

fn find_binary_artifact() -> Option<PathBuf> {
    // Für Android: suche nach .so in target/
    // Für Desktop: suche nach executable
    
    let target = env::var("TARGET").ok()?;
    let profile = env::var("PROFILE").unwrap_or_else(|_| "debug".to_string());
    let target_dir = env::var("CARGO_TARGET_DIR")
        .ok()
        .map(PathBuf::from)
        .or_else(|| {
            // SECURITY FIX: Sichere Pfad-Traversierung mit parent() statt pop()
            // Validierung verhindert fehlerhafte Pfade oder Panics
            let manifest_dir = env::var("CARGO_MANIFEST_DIR").ok()?;
            let manifest_path = PathBuf::from(manifest_dir);
            
            // Gehe sicher zum Parent (connectias-security -> crates)
            let crates_dir = manifest_path.parent()?;
            // Gehe sicher zum Parent (crates -> project root)
            let project_root = crates_dir.parent()?;
            
            // Validiere dass wir wirklich im erwarteten Verzeichnis sind
            // (Optional: könnte erweiterte Validierung hinzufügen)
            Some(project_root.join("target"))
        })?;
    
    // Suche nach Binary-Artefakten
    if target.contains("android") {
        // Android: suche nach .so
        let lib_path = target_dir
            .join(&target)
            .join(&profile)
            .join("libconnectias_security.so");
        if lib_path.exists() {
            return Some(lib_path);
        }
    } else {
        // Desktop: suche nach executable (wird normalerweise nicht gefunden, da noch nicht gelinkt)
        // Fallback: versuche trotzdem
    }
    
    None
}

fn compute_file_hash(path: &Path) -> Result<String, std::io::Error> {
    let contents = fs::read(path)?;
    let mut hasher = Sha256::new();
    hasher.update(&contents);
    let hash = hasher.finalize();
    Ok(hex::encode(hash))
}

