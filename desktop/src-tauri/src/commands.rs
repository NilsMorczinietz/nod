//! Tauri commands invoked from the webviews. Each window may only call the
//! commands granted in its capability file (see `capabilities/`).
use tauri::{AppHandle, Emitter, State};
use tauri_plugin_autostart::ManagerExt;

use crate::error::Result;
use crate::relay::client::ResolveOutcome;
use crate::relay::types::{HubSettings, Item, ResolveInput, RoutingMode, StreamMessage};
use crate::session::require_credentials;
use crate::state::{AppState, EVENT_MESSAGE, Snapshot, emit_snapshot};
use crate::store::Prefs;
use crate::updater::{self, UpdateInfo};
use crate::{main_window, overlay, session, shortcuts, tray};

#[tauri::command]
pub fn snapshot(state: State<'_, AppState>) -> Snapshot {
    state.snapshot()
}

#[tauri::command]
pub async fn pair(app: AppHandle, link: String, code: Option<String>) -> Result<Snapshot> {
    session::pair(&app, &link, code.as_deref()).await?;
    Ok(tauri::Manager::state::<AppState>(&app).snapshot())
}

#[tauri::command]
pub async fn unpair(app: AppHandle) -> Result<Snapshot> {
    session::forget(&app, true).await?;
    Ok(tauri::Manager::state::<AppState>(&app).snapshot())
}

#[tauri::command]
pub async fn resolve(
    state: State<'_, AppState>,
    id: String,
    input: ResolveInput,
) -> Result<ResolveOutcome> {
    let creds = require_credentials(&state)?;
    state.client.resolve(&creds, &id, &input).await
}

#[tauri::command]
pub async fn list_items(state: State<'_, AppState>, limit: Option<u32>) -> Result<Vec<Item>> {
    let creds = require_credentials(&state)?;
    state.client.list_items(&creds, limit.unwrap_or(50)).await
}

#[tauri::command]
pub async fn set_paused(
    app: AppHandle,
    state: State<'_, AppState>,
    paused: bool,
) -> Result<HubSettings> {
    let creds = require_credentials(&state)?;
    let settings = state.client.set_paused(&creds, paused).await?;
    apply_settings(&app, &state, settings);
    Ok(settings)
}

#[tauri::command]
pub async fn set_mode(
    app: AppHandle,
    state: State<'_, AppState>,
    mode: RoutingMode,
) -> Result<HubSettings> {
    let creds = require_credentials(&state)?;
    let settings = state.client.set_mode(&creds, mode).await?;
    apply_settings(&app, &state, settings);
    Ok(settings)
}

/// The relay also broadcasts new settings; applying them locally keeps the UI snappy.
fn apply_settings(app: &AppHandle, state: &AppState, settings: HubSettings) {
    state.hub.lock().expect("hub lock").settings = settings;
    tray::refresh(app);
    let _ = app.emit(EVENT_MESSAGE, StreamMessage::Settings { settings });
}

#[tauri::command]
pub async fn check_update(app: AppHandle) -> Result<Option<UpdateInfo>> {
    updater::check(&app).await
}

/// Only ever runs because the user chose "Install and restart".
#[tauri::command]
pub async fn install_update(app: AppHandle) -> Result<()> {
    updater::install(&app).await
}

#[tauri::command]
pub fn open_release_notes() -> Result<()> {
    updater::open_release_notes()
}

/// The overlay turns the global shortcuts on while it shows a request.
#[tauri::command]
pub fn set_shortcuts(app: AppHandle, active: bool) -> Result<()> {
    shortcuts::set_active(&app, active)
}

#[tauri::command]
pub fn set_prefs(app: AppHandle, state: State<'_, AppState>, prefs: Prefs) -> Result<Prefs> {
    {
        let mut config = state.config.lock().expect("config lock");
        config.prefs = prefs;
        state.config_file.save(&config)?;
    }
    sync_autostart(&app, prefs.autostart);
    emit_snapshot(&app);
    Ok(prefs)
}

#[tauri::command]
pub fn overlay_layout(app: AppHandle, width: f64, height: f64) -> Result<()> {
    overlay::layout(&app, width, height)
}

#[tauri::command]
pub fn open_main(app: AppHandle, item_id: Option<String>) {
    match item_id {
        Some(id) => main_window::show_item(&app, &id),
        None => main_window::show_main(&app),
    }
}

/// Registers or removes the Windows "Run" entry to match the preference.
pub fn sync_autostart(app: &AppHandle, enabled: bool) {
    let launcher = app.autolaunch();
    if launcher.is_enabled().unwrap_or(false) != enabled {
        let _ = if enabled {
            launcher.enable()
        } else {
            launcher.disable()
        };
    }
}
