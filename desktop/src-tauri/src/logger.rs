//! A tiny diagnostic log for user reports:
//! %LOCALAPPDATA%\io.github.nilsmorczinietz.nod\logs\nod.log, rotated at 512 KB
//! (one previous file kept as nod.log.1). Never panics, never blocks the UI long.
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

use tauri::{AppHandle, Manager};

const MAX_BYTES: u64 = 512 * 1024;

static PATH: OnceLock<PathBuf> = OnceLock::new();
static WRITE: Mutex<()> = Mutex::new(());

pub fn init(app: &AppHandle) {
    if let Ok(dir) = app.path().app_log_dir() {
        let _ = fs::create_dir_all(&dir);
        let _ = PATH.set(dir.join("nod.log"));
        write("info", &format!("started v{}", app.package_info().version));
    }
}

/// Appends one line; `source` is e.g. "rust", "main" or "overlay".
pub fn write(source: &str, message: &str) {
    let Some(path) = PATH.get() else { return };
    let _guard = WRITE.lock();
    if fs::metadata(path).is_ok_and(|m| m.len() > MAX_BYTES) {
        let _ = fs::rename(path, path.with_extension("log.1"));
    }
    let seconds = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or_default();
    let line: String = message
        .chars()
        .take(4000)
        .collect::<String>()
        .replace('\n', " ⏎ ");
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(path) {
        let _ = writeln!(file, "{seconds} [{source}] {line}");
    }
}
