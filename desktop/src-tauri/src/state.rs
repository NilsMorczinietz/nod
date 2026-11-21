//! Shared application state and the events pushed to the webviews.
use std::sync::Mutex;

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};
use tokio::sync::watch;

use crate::relay::client::{Credentials, RelayClient};
use crate::relay::types::{HubSettings, Item};
use crate::store::{Config, ConfigFile, Prefs};
use crate::updater::UpdateInfo;

pub const EVENT_SNAPSHOT: &str = "nod://snapshot";
pub const EVENT_STATUS: &str = "nod://status";
pub const EVENT_MESSAGE: &str = "nod://message";
pub const EVENT_FOCUS_ITEM: &str = "nod://focus-item";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum ConnectionStatus {
    Unpaired,
    Connecting,
    Online,
    Offline,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Hub {
    pub status: ConnectionStatus,
    pub pending: Vec<Item>,
    pub settings: HubSettings,
}

/// Everything a window needs to render from scratch.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    pub hub: Hub,
    pub paired: bool,
    pub relay_url: Option<String>,
    pub device_name: Option<String>,
    pub prefs: Prefs,
    /// Windows "Text size" accessibility setting (1.0–2.25).
    pub text_scale: f64,
    pub version: String,
    /// Set when a newer release is available.
    pub update: Option<UpdateInfo>,
}

pub struct AppState {
    pub client: RelayClient,
    pub config_file: ConfigFile,
    pub config: Mutex<Config>,
    pub hub: Mutex<Hub>,
    /// The stream task follows this channel: `None` = unpaired.
    pub credentials: watch::Sender<Option<Credentials>>,
    pub version: String,
    pub update: Mutex<Option<UpdateInfo>>,
}

impl AppState {
    pub fn credentials(&self) -> Option<Credentials> {
        self.credentials.borrow().clone()
    }

    pub fn snapshot(&self) -> Snapshot {
        let config = self.config.lock().expect("config lock").clone();
        Snapshot {
            hub: self.hub.lock().expect("hub lock").clone(),
            paired: self.credentials.borrow().is_some(),
            relay_url: config.relay_url,
            device_name: config.device_name,
            prefs: config.prefs,
            text_scale: crate::display::text_scale(),
            version: self.version.clone(),
            update: self.update.lock().expect("update lock").clone(),
        }
    }
}

pub fn emit_snapshot(app: &AppHandle) {
    let snapshot = app.state::<AppState>().snapshot();
    let _ = app.emit(EVENT_SNAPSHOT, snapshot);
}
