//! Main-window helpers.
use tauri::{AppHandle, Emitter, Manager};

use crate::state::EVENT_FOCUS_ITEM;

pub const MAIN: &str = "main";

pub fn show_main(app: &AppHandle) {
    if let Some(window) = app.get_webview_window(MAIN) {
        let _ = window.unminimize();
        let _ = window.show();
        let _ = window.set_focus();
    }
}

/// Opens the main window on a specific item (e.g. to read a long plan).
pub fn show_item(app: &AppHandle, id: &str) {
    show_main(app);
    let _ = app.emit_to(MAIN, EVENT_FOCUS_ITEM, id);
}
