//! FFI bindings to libdvdread.

#![allow(non_camel_case_types)]

use std::os::raw::{c_int, c_void};

pub const DVD_VIDEO_LB_LEN: usize = 2048;

#[repr(C)]
pub struct dvd_reader_t {
    _private: [u8; 0],
}

#[repr(C)]
pub struct dvd_file_t {
    _private: [u8; 0],
}

#[repr(C)]
pub struct ifo_handle_t {
    _private: [u8; 0],
}

#[repr(C)]
pub struct dvd_reader_stream_cb {
    pub pf_seek: Option<extern "C" fn(*mut c_void, u64) -> c_int>,
    pub pf_read: Option<extern "C" fn(*mut c_void, *mut c_void, c_int) -> c_int>,
    pub pf_readv: Option<extern "C" fn(*mut c_void, *mut c_void, c_int) -> c_int>,
}

#[repr(C)]
pub enum dvd_read_domain_t {
    DVD_READ_INFO_FILE = 0,
    DVD_READ_INFO_BACKUP_FILE,
    DVD_READ_MENU_VOBS,
    DVD_READ_TITLE_VOBS,
    DVD_READ_SAMG_INFO,
    DVD_READ_ASVS_INFO,
    DVD_READ_ASVS_INFO_BACKUP,
}

#[repr(C)]
pub struct dvd_stat_t {
    pub size: i64,
    pub nr_parts: c_int,
    pub parts_size: [i64; 9],
}

#[repr(C)]
pub struct dvd_logger_cb {
    pub pf_log: Option<extern "C" fn(*mut c_void, u32, *const i8, *mut c_void)>,
}

extern "C" {
    pub fn DVDOpenStream2(
        priv_data: *mut c_void,
        logcb: *const dvd_logger_cb,
        stream_cb: *const dvd_reader_stream_cb,
    ) -> *mut dvd_reader_t;

    pub fn DVDClose(dvd: *mut dvd_reader_t);

    pub fn DVDOpenFile(
        dvd: *mut dvd_reader_t,
        titlenum: c_int,
        domain: dvd_read_domain_t,
    ) -> *mut dvd_file_t;

    pub fn DVDCloseFile(dvd_file: *mut dvd_file_t);

    pub fn DVDReadBlocks(
        dvd_file: *mut dvd_file_t,
        offset: c_int,
        block_count: usize,
        data: *mut u8,
    ) -> isize;

    pub fn DVDFileSeek(dvd_file: *mut dvd_file_t, seek_offset: i32) -> i32;

    pub fn DVDFileSize(dvd_file: *mut dvd_file_t) -> isize;

    pub fn ifoOpen(dvd: *mut dvd_reader_t, title: c_int) -> *mut ifo_handle_t;

    pub fn ifoClose(ifo: *mut ifo_handle_t);

    pub fn ifoRead_TT_SRPT(ifo: *mut ifo_handle_t) -> c_int;

    pub fn ifoRead_VTS_PTT_SRPT(ifo: *mut ifo_handle_t) -> c_int;
}

// C helper from dvd_helper.c
extern "C" {
    /// Fills buf with JSON array of titles. Returns 0 on success, -1 on error.
    pub fn dvd_list_titles_json(dvd: *mut dvd_reader_t, buf: *mut u8, buf_size: usize) -> c_int;

    /// Fills buf with JSON array of chapters for title_id (1-based). Returns 0 on success, -1 on error.
    pub fn dvd_list_chapters_json(
        dvd: *mut dvd_reader_t,
        title_id: c_int,
        buf: *mut u8,
        buf_size: usize,
    ) -> c_int;
}
