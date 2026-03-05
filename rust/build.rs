//! Build script: compiles libdvdcss and libdvdread for Android when targeting aarch64-linux-android.
//! Uses cc crate to compile C sources directly. For stream-only use, ioctl_stub.c replaces ioctl.c.

use std::env;
use std::path::PathBuf;

fn find_ndk_clang(target: &str) -> Option<(PathBuf, PathBuf)> {
    let ndk_root = env::var("ANDROID_NDK_HOME")
        .ok()
        .or_else(|| env::var("ANDROID_NDK_ROOT").ok())
        .or_else(|| {
            let home = env::var("HOME").ok()?;
            let sdk = PathBuf::from(&home).join("Android/Sdk/ndk");
            if sdk.exists() {
                std::fs::read_dir(&sdk).ok()?.next()?.ok().map(|e| e.path().to_string_lossy().into_owned())
            } else {
                None
            }
        })?;
    let ndk = PathBuf::from(&ndk_root);
    let host = if cfg!(target_os = "windows") {
        "windows-x86_64"
    } else if cfg!(target_os = "macos") {
        if cfg!(target_arch = "aarch64") {
            "darwin-arm64"
        } else {
            "darwin-x86_64"
        }
    } else {
        "linux-x86_64"
    };
    let prebuilt = ndk.join("toolchains/llvm/prebuilt").join(host);
    let prebuilt_alt = ndk.join("toolchains/llvm/prebuilt/linux-x86_64");
    let bin = if prebuilt.join("bin").exists() {
        prebuilt.join("bin")
    } else if prebuilt_alt.join("bin").exists() {
        prebuilt_alt.join("bin")
    } else {
        return None;
    };
    let (_arch, clang_name) = match target {
        t if t.contains("aarch64") => ("aarch64", "aarch64-linux-android21-clang"),
        t if t.contains("armv7") => ("arm", "armv7a-linux-androideabi21-clang"),
        t if t.contains("i686") => ("i686", "i686-linux-android21-clang"),
        t if t.contains("x86_64") => ("x86_64", "x86_64-linux-android21-clang"),
        _ => return None,
    };
    let clang = bin.join(clang_name);
    let ar = bin.join("llvm-ar");
    if clang.exists() {
        Some((clang, ar))
    } else {
        None
    }
}

fn main() {
    println!("cargo:rustc-check-cfg=cfg(has_dvd)");
    let target = env::var("TARGET").unwrap_or_default();
    if !target.contains("android") {
        // Not building for Android - skip DVD libs
        return;
    }

    if let Some((clang, ar)) = find_ndk_clang(&target) {
        let cc_key = format!("CC_{}", target.replace("-", "_"));
        let ar_key = format!("AR_{}", target.replace("-", "_"));
        env::set_var(&cc_key, &clang);
        env::set_var(&ar_key, ar);
    } else {
        eprintln!("cargo:warning=Android NDK not found. DVD libs will not be built. Set ANDROID_NDK_HOME.");
        return;
    }

    let manifest_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    let project_root = manifest_dir.parent().unwrap();
    let third_party = project_root.join("third_party");

    let dvdcss_src = third_party.join("libdvdcss").join("src");
    let dvdread_src = third_party.join("libdvdread").join("src");
    let dvdread_dvdread = third_party.join("libdvdread").join("src").join("dvdread");

    // Create build output dir for config.h
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let dvdcss_config_dir = out_dir.join("dvdcss_config");
    let dvdread_config_dir = out_dir.join("dvdread_config");
    std::fs::create_dir_all(&dvdcss_config_dir).unwrap();
    std::fs::create_dir_all(&dvdread_config_dir).unwrap();

    // Copy config.h for each lib (config.h is included by sources)
    std::fs::copy(
        third_party.join("libdvdcss_config.h"),
        dvdcss_config_dir.join("config.h"),
    )
    .unwrap();
    std::fs::copy(
        third_party.join("libdvdread_config.h"),
        dvdread_config_dir.join("config.h"),
    )
    .unwrap();

    // libdvdcss: use ioctl_stub.c instead of ioctl.c for Android (no linux/cdrom.h)
    let dvdcss_files = [
        "cpxm.c",
        "css.c",
        "device.c",
        "error.c",
        "ioctl_stub.c",
        "libdvdcpxm.c",
        "libdvdcss.c",
    ];
    let dvdcss_sources: Vec<PathBuf> = dvdcss_files
        .iter()
        .map(|f| dvdcss_src.join(f))
        .collect();

    cc::Build::new()
        .files(&dvdcss_sources)
        .include(&dvdcss_config_dir)
        .include(&dvdcss_src)
        .include(dvdcss_src.join("dvdcss"))
        .flag("-D_DEFAULT_SOURCE")
        .compile("dvdcss");

    // libdvdread
    let dvdread_files = [
        "bitreader.c",
        "dvd_input.c",
        "dvd_reader.c",
        "dvd_udf.c",
        "ifo_print.c",
        "ifo_read.c",
        "logger.c",
        "md5.c",
        "nav_print.c",
        "nav_read.c",
    ];
    let dvdread_sources: Vec<PathBuf> = dvdread_files
        .iter()
        .map(|f| dvdread_src.join(f))
        .collect();

    let rust_c = manifest_dir.join("c");
    let dvd_helper = rust_c.join("dvd_helper.c");

    cc::Build::new()
        .files(&dvdread_sources)
        .file(&dvd_helper)
        .include(&dvdread_config_dir)
        .include(&dvdread_src)
        .include(&dvdread_dvdread)
        .include(&dvdcss_src)  // for <dvdcss/dvdcss.h>
        .include(dvdcss_src.join("dvdcss"))
        .define("HAVE_DVDCSS_DVDCSS_H", None)
        .define("DVDREAD_API_EXPORT", None)
        .flag("-D_DEFAULT_SOURCE")
        .compile("dvdread");

    // Link order: dvdread depends on dvdcss, so list dvdread first
    println!("cargo:rustc-link-lib=static=dvdread");
    println!("cargo:rustc-link-lib=static=dvdcss");
    println!("cargo:rustc-cfg=has_dvd");
}
