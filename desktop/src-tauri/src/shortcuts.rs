//! Global shortcuts to answer the request shown in the island without
//! focusing it: Ctrl+Shift+Y approves, Ctrl+Shift+N denies. They are only
//! registered while a request is on screen, so they never shadow other apps'
//! shortcuts the rest of the time.
use tauri::plugin::TauriPlugin;
use tauri::{AppHandle, Emitter, Runtime, Wry};
use tauri_plugin_global_shortcut::{Code, GlobalShortcutExt, Modifiers, Shortcut, ShortcutState};

use crate::error::{Error, Result};
use crate::overlay;

pub const EVENT_SHORTCUT: &str = "nod://shortcut";

fn approve() -> Shortcut {
    Shortcut::new(Some(Modifiers::CONTROL | Modifiers::SHIFT), Code::KeyY)
}

fn deny() -> Shortcut {
    Shortcut::new(Some(Modifiers::CONTROL | Modifiers::SHIFT), Code::KeyN)
}

/// Forwards presses to the overlay as `"approve"` / `"deny"`.
pub fn plugin() -> TauriPlugin<Wry> {
    tauri_plugin_global_shortcut::Builder::new()
        .with_handler(|app, shortcut, event| {
            if event.state() != ShortcutState::Pressed {
                return;
            }
            let action = if *shortcut == approve() {
                "approve"
            } else {
                "deny"
            };
            let _ = app.emit_to(overlay::LABEL, EVENT_SHORTCUT, action);
        })
        .build()
}

/// Registers (or removes) both shortcuts. Registering twice is a no-op.
pub fn set_active<R: Runtime>(app: &AppHandle<R>, active: bool) -> Result<()> {
    let manager = app.global_shortcut();
    for shortcut in [approve(), deny()] {
        let registered = manager.is_registered(shortcut);
        let outcome = match (active, registered) {
            (true, false) => manager.register(shortcut),
            (false, true) => manager.unregister(shortcut),
            _ => Ok(()),
        };
        outcome.map_err(|e| Error::Shortcut(e.to_string()))?;
    }
    Ok(())
}
