//! Connectias Rust library: SCSI BOT, NTFS read, JNI for Android.

mod block_device;
mod jni_bridge;
mod ntfs_reader;
mod ntfs_volume;
mod partition;
mod scsi;

#[cfg(has_dvd)]
mod dvd;

use block_device::ScsiBlockDevice;
use jni_bridge::JniTransferHandler;
use jni::objects::{JObject, JString};
use ntfs_volume::{DirEntry, NtfsVolume};
use std::collections::HashMap;
use std::ffi::CString;
use std::sync::Mutex;

static LAST_ERROR: std::sync::LazyLock<Mutex<Option<CString>>> =
    std::sync::LazyLock::new(|| Mutex::new(None));

pub(crate) fn set_last_error(msg: &str) {
    if let Ok(cstr) = CString::new(msg) {
        if let Ok(mut guard) = LAST_ERROR.lock() {
            *guard = Some(cstr);
        }
    }
}

/// Error codes for FFI/JNI.
pub const ERR_OK: i32 = 0;
pub const ERR_TRANSPORT: i32 = 1;
pub const ERR_SCSI: i32 = 2;
pub const ERR_NTFS: i32 = 3;
pub const ERR_NOT_FOUND: i32 = 4;
pub const ERR_NO_NTFS: i32 = 5;

static VOLUMES: std::sync::LazyLock<Mutex<HashMap<u64, NtfsVolume>>> =
    std::sync::LazyLock::new(|| Mutex::new(HashMap::new()));
static VOLUME_ID: std::sync::LazyLock<Mutex<u64>> =
    std::sync::LazyLock::new(|| Mutex::new(1));

// --- JNI entry points ---

#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_openVolume(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    session_id: jni::sys::jlong,
    handler: jni::sys::jobject,
) -> jni::sys::jlong {
    let mut env = unsafe {
        jni::JNIEnv::from_raw(env).expect("JNIEnv from_raw")
    };
    let handler = unsafe { JObject::from_raw(handler) };
    let transfer = match JniTransferHandler::new(&mut env, handler) {
        Ok(t) => t,
        Err(e) => {
            set_last_error(&e.to_string());
            return -(ERR_TRANSPORT as i64);
        }
    };
    let block_device = match ScsiBlockDevice::new(Box::new(transfer), session_id as u64) {
        Ok(d) => d,
        Err(e) => {
            set_last_error(&e.to_string());
            return -(ERR_TRANSPORT as i64);
        }
    };
    let volume = match NtfsVolume::open(block_device) {
        Ok(v) => v,
        Err(e) => {
            let msg = e.to_string();
            set_last_error(&msg);
            if msg.contains("No NTFS") || msg.contains("NTFS parse") {
                return -(ERR_NO_NTFS as i64);
            }
            return -(ERR_NTFS as i64);
        }
    };
    let id = {
        let mut guard = VOLUME_ID.lock().unwrap();
        let id = *guard;
        *guard += 1;
        id
    };
    VOLUMES.lock().unwrap().insert(id, volume);
    id as i64
}

#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_closeVolume(
    _env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    volume_id: jni::sys::jlong,
) -> jni::sys::jint {
    let removed = VOLUMES.lock().unwrap().remove(&(volume_id as u64));
    if removed.is_some() {
        ERR_OK
    } else {
        set_last_error("Volume not found");
        -ERR_NOT_FOUND
    }
}

#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_listDirectory(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    volume_id: jni::sys::jlong,
    path: jni::sys::jstring,
) -> jni::sys::jstring {
    let mut env = unsafe {
        jni::JNIEnv::from_raw(env).expect("JNIEnv from_raw")
    };
    let path_jstr = unsafe { JString::from_raw(path) };
    let path_str = match env.get_string(&path_jstr) {
        Ok(s) => s.to_string_lossy().into_owned(),
        Err(_) => {
            set_last_error("Invalid path");
            return std::ptr::null_mut();
        }
    };
    let mut volumes = VOLUMES.lock().unwrap();
    let volume = match volumes.get_mut(&(volume_id as u64)) {
        Some(v) => v,
        None => {
            set_last_error("Volume not found");
            return std::ptr::null_mut();
        }
    };
    let entries = match volume.list_directory(&path_str) {
        Ok(e) => e,
        Err(e) => {
            set_last_error(&e.to_string());
            return std::ptr::null_mut();
        }
    };
    let json = entries_to_json(&entries);
    match env.new_string(&json) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_readFile(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    volume_id: jni::sys::jlong,
    path: jni::sys::jstring,
    offset: jni::sys::jlong,
    length: jni::sys::jint,
) -> jni::sys::jbyteArray {
    let mut env = unsafe {
        jni::JNIEnv::from_raw(env).expect("JNIEnv from_raw")
    };
    let path_jstr = unsafe { JString::from_raw(path) };
    let path_str = match env.get_string(&path_jstr) {
        Ok(s) => s.to_string_lossy().into_owned(),
        Err(_) => {
            set_last_error("Invalid path");
            return std::ptr::null_mut();
        }
    };
    let mut volumes = VOLUMES.lock().unwrap();
    let volume = match volumes.get_mut(&(volume_id as u64)) {
        Some(v) => v,
        None => {
            set_last_error("Volume not found");
            return std::ptr::null_mut();
        }
    };
    let data = match volume.read_file(&path_str, offset as u64, length as usize) {
        Ok(d) => d,
        Err(e) => {
            set_last_error(&e.to_string());
            return std::ptr::null_mut();
        }
    };
    match env.byte_array_from_slice(&data) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_getDeviceType(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    session_id: jni::sys::jlong,
    handler: jni::sys::jobject,
) -> jni::sys::jstring {
    let mut env = unsafe { jni::JNIEnv::from_raw(env).expect("JNIEnv from_raw") };
    let handler = unsafe { JObject::from_raw(handler) };
    let transfer = match JniTransferHandler::new(&mut env, handler) {
        Ok(t) => t,
        Err(e) => {
            set_last_error(&e.to_string());
            return std::ptr::null_mut();
        }
    };
    let dev = ScsiBlockDevice::new_minimal(Box::new(transfer), session_id as u64);
    match dev.inquiry() {
        Ok(0x05) => env.new_string("optical").map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Ok(_) => env.new_string("block").map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            set_last_error(&e.to_string());
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_lastError(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
) -> jni::sys::jstring {
    let env = unsafe {
        jni::JNIEnv::from_raw(env).expect("JNIEnv from_raw")
    };
    let msg = if let Ok(guard) = LAST_ERROR.lock() {
        guard.as_ref().map(|c| c.to_string_lossy().into_owned())
    } else {
        None
    };
    let msg = msg.unwrap_or_else(|| "".to_string());
    match env.new_string(&msg) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn entries_to_json(entries: &[DirEntry]) -> String {
    let mut parts: Vec<String> = Vec::new();
    for e in entries {
        parts.push(format!(
            r#"{{"n":"{}","d":{},"s":{}}}"#,
            escape_json(&e.name),
            e.is_dir,
            e.size
        ));
    }
    format!("[{}]", parts.join(","))
}

fn escape_json(s: &str) -> String {
    s.replace('\\', "\\\\")
        .replace('"', "\\\"")
        .replace('\n', "\\n")
        .replace('\r', "\\r")
        .replace('\t', "\\t")
}

// --- DVD JNI entry points (when has_dvd) ---

#[cfg(has_dvd)]
#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_openDvd(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    session_id: jni::sys::jlong,
    handler: jni::sys::jobject,
) -> jni::sys::jlong {
    let mut env = unsafe { jni::JNIEnv::from_raw(env).expect("JNIEnv from_raw") };
    let handler = unsafe { JObject::from_raw(handler) };
    let transfer = match JniTransferHandler::new(&mut env, handler) {
        Ok(t) => t,
        Err(e) => {
            set_last_error(&e.to_string());
            return -1;
        }
    };
    match dvd::open_dvd(session_id as u64, Box::new(transfer)) {
        Ok(id) => id as i64,
        Err(e) => {
            set_last_error(&e);
            -1
        }
    }
}

#[cfg(has_dvd)]
#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_closeDvd(
    _env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    dvd_handle: jni::sys::jlong,
) {
    dvd::close_dvd(dvd_handle as u64);
}

#[cfg(has_dvd)]
#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_dvdListTitles(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    dvd_handle: jni::sys::jlong,
) -> jni::sys::jstring {
    let mut env = unsafe { jni::JNIEnv::from_raw(env).expect("JNIEnv from_raw") };
    match dvd::list_titles_json(dvd_handle as u64) {
        Ok(json) => env.new_string(&json).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            set_last_error(&e);
            std::ptr::null_mut()
        }
    }
}

#[cfg(has_dvd)]
#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_dvdListChapters(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    dvd_handle: jni::sys::jlong,
    title_id: jni::sys::jint,
) -> jni::sys::jstring {
    let mut env = unsafe { jni::JNIEnv::from_raw(env).expect("JNIEnv from_raw") };
    match dvd::list_chapters_json(dvd_handle as u64, title_id) {
        Ok(json) => env.new_string(&json).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            set_last_error(&e);
            std::ptr::null_mut()
        }
    }
}

#[cfg(has_dvd)]
#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_dvdOpenTitleStream(
    _env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    dvd_handle: jni::sys::jlong,
    title_id: jni::sys::jint,
) -> jni::sys::jlong {
    match dvd::open_title_stream(dvd_handle as u64, title_id) {
        Ok(stream_id) => stream_id as i64,
        Err(e) => {
            set_last_error(&e);
            -1
        }
    }
}

#[cfg(has_dvd)]
#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_dvdReadStream(
    env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    stream_id: jni::sys::jlong,
    buffer: jni::sys::jbyteArray,
) -> jni::sys::jint {
    let mut env = unsafe { jni::JNIEnv::from_raw(env).expect("JNIEnv from_raw") };
    let buffer_obj = unsafe { jni::objects::JByteArray::from_raw(buffer) };
    let len = env.get_array_length(&buffer_obj).unwrap_or(0) as usize;
    if len == 0 {
        return 0;
    }
    let mut buf = vec![0u8; len];
    match dvd::read_stream(stream_id as u64, &mut buf) {
        Ok(n) => {
            let i8_buf: Vec<i8> = buf[..n].iter().map(|&b| b as i8).collect();
            let _ = env.set_byte_array_region(&buffer_obj, 0, &i8_buf);
            n as i32
        }
        Err(e) => {
            set_last_error(&e);
            -1
        }
    }
}

#[cfg(has_dvd)]
#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_dvdSeekStream(
    _env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    stream_id: jni::sys::jlong,
    offset: jni::sys::jlong,
) -> jni::sys::jboolean {
    match dvd::seek_stream(stream_id as u64, offset as u64) {
        Ok(()) => jni::sys::JNI_TRUE,
        Err(e) => {
            set_last_error(&e);
            jni::sys::JNI_FALSE
        }
    }
}

#[cfg(has_dvd)]
#[no_mangle]
pub extern "system" fn Java_com_bleist_connectias_connectias_NativeBridge_dvdCloseStream(
    _env: *mut jni::sys::JNIEnv,
    _class: jni::sys::jclass,
    stream_id: jni::sys::jlong,
) {
    dvd::close_stream(stream_id as u64);
}
