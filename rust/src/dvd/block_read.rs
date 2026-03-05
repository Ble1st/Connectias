//! Stream callback adapter: ScsiBlockDevice -> libdvdread dvd_reader_stream_cb.
//! DVD uses 2048-byte blocks. ScsiBlockDevice may use 512 - we convert.

use crate::block_device::ScsiBlockDevice;
use crate::dvd::ffi;
use std::os::raw::{c_int, c_void};

const DVD_BLOCK: usize = ffi::DVD_VIDEO_LB_LEN;

/// Context passed as p_stream to libdvdread callbacks.
pub struct StreamContext {
    pub block_device: ScsiBlockDevice,
    pub position: u64,
}

unsafe impl Send for StreamContext {}

extern "C" fn stream_seek(p_stream: *mut c_void, i_pos: u64) -> c_int {
    if p_stream.is_null() {
        return -1;
    }
    let ctx = unsafe { &mut *(p_stream as *mut StreamContext) };
    ctx.position = i_pos;
    0
}

extern "C" fn stream_read(p_stream: *mut c_void, buffer: *mut c_void, i_read: c_int) -> c_int {
    if p_stream.is_null() || buffer.is_null() || i_read <= 0 {
        return -1;
    }
    let ctx = unsafe { &mut *(p_stream as *mut StreamContext) };
    let buf = unsafe { std::slice::from_raw_parts_mut(buffer as *mut u8, i_read as usize) };
    let lba = ctx.position / DVD_BLOCK as u64;
    let block_count = ((i_read as usize) + DVD_BLOCK - 1) / DVD_BLOCK;
    let block_count = block_count as u32;
    let mut read_buf = vec![0u8; (block_count as usize) * DVD_BLOCK];
    match ctx.block_device.read_blocks(lba, block_count, &mut read_buf) {
        Ok(n) => {
            let bytes_to_copy = n.min(i_read as usize);
            buf[..bytes_to_copy].copy_from_slice(&read_buf[..bytes_to_copy]);
            ctx.position += bytes_to_copy as u64;
            bytes_to_copy as c_int
        }
        Err(_) => -1,
    }
}

extern "C" fn stream_readv(p_stream: *mut c_void, _p_iovec: *mut c_void, i_blocks: c_int) -> c_int {
    if p_stream.is_null() || i_blocks <= 0 {
        return -1;
    }
    let ctx = unsafe { &mut *(p_stream as *mut StreamContext) };
    let mut buf = vec![0u8; (i_blocks as usize) * DVD_BLOCK];
    match ctx.block_device.read_blocks(ctx.position / DVD_BLOCK as u64, i_blocks as u32, &mut buf) {
        Ok(n) => {
            ctx.position += n as u64;
            (n / DVD_BLOCK) as c_int
        }
        Err(_) => -1,
    }
}

pub fn make_stream_cb() -> ffi::dvd_reader_stream_cb {
    ffi::dvd_reader_stream_cb {
        pf_seek: Some(stream_seek),
        pf_read: Some(stream_read),
        pf_readv: Some(stream_readv),
    }
}
