use serde::{Serialize, Serializer};

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("Not paired with a relay")]
    NotPaired,
    #[error("Invalid pairing link: {0}")]
    InvalidLink(String),
    #[error("{message}")]
    Relay {
        status: u16,
        code: String,
        message: String,
    },
    #[error("Network error: {0}")]
    Http(#[from] reqwest::Error),
    #[error("Update failed: {0}")]
    Update(String),
    #[error("Could not register the shortcut: {0}")]
    Shortcut(String),
    #[error("Could not access the credential store: {0}")]
    Secret(String),
    #[error(transparent)]
    Io(#[from] std::io::Error),
    #[error(transparent)]
    Json(#[from] serde_json::Error),
    #[error(transparent)]
    Tauri(#[from] tauri::Error),
}

pub type Result<T, E = Error> = std::result::Result<T, E>;

/// Commands return errors to the frontend as plain messages.
impl Serialize for Error {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(&self.to_string())
    }
}
