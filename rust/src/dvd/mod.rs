//! DVD playback via libdvdread with stream callbacks from ScsiBlockDevice.

pub mod block_read;
pub mod ffi;
pub mod stream;

use block_read::{make_stream_cb, StreamContext};
use crate::block_device::ScsiBlockDevice;
use std::collections::HashMap;
use std::sync::Mutex;

static DVD_HANDLES: std::sync::LazyLock<Mutex<HashMap<u64, DvdHandle>>> =
    std::sync::LazyLock::new(|| Mutex::new(HashMap::new()));
static DVD_HANDLE_ID: std::sync::LazyLock<Mutex<u64>> =
    std::sync::LazyLock::new(|| Mutex::new(1));

struct DvdHandle {
    dvd_reader: *mut ffi::dvd_reader_t,
    stream_ctx: Box<StreamContext>,
}

unsafe impl Send for DvdHandle {}

pub fn open_dvd(
    session_id: u64,
    transfer: Box<dyn crate::block_device::TransferHandler>,
) -> Result<u64, String> {
    let block_device = ScsiBlockDevice::new(transfer, session_id)
        .map_err(|e| e.to_string())?;
    let stream_ctx = Box::new(StreamContext {
        block_device,
        position: 0,
    });
    let stream_cb = make_stream_cb();
    let dvd_reader = unsafe {
        ffi::DVDOpenStream2(
            stream_ctx.as_ref() as *const StreamContext as *mut std::ffi::c_void,
            std::ptr::null(),
            &stream_cb,
        )
    };
    if dvd_reader.is_null() {
        return Err("DVDOpenStream2 failed".to_string());
    }
    let id = {
        let mut guard = DVD_HANDLE_ID.lock().unwrap();
        let id = *guard;
        *guard += 1;
        id
    };
    DVD_HANDLES.lock().unwrap().insert(
        id,
        DvdHandle {
            dvd_reader,
            stream_ctx,
        },
    );
    Ok(id)
}

pub fn close_dvd(dvd_handle: u64) -> bool {
    let mut handles = DVD_HANDLES.lock().unwrap();
    if let Some(handle) = handles.remove(&dvd_handle) {
        unsafe {
            ffi::DVDClose(handle.dvd_reader);
        }
        true
    } else {
        false
    }
}

pub fn get_dvd_reader(dvd_handle: u64) -> Option<*mut ffi::dvd_reader_t> {
    DVD_HANDLES.lock().unwrap().get(&dvd_handle).map(|h| h.dvd_reader)
}

pub fn list_titles_json(dvd_handle: u64) -> Result<String, String> {
    let handles = DVD_HANDLES.lock().unwrap();
    let handle = handles.get(&dvd_handle).ok_or("DVD handle not found")?;
    let mut buf = vec![0u8; 8192];
    let rc = unsafe {
        ffi::dvd_list_titles_json(handle.dvd_reader, buf.as_mut_ptr(), buf.len())
    };
    if rc != 0 {
        return Err("dvd_list_titles_json failed".to_string());
    }
    let len = buf.iter().position(|&b| b == 0).unwrap_or(buf.len());
    Ok(String::from_utf8_lossy(&buf[..len]).to_string())
}

pub fn list_chapters_json(dvd_handle: u64, title_id: i32) -> Result<String, String> {
    let handles = DVD_HANDLES.lock().unwrap();
    let handle = handles.get(&dvd_handle).ok_or("DVD handle not found")?;
    let mut buf = vec![0u8; 4096];
    let rc = unsafe {
        ffi::dvd_list_chapters_json(handle.dvd_reader, title_id, buf.as_mut_ptr(), buf.len())
    };
    if rc != 0 {
        return Err("dvd_list_chapters_json failed".to_string());
    }
    let len = buf.iter().position(|&b| b == 0).unwrap_or(buf.len());
    Ok(String::from_utf8_lossy(&buf[..len]).to_string())
}

pub fn open_title_stream(dvd_handle: u64, title_id: i32) -> Result<u64, String> {
    let handles = DVD_HANDLES.lock().unwrap();
    let handle = handles.get(&dvd_handle).ok_or("DVD handle not found")?;
    let dvd_file = unsafe {
        ffi::DVDOpenFile(
            handle.dvd_reader,
            title_id,
            ffi::dvd_read_domain_t::DVD_READ_TITLE_VOBS,
        )
    };
    if dvd_file.is_null() {
        return Err("DVDOpenFile failed".to_string());
    }
    let stream = stream::DvdStream {
        dvd_file,
        position: 0,
        buffer: Vec::new(),
        buffer_offset: 0,
        buffer_len: 0,
    };
    Ok(stream::register_stream(stream))
}

pub fn read_stream(stream_id: u64, buf: &mut [u8]) -> Result<usize, String> {
    stream::read_stream(stream_id, buf).map_err(|e| e.to_string())
}

pub fn seek_stream(stream_id: u64, offset: u64) -> Result<(), String> {
    stream::seek_stream(stream_id, offset).map_err(|e| e.to_string())
}

pub fn close_stream(stream_id: u64) -> bool {
    if let Some(dvd_file) = stream::close_stream(stream_id) {
        if !dvd_file.is_null() {
            unsafe { ffi::DVDCloseFile(dvd_file) };
        }
        true
    } else {
        false
    }
}
