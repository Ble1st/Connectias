use async_trait::async_trait;
use nix::sys::socket::*;
use std::os::unix::io::RawFd;
use std::os::fd::{AsRawFd, IntoRawFd};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::Mutex;
use tokio::time::timeout;
use crate::{IPCTransport, IPCMessage, IPCError, MAX_MESSAGE_SIZE};

pub struct UnixSocketTransport {
    socket_fd: Arc<Mutex<Option<RawFd>>>,
}

impl UnixSocketTransport {
    pub fn new() -> Self {
        Self {
            socket_fd: Arc::new(Mutex::new(None)),
        }
    }
}

#[async_trait]
impl IPCTransport for UnixSocketTransport {
    async fn send(&self, _target: &str, msg: IPCMessage) -> Result<(), IPCError> {
        let data = bincode::serialize(&msg)?;
        let len = (data.len() as u32).to_le_bytes();
        
        let fd = self.socket_fd.lock().await
            .ok_or(IPCError::ConnectionFailed("No socket".into()))?;
        
        // Send length with complete-write check
        let mut total_written = 0;
        unsafe {
            while total_written < 4 {
                let result = libc::write(
                    fd, 
                    len.as_ptr().add(total_written) as *const libc::c_void, 
                    4 - total_written
                );
                if result <= 0 {
                    return Err(IPCError::WriteFailed(format!("Failed to write length: {}", result)));
                }
                total_written += result as usize;
            }
        }
        
        // Send data with complete-write check
        let mut total_written = 0;
        unsafe {
            while total_written < data.len() {
                let result = libc::write(
                    fd, 
                    data.as_ptr().add(total_written) as *const libc::c_void, 
                    data.len() - total_written
                );
                if result <= 0 {
                    return Err(IPCError::WriteFailed(format!("Failed to write data: {}", result)));
                }
                total_written += result as usize;
            }
        }
        
        Ok(())
    }
    
    async fn receive(&self) -> Result<IPCMessage, IPCError> {
        let fd = self.socket_fd.lock().await
            .ok_or(IPCError::ConnectionFailed("No socket".into()))?;
        
        // Read length with complete-read check
        let mut len_buf = [0u8; 4];
        let mut total_read = 0;
        unsafe {
            while total_read < 4 {
                let result = libc::read(
                    fd, 
                    len_buf.as_mut_ptr().add(total_read) as *mut libc::c_void, 
                    4 - total_read
                );
                if result <= 0 {
                    return Err(IPCError::ReadFailed(format!("Failed to read length: {}", result)));
                }
                total_read += result as usize;
            }
        }
        let len = u32::from_le_bytes(len_buf) as usize;
        
        if len > MAX_MESSAGE_SIZE {
            return Err(IPCError::MessageTooLarge { size: len, max: MAX_MESSAGE_SIZE });
        }
        
        // Read data with complete-read check
        let mut data = vec![0u8; len];
        let mut total_read = 0;
        unsafe {
            while total_read < len {
                let result = libc::read(
                    fd, 
                    data.as_mut_ptr().add(total_read) as *mut libc::c_void, 
                    len - total_read
                );
                if result <= 0 {
                    return Err(IPCError::ReadFailed(format!("Failed to read data: {}", result)));
                }
                total_read += result as usize;
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
        let socket_fd = socket(AddressFamily::Unix, SockType::Stream, SockFlag::empty(), None)?;
        let addr = UnixAddr::new(path)?;
        connect(socket_fd.as_raw_fd(), &addr)?;
        
        let mut fd = self.socket_fd.lock().await;
        *fd = Some(socket_fd.into_raw_fd());
        Ok(())
    }
    
    async fn listen(&self, path: &str) -> Result<(), IPCError> {
        // Entferne existierende Socket-Datei falls vorhanden
        if std::path::Path::new(path).exists() {
            if let Err(e) = std::fs::remove_file(path) {
                // Log warning aber nicht als Fehler behandeln
                eprintln!("Warning: Could not remove existing socket file {}: {}", path, e);
            }
        }
        
        let socket_fd = socket(AddressFamily::Unix, SockType::Stream, SockFlag::empty(), None)?;
        let addr = UnixAddr::new(path)?;
        bind(socket_fd.as_raw_fd(), &addr)?;
        listen(&socket_fd, Backlog::new(128).unwrap())?;
        
        let mut fd = self.socket_fd.lock().await;
        *fd = Some(socket_fd.into_raw_fd());
        Ok(())
    }
    
    async fn disconnect(&self) -> Result<(), IPCError> {
        let mut fd = self.socket_fd.lock().await;
        if let Some(socket_fd) = fd.take() {
            unsafe {
                libc::close(socket_fd);
            }
        }
        Ok(())
    }
}
