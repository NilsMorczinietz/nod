//! Pairing lifecycle: turning a pairing link into stored credentials and back.
use tauri::{AppHandle, Manager};

use crate::error::{Error, Result};
use crate::relay::client::Credentials;
use crate::relay::types::{PairingLink, validate_relay_url};
use crate::state::{AppState, ConnectionStatus, emit_snapshot};
use crate::store::secrets;
use crate::tray;

/// Restores credentials saved by a previous run, if any.
pub fn restore(state: &AppState) -> Option<Credentials> {
    let config = state.config.lock().expect("config lock").clone();
    let relay = validate_relay_url(config.relay_url.as_deref()?).ok()?;
    let token = secrets::load(config.device_id.as_deref()?).ok().flatten()?;
    Some(Credentials { relay, token })
}

pub async fn pair(app: &AppHandle, link: &str, code: Option<&str>) -> Result<()> {
    let link = PairingLink::parse(link, code)?;
    let state = app.state::<AppState>();
    let name = gethostname::gethostname().to_string_lossy().into_owned();
    let registered = state.client.register(&link, &name).await?;

    // Replace any previous pairing (best effort) before storing the new one.
    if let Some(old) = state.credentials() {
        let _ = state.client.unpair(&old).await;
    }
    let old_device = {
        let mut config = state.config.lock().expect("config lock");
        let old_device = config.device_id.replace(registered.device.id.clone());
        config.relay_url = Some(link.relay.to_string());
        config.device_name = Some(registered.device.name.clone());
        state.config_file.save(&config)?;
        old_device
    };
    if let Some(old) = old_device {
        secrets::delete(&old)?;
    }
    secrets::save(&registered.device.id, &registered.token)?;

    state.credentials.send_replace(Some(Credentials {
        relay: link.relay,
        token: registered.token,
    }));
    emit_snapshot(app);
    Ok(())
}

/// Forgets the pairing locally; `remote` also deletes the device on the relay.
pub async fn forget(app: &AppHandle, remote: bool) -> Result<()> {
    let state = app.state::<AppState>();
    if remote && let Some(creds) = state.credentials() {
        state.client.unpair(&creds).await?;
    }
    state.credentials.send_replace(None);
    {
        let mut config = state.config.lock().expect("config lock");
        if let Some(id) = config.device_id.take() {
            secrets::delete(&id)?;
        }
        config.device_name = None;
        state.config_file.save(&config)?;
    }
    {
        let mut hub = state.hub.lock().expect("hub lock");
        hub.pending.clear();
        hub.status = ConnectionStatus::Unpaired;
    }
    tray::refresh(app);
    emit_snapshot(app);
    Ok(())
}

pub fn require_credentials(state: &AppState) -> Result<Credentials> {
    state.credentials().ok_or(Error::NotPaired)
}
