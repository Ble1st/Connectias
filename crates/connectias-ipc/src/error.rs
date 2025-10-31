use thiserror::Error;

#[derive(Debug, Error)]
pub enum IPCError {
    #[error("Connection failed: {0}")]
    ConnectionFailed(String),
    
    #[error("Socket error: {0}")]
    SocketError(#[from] nix::errno::Errno),
    
    #[error("Serialization error: {0}")]
    SerializationError(#[from] bincode::Error),
    
    #[error("IO error: {0}")]
    IoError(#[from] std::io::Error),
    
    #[error("Message too large: {size} > {max}")]
    MessageTooLarge { size: usize, max: usize },
    
    #[error("Read failed: {0}")]
    ReadFailed(String),
    
    #[error("Write failed: {0}")]
    WriteFailed(String),
    
    #[error("Timeout: operation timed out after {0:?}")]
    Timeout(std::time::Duration),
    
    #[error("Validation error: {0}")]
    ValidationError(String),
}
