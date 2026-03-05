//! Stream registry and read-ahead buffer for LibVLC Custom I/O.

use crate::dvd::ffi;
use std::collections::HashMap;
use std::io;
use std::sync::Mutex;

const READ_AHEAD_KB: usize = 256;
const DVD_BLOCK: usize = ffi::DVD_VIDEO_LB_LEN;

/// Stream handle for a playing title. Holds dvd_file_t and read-ahead buffer.
/// SAFETY: dvd_file is only accessed from the thread that owns the stream.
pub struct DvdStream {
    pub dvd_file: *mut ffi::dvd_file_t,
    pub position: u64,
    pub buffer: Vec<u8>,
    pub buffer_offset: usize,
    pub buffer_len: usize,
}

static STREAMS: std::sync::LazyLock<Mutex<HashMap<u64, DvdStream>>> =
    std::sync::LazyLock::new(|| Mutex::new(HashMap::new()));
static STREAM_ID: std::sync::LazyLock<Mutex<u64>> =
    std::sync::LazyLock::new(|| Mutex::new(1));

pub fn alloc_stream_id() -> u64 {
    let mut guard = STREAM_ID.lock().unwrap();
    let id = *guard;
    *guard += 1;
    id
}

pub fn register_stream(stream: DvdStream) -> u64 {
    let id = alloc_stream_id();
    STREAMS.lock().unwrap().insert(id, stream);
    id
}

pub fn remove_stream(stream_id: u64) -> Option<DvdStream> {
    STREAMS.lock().unwrap().remove(&stream_id)
}

impl DvdStream {
    pub fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let mut copied = 0;
        while copied < buf.len() {
            if self.buffer_offset >= self.buffer_len {
                self.position += self.buffer_len as u64;
                self.fill_buffer()?;
                if self.buffer_len == 0 {
                    break;
                }
            }
            let to_copy = (self.buffer_len - self.buffer_offset).min(buf.len() - copied);
            buf[copied..copied + to_copy]
                .copy_from_slice(&self.buffer[self.buffer_offset..self.buffer_offset + to_copy]);
            self.buffer_offset += to_copy;
            copied += to_copy;
        }
        Ok(copied)
    }

    fn fill_buffer(&mut self) -> io::Result<()> {
        if self.buffer.is_empty() {
            self.buffer.resize(READ_AHEAD_KB * 1024, 0);
        }
        let block_offset = (self.position / DVD_BLOCK as u64) as i32;
        let blocks_to_read = (self.buffer.len() / DVD_BLOCK).min(256);
        let n = unsafe {
            ffi::DVDReadBlocks(
                self.dvd_file,
                block_offset,
                blocks_to_read,
                self.buffer.as_mut_ptr(),
            )
        };
        if n < 0 {
            return Err(io::Error::new(io::ErrorKind::Other, "DVDReadBlocks failed"));
        }
        self.buffer_len = (n as usize) * DVD_BLOCK;
        self.buffer_offset = 0;
        Ok(())
    }

    pub fn seek(&mut self, offset: u64) -> io::Result<()> {
        let result = unsafe { ffi::DVDFileSeek(self.dvd_file, offset as i32) };
        if result < 0 {
            return Err(io::Error::new(io::ErrorKind::Other, "DVDFileSeek failed"));
        }
        self.position = offset;
        self.buffer_offset = 0;
        self.buffer_len = 0;
        Ok(())
    }
}

pub fn read_stream(stream_id: u64, buf: &mut [u8]) -> io::Result<usize> {
    let mut streams = STREAMS.lock().unwrap();
    if let Some(stream) = streams.get_mut(&stream_id) {
        stream.read(buf)
    } else {
        Err(io::Error::new(io::ErrorKind::NotFound, "Stream not found"))
    }
}

pub fn seek_stream(stream_id: u64, offset: u64) -> io::Result<()> {
    let mut streams = STREAMS.lock().unwrap();
    if let Some(stream) = streams.get_mut(&stream_id) {
        stream.seek(offset)
    } else {
        Err(io::Error::new(io::ErrorKind::NotFound, "Stream not found"))
    }
}

pub fn close_stream(stream_id: u64) -> Option<*mut ffi::dvd_file_t> {
    let mut streams = STREAMS.lock().unwrap();
    streams.remove(&stream_id).map(|s| s.dvd_file)
}

unsafe impl Send for DvdStream {}
