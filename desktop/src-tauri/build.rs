// Declaring the app commands generates `allow-<command>` permissions, so each
// window only gets the commands listed in its capability file.
const COMMANDS: &[&str] = &[
    "snapshot",
    "pair",
    "unpair",
    "resolve",
    "list_items",
    "set_paused",
    "set_mode",
    "set_shortcuts",
    "check_update",
    "install_update",
    "open_release_notes",
    "set_prefs",
    "overlay_layout",
    "open_main",
];

fn main() {
    tauri_build::try_build(
        tauri_build::Attributes::new()
            .app_manifest(tauri_build::AppManifest::new().commands(COMMANDS)),
    )
    .expect("failed to run tauri-build");
}
