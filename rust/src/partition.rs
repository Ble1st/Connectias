//! MBR and GPT partition table parsing.

use std::io;

/// Partition type for NTFS in MBR.
pub const PARTITION_TYPE_NTFS: u8 = 0x07;

/// Microsoft basic data (NTFS/FAT) partition type GUID for GPT.
/// Stored as mixed endian in disk: EBD0A0A2-B9E5-4433-87C0-68B6B72699C7.
const GPT_TYPE_MS_BASIC_DATA: [u8; 16] = [
    0xA2, 0xA0, 0xD0, 0xEB, 0xE5, 0xB9, 0x33, 0x44, 0x87, 0xC0, 0x68, 0xB6, 0xB7, 0x26, 0x99, 0xC7,
];

/// Find the first NTFS partition's start LBA from MBR.
/// Returns None if no NTFS partition found.
pub fn find_first_ntfs_partition(mbr: &[u8]) -> Option<u64> {
    if mbr.len() < 512 {
        return None;
    }
    let sig1 = mbr[510];
    let sig2 = mbr[511];
    if sig1 != 0x55 || sig2 != 0xAA {
        return None;
    }
    for i in 0..4 {
        let off = 446 + i * 16;
        if off + 16 > mbr.len() {
            break;
        }
        let part_type = mbr[off + 4];
        if part_type == PARTITION_TYPE_NTFS {
            let lba = u32::from_le_bytes([
                mbr[off + 8],
                mbr[off + 9],
                mbr[off + 10],
                mbr[off + 11],
            ]);
            return Some(lba as u64);
        }
    }
    None
}

/// Check if GPT header signature is present (at LBA 1 sector).
fn is_gpt_header(sector: &[u8]) -> bool {
    sector.len() >= 8
        && sector[0] == 0x45
        && sector[1] == 0x46
        && sector[2] == 0x49
        && sector[3] == 0x20
        && sector[4] == 0x50
        && sector[5] == 0x41
        && sector[6] == 0x52
        && sector[7] == 0x54
}

/// Find first NTFS (Microsoft basic data) partition LBA from GPT.
/// header: GPT header. entries: full partition entry array (num_entries * entry_size bytes).
fn find_first_ntfs_gpt(header: &[u8], entries: &[u8]) -> Option<u64> {
    if header.len() < 88 {
        return None;
    }
    let num_entries = u32::from_le_bytes(header[80..84].try_into().ok()?);
    let entry_size = u32::from_le_bytes(header[84..88].try_into().ok()?);
    if entry_size == 0 || entry_size > 256 {
        return None;
    }
    let entry_size = entry_size as usize;
    for i in 0..num_entries {
        let start = i as usize * entry_size;
        if start + 40 > entries.len() || start + entry_size > entries.len() {
            break;
        }
        let typ = &entries[start..start + 16];
        if typ == GPT_TYPE_MS_BASIC_DATA {
            let first_lba = u64::from_le_bytes(entries[start + 32..start + 40].try_into().ok()?);
            if first_lba > 0 {
                return Some(first_lba);
            }
        }
    }
    None
}

/// Read MBR/GPT from block device and return first NTFS partition LBA.
/// Tries MBR first, then GPT (LBA 1 + partition entries at LBA 2).
/// Returns 0 only if no NTFS partition found (try whole disk for superfloppy).
pub fn get_partition_offset(block_device: &crate::block_device::ScsiBlockDevice) -> io::Result<u64> {
    let block_size = block_device.block_size as usize;
    let mut sector0 = vec![0u8; block_size];
    block_device.read_blocks(0, 1, &mut sector0)?;

    if let Some(lba) = find_first_ntfs_partition(&sector0) {
        return Ok(lba);
    }

    let mut sector1 = vec![0u8; block_size];
    block_device.read_blocks(1, 1, &mut sector1)?;
    if !is_gpt_header(&sector1) {
        return Ok(0);
    }
    if sector1.len() < 88 {
        return Ok(0);
    }
    let entry_lba = u64::from_le_bytes(sector1[72..80].try_into().unwrap_or([0u8; 8]));
    let num_entries = u32::from_le_bytes(sector1[80..84].try_into().unwrap_or([0u8; 4]));
    let entry_size = u32::from_le_bytes(sector1[84..88].try_into().unwrap_or([0u8; 4]));
    let entry_size = entry_size as usize;
    if entry_size == 0 || entry_size > 256 {
        return Ok(0);
    }
    // Read full partition entry array (e.g. 128 entries * 128 bytes = 32 sectors of 512 bytes).
    let entries_bytes = (num_entries as usize).saturating_mul(entry_size).min(128 * 128);
    let sectors_to_read = (entries_bytes + block_size - 1) / block_size;
    let mut entries_buf = vec![0u8; sectors_to_read * block_size];
    block_device.read_blocks(entry_lba, sectors_to_read as u32, &mut entries_buf)?;
    let entries_used = entries_bytes.min(entries_buf.len());
    if let Some(lba) = find_first_ntfs_gpt(&sector1, &entries_buf[..entries_used]) {
        return Ok(lba);
    }

    Ok(0)
}
