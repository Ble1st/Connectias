// Build script for Rust port scanner
// Ensures proper linking on Android

fn main() {
    // Tell Cargo to link against log on Android
    #[cfg(target_os = "android")]
    {
        println!("cargo:rustc-link-lib=log");
    }
}

