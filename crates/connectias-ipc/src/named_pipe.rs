#[cfg(windows)]
use async_trait::async_trait;
#[cfg(windows)]
use std::sync::Arc;
#[cfg(windows)]
use std::time::Duration;
#[cfg(windows)]
use tokio::sync::Mutex;
#[cfg(windows)]
use tokio::time::timeout;
#[cfg(windows)]
use windows::Win32::Foundation::HANDLE;
#[cfg(windows)]
use windows::Win32::System::Pipes::*;
#[cfg(windows)]
use crate::{IPCTransport, IPCMessage, IPCError, MAX_MESSAGE_SIZE};

#[cfg(windows)]
pub struct NamedPipeTransport {
    pipe_handle: Arc<Mutex<Option<HANDLE>>>,
}

#[cfg(windows)]
impl NamedPipeTransport {
    pub fn new() -> Self {
        Self {
            pipe_handle: Arc::new(Mutex::new(None)),
        }
    }
}

#[cfg(windows)]
#[async_trait]
impl IPCTransport for NamedPipeTransport {
    async fn send(&self, _target: &str, msg: IPCMessage) -> Result<(), IPCError> {
        let data = bincode::serialize(&msg)?;
        let len = (data.len() as u32).to_le_bytes();
        
        let handle = self.pipe_handle.lock().await
            .ok_or(IPCError::ConnectionFailed("No pipe".into()))?;
        
        unsafe {
            // Write length with complete-write check
            let mut total_written = 0u32;
            while total_written < 4 {
                let mut written = 0u32;
                let result = WriteFile(
                    handle, 
                    &len[total_written as usize..], 
                    Some(&mut written), 
                    None
                );
                if let Err(e) = result {
                    return Err(IPCError::WriteFailed(format!("Failed to write length: {}", e)));
                }
                total_written += written;
            }
            
            // Write data with complete-write check
            let mut total_written = 0u32;
            while total_written < data.len() as u32 {
                let mut written = 0u32;
                let result = WriteFile(
                    handle, 
                    &data[total_written as usize..], 
                    Some(&mut written), 
                    None
                );
                if let Err(e) = result {
                    return Err(IPCError::WriteFailed(format!("Failed to write data: {}", e)));
                }
                total_written += written;
            }
        }
        Ok(())
    }
    
    async fn receive(&self) -> Result<IPCMessage, IPCError> {
        let guard = self.pipe_handle.lock().await;
        let handle = guard.as_ref()
            .ok_or(IPCError::ConnectionFailed("No pipe".into()))?;
        
        // Read length with complete-read check
        let mut len_buf = [0u8; 4];
        let mut total_read = 0u32;
        unsafe {
            while total_read < 4 {
                let mut read = 0u32;
                let result = ReadFile(
                    handle, 
                    &mut len_buf[total_read as usize..], 
                    Some(&mut read), 
                    None
                );
                if let Err(e) = result {
                    return Err(IPCError::ReadFailed(format!("Failed to read length: {}", e)));
                }
                total_read += read;
            }
        }
        let len = u32::from_le_bytes(len_buf) as usize;
        
        if len > MAX_MESSAGE_SIZE {
            return Err(IPCError::MessageTooLarge { size: len, max: MAX_MESSAGE_SIZE });
        }
        
        // Read data with complete-read check
        let mut data = vec![0u8; len];
        let mut total_read = 0u32;
        unsafe {
            while total_read < len as u32 {
                let mut read = 0u32;
                let result = ReadFile(
                    handle, 
                    &mut data[total_read as usize..], 
                    Some(&mut read), 
                    None
                );
                if let Err(e) = result {
                    return Err(IPCError::ReadFailed(format!("Failed to read data: {}", e)));
                }
                total_read += read;
            }
        }
        
        let msg = bincode::deserialize(&data)?;
        Ok(msg)
    }
    
    async fn try_receive(&self, timeout_duration: Duration) -> Result<Option<IPCMessage>, IPCError> {
        match timeout(timeout_duration, self.receive()).await {
            Ok(Ok(msg)) => Ok(Some(msg)),
            Ok(Err(e)) => Err(e),
            Err(_) => Ok(None), // Timeout
        }
    }
    
    async fn connect(&self, path: &str) -> Result<(), IPCError> {
        let pipe_name = format!("\\\\.\\pipe\\{}", path);
        let pipe_name_wide: Vec<u16> = pipe_name.encode_utf16().chain(std::iter::once(0)).collect();
        
        let handle = unsafe {
            CreateFileW(
                &pipe_name_wide,
                GENERIC_READ | GENERIC_WRITE,
                0,
                None,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                None,
            )?
        };
        
        let mut pipe_handle = self.pipe_handle.lock().await;
        *pipe_handle = Some(handle);
        Ok(())
    }
    
    async fn listen(&self, path: &str) -> Result<(), IPCError> {
        let pipe_name = format!("\\\\.\\pipe\\{}", path);
        let pipe_name_wide: Vec<u16> = pipe_name.encode_utf16().chain(std::iter::once(0)).collect();
        
        let handle = unsafe {
            CreateNamedPipeW(
                &pipe_name_wide,
                PIPE_ACCESS_DUPLEX,
                PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT,
                PIPE_UNLIMITED_INSTANCES,
                4096,
                4096,
                0,
                None,
            )?
        };
        
        // HANDLE ist Copy, daher können wir es klonen für den move
        let handle_for_blocking = handle;
        
        // Warte auf eingehende Verbindung - blockierend, daher in spawn_blocking
        let connection_result = tokio::task::spawn_blocking(move || {
            unsafe {
                ConnectNamedPipe(handle_for_blocking, None)
            }
        }).await.map_err(|e| IPCError::ConnectionFailed(format!("Failed to spawn blocking task: {}", e)))?;
        
        connection_result?;
        
        let mut pipe_handle = self.pipe_handle.lock().await;
        *pipe_handle = Some(handle);
        Ok(())
    }
    
    async fn disconnect(&self) -> Result<(), IPCError> {
        let mut pipe_handle = self.pipe_handle.lock().await;
        if let Some(handle) = pipe_handle.take() {
            unsafe {
                CloseHandle(handle)?;
            }
        }
        Ok(())
    }
}
