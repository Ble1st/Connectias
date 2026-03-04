//! Block device abstraction over SCSI BOT.

use crate::scsi;
use std::io;

/// Transfer handler for bulk USB transfers. Implemented by JNI (Kotlin) or C callbacks.
pub trait TransferHandler: Send {
    fn bulk_out(&self, session_id: u64, data: &[u8]) -> io::Result<usize>;
    fn bulk_in(&self, session_id: u64, buf: &mut [u8]) -> io::Result<usize>;
}

pub struct ScsiBlockDevice {
    pub session_id: u64,
    pub transfer: Box<dyn TransferHandler>,
    pub block_size: u32,
    pub block_count: u64,
    pub tag: std::sync::atomic::AtomicU32,
}

impl ScsiBlockDevice {
    pub fn new(transfer: Box<dyn TransferHandler>, session_id: u64) -> io::Result<Self> {
        let mut dev = Self {
            session_id,
            transfer,
            block_size: 512,
            block_count: 0,
            tag: std::sync::atomic::AtomicU32::new(1),
        };
        dev.read_capacity()?;
        Ok(dev)
    }

    fn next_tag(&self) -> u32 {
        self.tag.fetch_add(1, std::sync::atomic::Ordering::SeqCst)
    }

    fn read_capacity(&mut self) -> io::Result<()> {
        let cdb = scsi::build_read_capacity_10_cdb();
        let mut buffer = [0u8; 8];
        self.execute_command(&cdb, Some((&mut buffer as *mut u8, 8)), scsi::DIRECTION_IN)?;
        let last_lba = u32::from_be_bytes([buffer[0], buffer[1], buffer[2], buffer[3]]) as u64;
        let block_len = u32::from_be_bytes([buffer[4], buffer[5], buffer[6], buffer[7]]);
        if block_len > 0 && block_len <= 4096 {
            self.block_size = block_len;
            self.block_count = last_lba + 1;
        }
        Ok(())
    }

    fn execute_command(
        &self,
        cdb: &[u8],
        data: Option<(*mut u8, usize)>,
        direction: u8,
    ) -> io::Result<usize> {
        let (data_ptr, data_len) = data.unwrap_or((std::ptr::null_mut(), 0));
        let tag = self.next_tag();
        let cbw = scsi::build_cbw(tag, data_len as u32, direction, cdb);

        let out_result = self.transfer.bulk_out(self.session_id, &cbw)?;
        if out_result != cbw.len() {
            return Err(io::Error::new(
                io::ErrorKind::Other,
                format!("CBW transfer failed: {} != {}", out_result, cbw.len()),
            ));
        }

        let mut transferred = 0usize;
        if data_len > 0 && !data_ptr.is_null() {
            let slice = unsafe { std::slice::from_raw_parts_mut(data_ptr, data_len) };
            transferred = self.transfer.bulk_in(self.session_id, slice)?;
        }

        let mut csw_buf = [0u8; scsi::CSW_SIZE];
        let csw_slice = &mut csw_buf[..];
        let csw_result = self.transfer.bulk_in(self.session_id, csw_slice)?;
        if csw_result != scsi::CSW_SIZE {
            return Err(io::Error::new(
                io::ErrorKind::Other,
                format!("CSW read failed: {}", csw_result),
            ));
        }

        let (sig_ok, _tag_ok, status, _residue) = scsi::parse_csw(&csw_buf)?;
        if !sig_ok {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "Invalid CSW signature",
            ));
        }

        match status {
            0 => Ok(transferred),
            1 => {
                let mut sense_buf = [0u8; 18];
                let sense_cdb = scsi::build_request_sense_cdb(18);
                let sense_cbw = scsi::build_cbw(
                    self.next_tag(),
                    18,
                    scsi::DIRECTION_IN,
                    &sense_cdb,
                );
                let _ = self.transfer.bulk_out(self.session_id, &sense_cbw);
                let sense_slice = &mut sense_buf[..];
                let _ = self.transfer.bulk_in(self.session_id, sense_slice);
                let mut csw = [0u8; scsi::CSW_SIZE];
                let csw_slice = &mut csw[..];
                let _ = self.transfer.bulk_in(self.session_id, csw_slice);
                let (sense_key, asc, ascq) = scsi::parse_sense(&sense_buf);
                Err(io::Error::new(
                    io::ErrorKind::Other,
                    format!("SCSI Check Condition: sense_key={} asc=0x{:02x} ascq=0x{:02x}", sense_key, asc, ascq),
                ))
            }
            _ => Err(io::Error::new(
                io::ErrorKind::Other,
                format!("SCSI command failed with status {}", status),
            )),
        }
    }

    pub fn read_blocks(&self, lba: u64, count: u32, buffer: &mut [u8]) -> io::Result<usize> {
        let bytes_needed = (count as usize) * (self.block_size as usize);
        if buffer.len() < bytes_needed {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "Buffer too small",
            ));
        }
        let max_blocks_per_read = 64u32;
        let mut total_read = 0usize;
        let mut remaining = count;
        let mut current_lba = lba;
        let mut offset = 0;

        while remaining > 0 {
            let to_read = std::cmp::min(remaining, max_blocks_per_read);
            let cdb = scsi::build_read_10_cdb(current_lba as u32, to_read as u16);
            let slice = &mut buffer[offset..offset + (to_read as usize) * (self.block_size as usize)];
            let n = self.execute_command(
                &cdb,
                Some((slice.as_mut_ptr(), slice.len())),
                scsi::DIRECTION_IN,
            )?;
            total_read += n;
            offset += n;
            current_lba += to_read as u64;
            remaining -= to_read;
        }
        Ok(total_read)
    }

    pub fn test_unit_ready(&self) -> io::Result<()> {
        let cdb = scsi::build_test_unit_ready_cdb();
        self.execute_command(&cdb, None, scsi::DIRECTION_IN)?;
        Ok(())
    }
}
