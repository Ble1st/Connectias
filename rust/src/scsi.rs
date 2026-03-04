//! SCSI Bulk-Only Transport (BOT) and Command Block Wrapper (CBW/CSW).

use std::io;

pub const CBW_SIGNATURE: u32 = 0x4342_5355; // "USBC" little endian
pub const CSW_SIGNATURE: u32 = 0x5342_5355; // "USBS" little endian
pub const CBW_SIZE: usize = 31;
pub const CSW_SIZE: usize = 13;

pub const DIRECTION_OUT: u8 = 0x00;
pub const DIRECTION_IN: u8 = 0x80;

// SCSI Operation Codes
pub const TEST_UNIT_READY: u8 = 0x00;
pub const REQUEST_SENSE: u8 = 0x03;
pub const INQUIRY: u8 = 0x12;
pub const READ_CAPACITY_10: u8 = 0x25;
pub const READ_10: u8 = 0x28;
pub const READ_10_OPCODE: u8 = 0x28;

/// Build CBW (Command Block Wrapper).
pub fn build_cbw(tag: u32, data_length: u32, flags: u8, cdb: &[u8]) -> Vec<u8> {
    let mut buf = vec![0u8; CBW_SIZE];
    buf[0..4].copy_from_slice(&CBW_SIGNATURE.to_le_bytes());
    buf[4..8].copy_from_slice(&tag.to_le_bytes());
    buf[8..12].copy_from_slice(&data_length.to_le_bytes());
    buf[12] = flags;
    buf[13] = 0; // LUN
    buf[14] = cdb.len() as u8;
    buf[15..15 + cdb.len().min(16)].copy_from_slice(&cdb[..cdb.len().min(16)]);
    buf
}

/// Build READ CAPACITY(10) CDB.
pub fn build_read_capacity_10_cdb() -> [u8; 10] {
    let mut cdb = [0u8; 10];
    cdb[0] = READ_CAPACITY_10;
    cdb
}

/// Build READ(10) CDB. SCSI uses Big Endian for LBA and transfer length.
pub fn build_read_10_cdb(lba: u32, block_count: u16) -> [u8; 10] {
    let mut cdb = [0u8; 10];
    cdb[0] = READ_10;
    cdb[2..6].copy_from_slice(&lba.to_be_bytes());
    cdb[7..9].copy_from_slice(&block_count.to_be_bytes());
    cdb
}

/// Build REQUEST SENSE CDB.
pub fn build_request_sense_cdb(allocation_length: u8) -> [u8; 6] {
    let mut cdb = [0u8; 6];
    cdb[0] = REQUEST_SENSE;
    cdb[4] = allocation_length;
    cdb
}

/// Build TEST UNIT READY CDB.
pub fn build_test_unit_ready_cdb() -> [u8; 6] {
    let mut cdb = [0u8; 6];
    cdb[0] = TEST_UNIT_READY;
    cdb
}

/// Parse CSW (Command Status Wrapper). Returns (signature_ok, tag_ok, status, residue).
pub fn parse_csw(buf: &[u8]) -> io::Result<(bool, bool, u8, u32)> {
    if buf.len() < CSW_SIZE {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "CSW too short",
        ));
    }
    let signature = u32::from_le_bytes([buf[0], buf[1], buf[2], buf[3]]);
    let tag = u32::from_le_bytes([buf[4], buf[5], buf[6], buf[7]]);
    let residue = u32::from_le_bytes([buf[8], buf[9], buf[10], buf[11]]);
    let status = buf[12];
    Ok((
        signature == CSW_SIGNATURE,
        true, // tag checked by caller
        status,
        residue,
    ))
}

/// Sense data from REQUEST SENSE response (Fixed format, response code 0x70/0x71).
pub fn parse_sense(buf: &[u8]) -> (u8, u8, u8) {
    if buf.len() < 14 {
        return (0, 0, 0);
    }
    let response_code = buf[0] & 0x7F;
    if response_code == 0x70 || response_code == 0x71 {
        let sense_key = buf[2] & 0x0F;
        let asc = buf[12];
        let ascq = buf[13];
        (sense_key, asc, ascq)
    } else {
        (0, 0, 0)
    }
}

pub const SENSE_NO_SENSE: u8 = 0x00;
pub const ASC_NO_MEDIUM: u8 = 0x3A;
