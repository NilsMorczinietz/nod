//! Nod desktop: a tray app that mirrors pending Claude Code requests from the
//! relay into an always-on-top overlay and lets you answer them.
mod commands;
mod display;
mod error;
mod main_window;
mod overlay;
mod presence;
mod relay;
mod session;
mod shortcuts;
mod state;
mod store;
mod tray;
mod updater;

use std::sync::Mutex;

use tauri::{AppHandle, Manager, WindowEvent};
use tokio::sync::watch;

use crate::relay::client::RelayClient;
use crate::state::{AppState, ConnectionStatus, Hub};
use crate::store::ConfigFile;

/// Launched by the Windows autostart entry: stay in the tray.
const ARG_AUTOSTART: &str = "--autostart";
/// `Nod.exe --pair "nod://pair?relay=…&code=…"` pairs without the UI.
const ARG_PAIR: &str = "--pair";

pub fn run() {
    tauri::Builder::default()
        // Must be the first plugin: a second launch forwards its args here and exits.
        .plugin(tauri_plugin_single_instance::init(|app, args, _cwd| {
            handle_args(app, &args);
        }))
        .plugin(
            tauri_plugin_autostart::Builder::new()
                .arg(ARG_AUTOSTART)
                .build(),
        )
        .plugin(shortcuts::plugin())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|app| {
            setup(app.handle())?;
            Ok(())
        })
        .on_window_event(|window, event| {
            // Closing the main window only hides it; Nod keeps running in the tray.
            if window.label() == main_window::MAIN
                && let WindowEvent::CloseRequested { api, .. } = event
            {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .invoke_handler(tauri::generate_handler![
            commands::snapshot,
            commands::pair,
            commands::unpair,
            commands::resolve,
            commands::list_items,
            commands::set_paused,
            commands::set_mode,
            commands::set_shortcuts,
            commands::check_update,
            commands::install_update,
            commands::open_release_notes,
            commands::set_prefs,
            commands::overlay_layout,
            commands::open_main,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Nod");
}

fn setup(app: &AppHandle) -> Result<(), Box<dyn std::error::Error>> {
    let config_file = ConfigFile::new(&app.path().app_config_dir()?);
    let config = config_file.load();
    let prefs = config.prefs;
    let (credentials, receiver) = watch::channel(None);

    let state = AppState {
        client: RelayClient::new()?,
        config_file,
        config: Mutex::new(config),
        hub: Mutex::new(Hub {
            status: ConnectionStatus::Unpaired,
            pending: Vec::new(),
            settings: Default::default(),
        }),
        credentials,
        version: app.package_info().version.to_string(),
        update: Mutex::new(None),
    };
    let restored = session::restore(&state);
    let paired = restored.is_some();
    state.credentials.send_replace(restored);
    app.manage(state);

    tray::build(app)?;
    commands::sync_autostart(app, prefs.autostart);
    relay::stream::spawn(app.clone(), receiver);
    updater::spawn(app.clone());

    let args: Vec<String> = std::env::args().collect();
    if !handle_args(app, &args) && !paired {
        // Autostarted but never paired: the user needs the pairing screen.
        main_window::show_main(app);
    }
    Ok(())
}

/// Handles `--pair <link>`; otherwise shows the main window unless Nod was
/// autostarted. Returns whether the main window was (or may be) shown.
fn handle_args(app: &AppHandle, args: &[String]) -> bool {
    if let Some(link) = args
        .iter()
        .position(|a| a == ARG_PAIR)
        .and_then(|i| args.get(i + 1))
    {
        let app = app.clone();
        let link = link.clone();
        tauri::async_runtime::spawn(async move {
            if session::pair(&app, &link, None).await.is_err() {
                main_window::show_main(&app);
            }
        });
        return true;
    }
    if args.iter().any(|a| a == ARG_AUTOSTART) {
        return false;
    }
    main_window::show_main(app);
    true
}
