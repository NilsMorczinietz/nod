//! Auto-updates from GitHub Releases (tauri-plugin-updater, signed artifacts).
//!
//! Release builds check on startup and every 6 hours. An available update is
//! only surfaced quietly (Settings › About and a tray item); nothing installs
//! without the user choosing "Install and restart".
use std::time::Duration;

use serde::Serialize;
use tauri::{AppHandle, Manager};
use tauri_plugin_updater::UpdaterExt;

use crate::error::{Error, Result};
use crate::state::{AppState, emit_snapshot};
use crate::tray;

const CHECK_EVERY: Duration = Duration::from_secs(6 * 60 * 60);
pub const RELEASE_NOTES_URL: &str = "https://github.com/NilsMorczinietz/nod/releases";

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateInfo {
    pub version: String,
    pub notes: Option<String>,
}

fn update_error(error: tauri_plugin_updater::Error) -> Error {
    Error::Update(error.to_string())
}

/// Background checks; skipped in dev builds, which have nothing to update.
pub fn spawn(app: AppHandle) {
    if cfg!(debug_assertions) {
        return;
    }
    tauri::async_runtime::spawn(async move {
        loop {
            let _ = check(&app).await;
            tokio::time::sleep(CHECK_EVERY).await;
        }
    });
}

/// Asks the release endpoint and remembers the result for the UI and tray.
pub async fn check(app: &AppHandle) -> Result<Option<UpdateInfo>> {
    let update = app
        .updater()
        .map_err(update_error)?
        .check()
        .await
        .map_err(update_error)?;
    let info = update.map(|u| UpdateInfo {
        version: u.version,
        notes: u.body,
    });
    *app.state::<AppState>().update.lock().expect("update lock") = info.clone();
    tray::refresh(app);
    emit_snapshot(app);
    Ok(info)
}

/// Downloads, verifies (signature) and installs the update, then restarts.
/// On Windows the installer runs in passive mode and the app exits for it.
pub async fn install(app: &AppHandle) -> Result<()> {
    let update = app
        .updater()
        .map_err(update_error)?
        .check()
        .await
        .map_err(update_error)?
        .ok_or_else(|| Error::Update("No update available".into()))?;
    update
        .download_and_install(|_, _| {}, || {})
        .await
        .map_err(update_error)?;
    app.restart();
}

/// Opens the release notes in the default browser (fixed URL, no user input).
pub fn open_release_notes() -> Result<()> {
    #[cfg(windows)]
    let mut command = std::process::Command::new("explorer");
    #[cfg(target_os = "macos")]
    let mut command = std::process::Command::new("open");
    #[cfg(all(unix, not(target_os = "macos")))]
    let mut command = std::process::Command::new("xdg-open");
    command.arg(RELEASE_NOTES_URL).spawn()?;
    Ok(())
}
