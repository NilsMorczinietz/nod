//! System tray icon: pending count in the tooltip, quick pause, open, quit,
//! and – only while one is available – "Install update … and restart".
use std::sync::atomic::{AtomicBool, Ordering};

use tauri::menu::{Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager, Wry};

use crate::error::Result;
use crate::state::{AppState, ConnectionStatus};
use crate::{commands, main_window, overlay, updater};

const TRAY_ID: &str = "nod";

/// Menu items whose label or presence changes at runtime.
pub struct TrayItems {
    menu: Menu<Wry>,
    pause: MenuItem<Wry>,
    update: MenuItem<Wry>,
    update_shown: AtomicBool,
}

pub fn build(app: &AppHandle) -> Result<()> {
    let open = MenuItem::with_id(app, "open", "Open Nod", true, None::<&str>)?;
    let overview = MenuItem::with_id(app, "overview", "Show recent activity", true, None::<&str>)?;
    let pause = MenuItem::with_id(app, "pause", "Pause", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Quit Nod", true, None::<&str>)?;
    let update = MenuItem::with_id(app, "update", "Install update", true, None::<&str>)?;
    let menu = Menu::with_items(
        app,
        &[
            &open,
            &overview,
            &pause,
            &PredefinedMenuItem::separator(app)?,
            &quit,
        ],
    )?;

    let mut builder = TrayIconBuilder::with_id(TRAY_ID)
        .tooltip("Nod")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id().as_ref() {
            "open" => main_window::show_main(app),
            "overview" => overlay::show_overview(app),
            "pause" => {
                let app = app.clone();
                tauri::async_runtime::spawn(async move {
                    let state = app.state::<AppState>();
                    let paused = state.hub.lock().expect("hub lock").settings.paused;
                    let _ = commands::set_paused(app.clone(), state, !paused).await;
                });
            }
            "quit" => app.exit(0),
            "update" => {
                let app = app.clone();
                tauri::async_runtime::spawn(async move {
                    let _ = updater::install(&app).await;
                });
            }
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                main_window::show_main(tray.app_handle());
            }
        });
    if let Some(icon) = app.default_window_icon() {
        builder = builder.icon(icon.clone());
    }
    builder.build(app)?;
    app.manage(TrayItems {
        menu: menu.clone(),
        pause,
        update,
        update_shown: AtomicBool::new(false),
    });
    refresh(app);
    Ok(())
}

/// Updates tooltip and the pause item from the current hub state.
pub fn refresh(app: &AppHandle) {
    let (status, pending, paused) = {
        let hub = app
            .state::<AppState>()
            .hub
            .lock()
            .expect("hub lock")
            .clone();
        (hub.status, hub.pending.len(), hub.settings.paused)
    };
    let tooltip = match status {
        ConnectionStatus::Unpaired => "Nod · not paired".to_owned(),
        ConnectionStatus::Connecting | ConnectionStatus::Offline => {
            "Nod · reconnecting…".to_owned()
        }
        ConnectionStatus::Online if paused => "Nod · paused".to_owned(),
        ConnectionStatus::Online if pending == 0 => "Nod · all clear".to_owned(),
        ConnectionStatus::Online => format!("Nod · {pending} waiting"),
    };
    if let Some(tray) = app.tray_by_id(TRAY_ID) {
        let _ = tray.set_tooltip(Some(tooltip));
    }
    if let Some(items) = app.try_state::<TrayItems>() {
        let _ = items
            .pause
            .set_text(if paused { "Resume" } else { "Pause" });
        let _ = items.pause.set_enabled(status == ConnectionStatus::Online);

        let available = app
            .state::<AppState>()
            .update
            .lock()
            .expect("update lock")
            .clone();
        let shown = items.update_shown.load(Ordering::Relaxed);
        match available {
            Some(update) => {
                let _ = items
                    .update
                    .set_text(format!("Install update {} and restart", update.version));
                if !shown && items.menu.insert(&items.update, 0).is_ok() {
                    items.update_shown.store(true, Ordering::Relaxed);
                }
            }
            None if shown => {
                if items.menu.remove(&items.update).is_ok() {
                    items.update_shown.store(false, Ordering::Relaxed);
                }
            }
            None => {}
        }
    }
}
