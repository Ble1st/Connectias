pub mod transport;
pub mod unix_socket;
pub mod named_pipe;
pub mod error;

pub use transport::{IPCTransport, IPCMessage, MAX_MESSAGE_SIZE, ValidationError};
pub use error::IPCError;

// Platform-specific transports
#[cfg(unix)]
pub use unix_socket::UnixSocketTransport;

#[cfg(windows)]
pub use named_pipe::NamedPipeTransport;
