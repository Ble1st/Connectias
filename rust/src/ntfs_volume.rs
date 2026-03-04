//! NTFS volume operations: list directory, read file.

use crate::block_device::ScsiBlockDevice;
use crate::ntfs_reader::BlockDeviceReader;
use crate::partition;
use ntfs::{Ntfs, NtfsReadSeek};
use std::io::{self, Read, Seek, SeekFrom};

pub struct NtfsVolume {
    reader: BlockDeviceReader,
    ntfs: Ntfs,
}

#[derive(Debug, Clone)]
pub struct DirEntry {
    pub name: String,
    pub is_dir: bool,
    pub size: u64,
}

impl NtfsVolume {
    pub fn open(block_device: ScsiBlockDevice) -> io::Result<Self> {
        let partition_start = partition::get_partition_offset(&block_device)?;
        let block_size = block_device.block_size as u64;
        let block_count = block_device.block_count;
        let partition_size_bytes = if partition_start == 0 {
            block_count * block_size
        } else {
            (block_count - partition_start) * block_size
        };

        let mut reader = BlockDeviceReader::new(
            block_device,
            partition_start,
            partition_size_bytes,
        );

        let mut ntfs = Ntfs::new(&mut reader).map_err(|e| {
            let msg = format!("{:?}", e);
            if msg.contains("InvalidSectorsPerCluster") || msg.contains("sectors_per_cluster: 0") {
                io::Error::new(
                    io::ErrorKind::InvalidData,
                    "No NTFS volume. Only NTFS partitions are supported. This device may have FAT32/exFAT or no partition table.",
                )
            } else {
                io::Error::new(io::ErrorKind::InvalidData, format!("NTFS parse error: {}", msg))
            }
        })?;
        ntfs.read_upcase_table(&mut reader).map_err(|e| {
            io::Error::new(
                io::ErrorKind::InvalidData,
                format!("NTFS upcase error: {:?}", e),
            )
        })?;

        Ok(Self { reader, ntfs })
    }

    fn find_entry_record_number_in_dir(
        ntfs: &ntfs::Ntfs,
        reader: &mut BlockDeviceReader,
        dir_record_number: u64,
        name: &str,
    ) -> io::Result<Option<u64>> {
        let dir = ntfs.file(reader, dir_record_number)?;
        let index = dir.directory_index(reader)?;
        let mut finder = index.finder();
        let entry = ntfs::indexes::NtfsFileNameIndex::find(&mut finder, ntfs, reader, name);
        match entry {
            Some(Ok(entry)) => Ok(Some(entry.file_reference().file_record_number())),
            Some(Err(e)) => Err(io::Error::new(io::ErrorKind::Other, format!("{:?}", e))),
            None => Ok(None),
        }
    }

    fn list_dir_by_record_number(&mut self, dir_record_number: u64) -> io::Result<Vec<DirEntry>> {
        let mut entries = Vec::new();
        let dir = self.ntfs.file(&mut self.reader, dir_record_number)?;
        let index = dir.directory_index(&mut self.reader)?;
        let mut iter = index.entries();
        while let Some(entry) = iter.next(&mut self.reader) {
            let entry = entry.map_err(|e| io::Error::new(io::ErrorKind::Other, format!("{:?}", e)))?;
            if let Some(Ok(key)) = entry.key() {
                let name = key.name().to_string_lossy();
                if name == "." || name == ".." {
                    continue;
                }
                let is_dir = key.is_directory();
                let size = key.data_size();
                entries.push(DirEntry {
                    name,
                    is_dir,
                    size,
                });
            }
        }
        Ok(entries)
    }

    pub fn list_directory(&mut self, path: &str) -> io::Result<Vec<DirEntry>> {
        let path = path.trim_matches('/');
        if path.is_empty() {
            let root = self.ntfs.root_directory(&mut self.reader)?;
            return self.list_dir_by_record_number(root.file_record_number());
        }
        let parts: Vec<&str> = path.split('/').filter(|s| !s.is_empty()).collect();
        let mut current_record = self.ntfs.root_directory(&mut self.reader)?.file_record_number();
        for part in parts {
            current_record = match Self::find_entry_record_number_in_dir(
                &self.ntfs,
                &mut self.reader,
                current_record,
                part,
            )? {
                Some(r) => r,
                None => return Err(io::Error::new(io::ErrorKind::NotFound, format!("Not found: {}", part))),
            };
        }
        self.list_dir_by_record_number(current_record)
    }

    pub fn read_file(&mut self, path: &str, offset: u64, length: usize) -> io::Result<Vec<u8>> {
        let path = path.trim_matches('/');
        let parts: Vec<&str> = path.split('/').filter(|s| !s.is_empty()).collect();
        if parts.is_empty() {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "Invalid path"));
        }

        let mut current_record = self.ntfs.root_directory(&mut self.reader)?.file_record_number();
        for (i, part) in parts.iter().enumerate() {
            let record_num = match Self::find_entry_record_number_in_dir(
                &self.ntfs,
                &mut self.reader,
                current_record,
                part,
            )? {
                Some(r) => r,
                None => return Err(io::Error::new(io::ErrorKind::NotFound, format!("Not found: {}", part))),
            };
            if i == parts.len() - 1 {
                let file = self.ntfs.file(&mut self.reader, record_num)?;
                if file.is_directory() {
                    return Err(io::Error::new(io::ErrorKind::InvalidInput, "Is a directory"));
                }
                let data_item = file.data(&mut self.reader, "").ok_or_else(|| {
                    io::Error::new(io::ErrorKind::NotFound, "No data stream")
                })??;
                let attr = data_item.to_attribute()?;
                let mut value = attr.value(&mut self.reader)?;
                value.seek(&mut self.reader, SeekFrom::Start(offset))?;
                let mut data = vec![0u8; length];
                let n = value.read(&mut self.reader, &mut data)?;
                data.truncate(n);
                return Ok(data);
            }
            current_record = record_num;
        }
        Err(io::Error::new(io::ErrorKind::NotFound, "File not found"))
    }
}
