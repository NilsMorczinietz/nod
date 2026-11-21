//! Persistent, non-secret configuration plus the device token in the OS
//! credential store (Windows Credential Manager).
use std::fs;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::error::{Error, Result};

/// Where the overlay lives on screen.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum OverlayPosition {
    /// Top centre of the primary monitor ("the Island").
    #[default]
    Island,
    /// Bottom-right corner, above the taskbar.
    Corner,
}

/// Size of the island relative to the default (on top of display scale and text size).
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum IslandSize {
    Small,
    #[default]
    Default,
    Large,
}

/// Colour scheme of the main window (light for a fresh config).
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AppTheme {
    System,
    #[default]
    Light,
    Dark,
}

/// Colour scheme of the island; dark by default, like a hardware notch.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum IslandTheme {
    #[default]
    Dark,
    Light,
    /// Follow the app theme.
    App,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Prefs {
    /// Play a short chime for new cards.
    pub sound: bool,
    /// Start Nod when Windows starts.
    pub autostart: bool,
    pub position: OverlayPosition,
    pub island_size: IslandSize,
    pub app_theme: AppTheme,
    pub island_theme: IslandTheme,
}

impl Default for Prefs {
    fn default() -> Self {
        Self {
            sound: true,
            autostart: true,
            position: OverlayPosition::default(),
            island_size: IslandSize::default(),
            app_theme: AppTheme::default(),
            island_theme: IslandTheme::default(),
        }
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Config {
    pub relay_url: Option<String>,
    pub device_id: Option<String>,
    pub device_name: Option<String>,
    pub prefs: Prefs,
}

pub struct ConfigFile {
    path: PathBuf,
}

impl ConfigFile {
    pub fn new(dir: &Path) -> Self {
        Self {
            path: dir.join("config.json"),
        }
    }

    /// A missing or corrupt file yields the defaults rather than an error.
    pub fn load(&self) -> Config {
        fs::read(&self.path)
            .ok()
            .and_then(|bytes| serde_json::from_slice(&bytes).ok())
            .unwrap_or_default()
    }

    /// Atomic write: temp file + rename.
    pub fn save(&self, config: &Config) -> Result<()> {
        if let Some(dir) = self.path.parent() {
            fs::create_dir_all(dir)?;
        }
        let temp = self.path.with_extension("json.tmp");
        fs::write(&temp, serde_json::to_vec_pretty(config)?)?;
        fs::rename(temp, &self.path)?;
        Ok(())
    }
}

const SERVICE: &str = "io.github.nilsmorczinietz.nod";

#[cfg(windows)]
pub mod secrets {
    use std::sync::{Arc, OnceLock};

    use keyring_core::{CredentialStore, Entry};

    use super::{Error, Result, SERVICE};

    fn store() -> Result<&'static Arc<CredentialStore>> {
        static STORE: OnceLock<Arc<CredentialStore>> = OnceLock::new();
        if let Some(store) = STORE.get() {
            return Ok(store);
        }
        let store: Arc<CredentialStore> =
            windows_native_keyring_store::Store::new().map_err(secret_error)?;
        Ok(STORE.get_or_init(|| store))
    }

    fn entry(device_id: &str) -> Result<Entry> {
        Entry::new_in_store(store()?, SERVICE, device_id).map_err(secret_error)
    }

    fn secret_error(error: keyring_core::Error) -> Error {
        Error::Secret(error.to_string())
    }

    pub fn save(device_id: &str, token: &str) -> Result<()> {
        entry(device_id)?.set_password(token).map_err(secret_error)
    }

    pub fn load(device_id: &str) -> Result<Option<String>> {
        match entry(device_id)?.get_password() {
            Ok(token) => Ok(Some(token)),
            Err(keyring_core::Error::NoEntry) => Ok(None),
            Err(error) => Err(secret_error(error)),
        }
    }

    pub fn delete(device_id: &str) -> Result<()> {
        match entry(device_id)?.delete_credential() {
            Ok(()) | Err(keyring_core::Error::NoEntry) => Ok(()),
            Err(error) => Err(secret_error(error)),
        }
    }
}

/// Development fallback for non-Windows hosts: an owner-only file next to the config.
#[cfg(not(windows))]
pub mod secrets {
    use std::fs;
    use std::path::PathBuf;

    use super::{Error, Result, SERVICE};

    fn path(device_id: &str) -> Result<PathBuf> {
        let base = std::env::var_os("XDG_CONFIG_HOME")
            .map(PathBuf::from)
            .or_else(|| std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".config")))
            .ok_or_else(|| Error::Secret("no config directory".into()))?;
        Ok(base.join(SERVICE).join(format!("token-{device_id}")))
    }

    pub fn save(device_id: &str, token: &str) -> Result<()> {
        use std::os::unix::fs::PermissionsExt;
        let path = path(device_id)?;
        if let Some(dir) = path.parent() {
            fs::create_dir_all(dir)?;
        }
        fs::write(&path, token)?;
        fs::set_permissions(&path, fs::Permissions::from_mode(0o600))?;
        Ok(())
    }

    pub fn load(device_id: &str) -> Result<Option<String>> {
        Ok(fs::read_to_string(path(device_id)?).ok())
    }

    pub fn delete(device_id: &str) -> Result<()> {
        let _ = fs::remove_file(path(device_id)?);
        Ok(())
    }
}
