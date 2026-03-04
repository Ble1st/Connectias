//! Read+Seek adapter for NTFS over SCSI block device.

use crate::block_device::ScsiBlockDevice;
use std::cell::Cell;
use std::io;
use std::sync::Mutex;

/// Adapter that implements Read + Seek for the ntfs crate.
/// Reads from a partition on the block device (with LBA offset).
pub struct BlockDeviceReader {
    block_device: Mutex<ScsiBlockDevice>,
    partition_start_lba: u64,
    partition_size_bytes: u64,
    position: Cell<u64>,
}

impl BlockDeviceReader {
    pub fn new(
        block_device: ScsiBlockDevice,
        partition_start_lba: u64,
        partition_size_bytes: u64,
    ) -> Self {
        Self {
            block_device: Mutex::new(block_device),
            partition_start_lba,
            partition_size_bytes,
            position: Cell::new(0),
        }
    }

    fn read_at(&self, pos: u64, buf: &mut [u8]) -> io::Result<usize> {
        let block_size = self.block_device.lock().unwrap().block_size as u64;
        if pos >= self.partition_size_bytes {
            return Ok(0);
        }
        let to_read = std::cmp::min(
            buf.len() as u64,
            self.partition_size_bytes - pos,
        ) as usize;
        if to_read == 0 {
            return Ok(0);
        }
        let start_lba = self.partition_start_lba + pos / block_size;
        let offset_in_block = (pos % block_size) as usize;
        let block_size_usize = block_size as usize;

        let dev = self.block_device.lock().unwrap();
        if offset_in_block == 0 && to_read >= block_size_usize && to_read % block_size_usize == 0 {
            let blocks = to_read / block_size_usize;
            dev.read_blocks(start_lba, blocks as u32, &mut buf[..to_read])
        } else {
            let mut block_buf = vec![0u8; block_size_usize];
            let mut total = 0usize;
            let mut cur_pos = pos;
            let mut buf_off = 0;
            while buf_off < to_read {
                let lba = self.partition_start_lba + cur_pos / block_size;
                let off_in_block = (cur_pos % block_size) as usize;
                let n = std::cmp::min(block_size_usize - off_in_block, to_read - buf_off);
                dev.read_blocks(lba, 1, &mut block_buf)?;
                buf[buf_off..buf_off + n].copy_from_slice(&block_buf[off_in_block..off_in_block + n]);
                total += n;
                buf_off += n;
                cur_pos += n as u64;
            }
            Ok(total)
        }
    }
}

impl io::Read for BlockDeviceReader {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let pos = self.position.get();
        let n = self.read_at(pos, buf)?;
        self.position.set(pos + n as u64);
        Ok(n)
    }
}

impl io::Seek for BlockDeviceReader {
    fn seek(&mut self, pos: io::SeekFrom) -> io::Result<u64> {
        let p = self.position.get();
        let new_pos = match pos {
            io::SeekFrom::Start(n) => n as i64,
            io::SeekFrom::End(n) => self.partition_size_bytes as i64 + n,
            io::SeekFrom::Current(n) => p as i64 + n,
        };
        if new_pos < 0 {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "Seek before start"));
        }
        let new_p = new_pos as u64;
        self.position.set(new_p);
        Ok(new_p)
    }
}
