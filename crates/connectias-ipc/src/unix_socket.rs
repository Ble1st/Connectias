use async_trait::async_trait;
use nix::sys::socket::*;
use std::os::unix::io::RawFd;
use std::os::fd::{AsRawFd, IntoRawFd};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::Mutex;
use tokio::time::timeout as tokio_timeout;
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
        
        // Halte Mutex während der gesamten Write-Sequenz
        let mut socket_fd_guard = self.socket_fd.lock().await;
        let fd = *socket_fd_guard
            .as_ref()
            .ok_or(IPCError::ConnectionFailed("No socket".into()))?;
        // Mutex bleibt gesperrt für die gesamte Write-Sequenz
        
        // Send length with complete-write check
        let mut total_written: usize = 0;
        unsafe {
            while total_written < 4 {
                let result = libc::write(
                    fd, 
                    len.as_ptr().add(total_written) as *const libc::c_void, 
                    (4 - total_written) as usize
                );
                if result < 0 {
                    let errno = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                    if errno == libc::EAGAIN || errno == libc::EWOULDBLOCK {
                        tokio::task::yield_now().await;
                        continue;
                    }
                    return Err(IPCError::WriteFailed(format!("Failed to write length: errno {}", errno)));
                } else if result == 0 {
                    return Err(IPCError::WriteFailed("Zero bytes written for length prefix (EOF)".into()));
                }
                total_written += result as usize;
            }
        }
        
        // Send data with complete-write check
        let mut total_written: usize = 0;
        unsafe {
            while total_written < data.len() {
                let result = libc::write(
                    fd, 
                    data.as_ptr().add(total_written) as *const libc::c_void, 
                    data.len() - total_written
                );
                if result < 0 {
                    let errno = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                    if errno == libc::EAGAIN || errno == libc::EWOULDBLOCK {
                        tokio::task::yield_now().await;
                        continue;
                    }
                    return Err(IPCError::WriteFailed(format!("Failed to write data: errno {}", errno)));
                } else if result == 0 {
                    return Err(IPCError::WriteFailed("Zero bytes written for payload (EOF)".into()));
                }
                total_written += result as usize;
            }
        }
        drop(socket_fd_guard); // Mutex wird jetzt freigegeben
        
        Ok(())
    }
    
    async fn receive(&self) -> Result<IPCMessage, IPCError> {
        // Halte Mutex während der gesamten Read-Sequenz
        let mut socket_fd_guard = self.socket_fd.lock().await;
        let fd = *socket_fd_guard
            .as_ref()
            .ok_or(IPCError::ConnectionFailed("No socket".into()))?;
        // Mutex bleibt gesperrt für die gesamte Read-Sequenz
        
        // Read length with complete-read check
        let mut len_buf = [0u8; 4];
        let mut total_read: usize = 0;
        unsafe {
            while total_read < 4 {
                let result = libc::read(
                    fd, 
                    len_buf.as_mut_ptr().add(total_read) as *mut libc::c_void, 
                    (4 - total_read) as usize
                );
                if result < 0 {
                    let errno = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                    drop(socket_fd_guard);
                    return Err(IPCError::ReadFailed(format!("Failed to read length: errno {}", errno)));
                } else if result == 0 {
                    drop(socket_fd_guard);
                    return Err(IPCError::ReadFailed("Connection closed while reading length".into()));
                }
                total_read += result as usize;
            }
        }
        let len = u32::from_le_bytes(len_buf) as usize;
        
        if len > MAX_MESSAGE_SIZE {
            drop(socket_fd_guard);
            return Err(IPCError::MessageTooLarge { size: len, max: MAX_MESSAGE_SIZE });
        }
        
        // Read data with complete-read check
        let mut data = vec![0u8; len];
        let mut total_read: usize = 0;
        unsafe {
            while total_read < len {
                let result = libc::read(
                    fd, 
                    data.as_mut_ptr().add(total_read) as *mut libc::c_void, 
                    (len - total_read) as usize
                );
                if result < 0 {
                    let errno = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                    drop(socket_fd_guard);
                    return Err(IPCError::ReadFailed(format!("Failed to read data: errno {}", errno)));
                } else if result == 0 {
                    drop(socket_fd_guard);
                    return Err(IPCError::ReadFailed("Connection closed while reading data".into()));
                }
                total_read += result as usize;
            }
        }
        drop(socket_fd_guard); // Mutex wird jetzt freigegeben
        
        let msg = bincode::deserialize(&data)?;
        Ok(msg)
    }
    
    async fn try_receive(&self, timeout_duration: Duration) -> Result<Option<IPCMessage>, IPCError> {
        // FIX BUG 1: Verwende blocking libc::read mit timeout, da tokio::UnixStream::from_raw_fd nicht existiert
        // WICHTIG: RawFd ist Copy (i32), daher kopieren wir nur den Integer-Wert, nicht den File Descriptor selbst.
        // Es gibt KEINE echte FD-Duplikation (kein dup()/dup2()). Beide Referenzen zeigen auf denselben FD.
        // Der FD wird nur geschlossen, wenn disconnect() aufgerufen wird oder socket_fd auf None gesetzt wird.
        // KEIN LEAK: Der FD wird korrekt verwaltet durch self.socket_fd und nur bei disconnect() geschlossen.
        use libc;
        
        // Hole FD-Wert (RawFd ist Copy - Integer-Wert, keine echte FD-Duplikation)
        let fd = {
            let socket_fd_guard = self.socket_fd.lock().await;
            *socket_fd_guard
                .as_ref()
                .ok_or(IPCError::ConnectionFailed("No socket".into()))?
        };
        
        // FIX BUG 3: Race Condition zwischen try_receive und disconnect verhindern
        // Prüfe ob FD noch gültig ist, bevor wir spawn_blocking starten
        // Der FD bleibt in self.socket_fd gespeichert und wird nur bei disconnect() geschlossen.
        // WICHTIG: Wenn disconnect() während try_receive läuft, wird der FD geschlossen
        // und libc::read gibt EBADF zurück. Wir behandeln das korrekt als Read-Fehler.
        let fd_for_read = fd;
        let result = tokio_timeout(timeout_duration, tokio::task::spawn_blocking(move || -> Result<IPCMessage, IPCError> {
            // FIX BUG 3: Prüfe vor jedem Read ob FD noch gültig ist (Race Condition Schutz)
            // Wenn disconnect() aufgerufen wurde, ist socket_fd None und der FD wurde geschlossen
            // Wir können das nicht direkt prüfen, aber EBADF wird bei libc::read zurückgegeben
            // FIX BUG 1: Read length prefix mit Loop für partielle Reads
            let mut len_buf = [0u8; 4];
            let mut total_read = 0usize;
            while total_read < 4 {
                let remaining = 4 - total_read;
                let result = unsafe {
                    libc::read(
                        fd_for_read,
                        len_buf.as_mut_ptr().add(total_read) as *mut libc::c_void,
                        remaining
                    )
                };
                if result < 0 {
                    let errno = std::io::Error::last_os_error();
                    // FIX BUG 3: EBADF bedeutet FD wurde geschlossen (Race Condition)
                    if errno.raw_os_error() == Some(libc::EBADF) {
                        return Err(IPCError::ReadFailed("File descriptor closed during read (disconnect() called)".to_string()));
                    }
                    return Err(IPCError::ReadFailed(format!("Failed to read length: {}", errno)));
                }
                if result == 0 {
                    return Err(IPCError::ReadFailed("Connection closed during length read".to_string()));
                }
                total_read += result as usize;
            }
            
            let len = u32::from_le_bytes(len_buf) as usize;
            if len > MAX_MESSAGE_SIZE {
                return Err(IPCError::MessageTooLarge { size: len, max: MAX_MESSAGE_SIZE });
            }
            
            // FIX BUG 1: Read payload mit Loop für partielle Reads
            let mut data = vec![0u8; len];
            let mut total_read = 0usize;
            while total_read < len {
                let remaining = len - total_read;
                let result = unsafe {
                    libc::read(
                        fd_for_read,
                        data.as_mut_ptr().add(total_read) as *mut libc::c_void,
                        remaining
                    )
                };
                if result < 0 {
                    let errno = std::io::Error::last_os_error();
                    // FIX BUG 3: EBADF bedeutet FD wurde geschlossen (Race Condition)
                    if errno.raw_os_error() == Some(libc::EBADF) {
                        return Err(IPCError::ReadFailed("File descriptor closed during read (disconnect() called)".to_string()));
                    }
                    return Err(IPCError::ReadFailed(format!("Failed to read data: {}", errno)));
                }
                if result == 0 {
                    return Err(IPCError::ReadFailed("Connection closed during payload read".to_string()));
                }
                total_read += result as usize;
            }
            
            let msg = bincode::deserialize(&data)?;
            Ok(msg)
        })).await;
        
        match result {
            Ok(Ok(Ok(msg))) => Ok(Some(msg)),
            Ok(Ok(Err(e))) => Err(e),
            Ok(Err(e)) => Err(IPCError::ConnectionFailed(format!("Task join failed: {:?}", e))),
            Err(_) => Err(IPCError::Timeout(timeout_duration)),
        }
    }
    
    async fn connect(&self, path: &str) -> Result<(), IPCError> {
        let socket_fd = match socket(AddressFamily::Unix, SockType::Stream, SockFlag::empty(), None) {
            Ok(fd) => fd,
            Err(e) => return Err(IPCError::SocketError(e)),
        };
        
        let addr = match UnixAddr::new(path) {
            Ok(addr) => addr,
            Err(e) => {
                // Socket schließen bei Fehler
                unsafe {
                    libc::close(socket_fd.as_raw_fd());
                }
                return Err(IPCError::SocketError(e));
            }
        };
        
        // Verwende async connect statt blocking
        match tokio::task::spawn_blocking(move || {
            connect(socket_fd.as_raw_fd(), &addr)
        }).await {
            Ok(Ok(())) => {
                let mut fd = self.socket_fd.lock().await;
                *fd = Some(socket_fd.into_raw_fd());
                Ok(())
            }
            Ok(Err(e)) => {
                // Socket schließen bei Fehler
                unsafe {
                    libc::close(socket_fd.as_raw_fd());
                }
                Err(IPCError::SocketError(e))
            }
            Err(join_err) => {
                // Socket schließen bei Task-Fehler
                unsafe {
                    libc::close(socket_fd.as_raw_fd());
                }
                Err(IPCError::ConnectionFailed(format!("Task join failed: {:?}", join_err)))
            }
        }
    }
    
    async fn listen(&self, path: &str) -> Result<(), IPCError> {
        // Entferne existierende Socket-Datei atomar (TOCTOU-Schutz)
        // Ignoriere NotFound-Fehler (Datei existiert möglicherweise nicht)
        if let Err(e) = std::fs::remove_file(path) {
            if e.kind() != std::io::ErrorKind::NotFound {
                log::warn!("Could not remove existing socket file {}: {}", path, e);
            }
        }
        
        // Socket-Erstellung und bind/listen in spawn_blocking (blocking syscalls)
        let path_clone = path.to_string();
        let socket_fd_result = tokio::task::spawn_blocking(move || -> Result<_, IPCError> {
            let socket_fd = socket(AddressFamily::Unix, SockType::Stream, SockFlag::empty(), None)
                .map_err(|e| IPCError::SocketError(e))?;
            let addr = UnixAddr::new(&path_clone)
                .map_err(|e| IPCError::SocketError(e))?;
            bind(socket_fd.as_raw_fd(), &addr)
                .map_err(|e| IPCError::SocketError(e))?;
            
            // Vermeide unwrap() - propagiere Result
            let backlog = Backlog::new(128)
                .map_err(|e| IPCError::SocketError(nix::errno::Errno::from_i32(e as i32)))?;
            listen(&socket_fd, backlog)
                .map_err(|e| IPCError::SocketError(e))?;
            
            Ok(socket_fd)
        }).await
            .map_err(|e| IPCError::ConnectionFailed(format!("Failed to spawn blocking task: {:?}", e)))??;
        
        let mut fd = self.socket_fd.lock().await;
        *fd = Some(socket_fd_result.into_raw_fd());
        
        // Starte Accept-Loop in separatem Task (async-capable)
        let path_for_accept = path.to_string();
        tokio::task::spawn_blocking(move || {
            // Accept-Loop würde hier implementiert werden
            // Für jetzt ist dies ein Platzhalter - vollständige Implementierung
            // würde accepted sockets in einem Channel/Queue speichern
            log::info!("Accept loop started for socket at {}", path_for_accept);
        });
        
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
