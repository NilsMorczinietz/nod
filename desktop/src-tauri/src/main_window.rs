//! Main-window helpers, including reliably bringing the window to the front.
//!
//! Windows refuses `SetForegroundWindow` from a process that didn't receive
//! the last input (the "foreground lock"), so a window opened from the tray or
//! via a second launch could end up behind the active app. Two measures:
//! the launching process hands its foreground right to the running instance
//! (`allow_foreground_handoff`), and `show_main` uses a fallback sequence.
use tauri::{AppHandle, Emitter, Manager};

use crate::state::EVENT_FOCUS_ITEM;

pub const MAIN: &str = "main";

pub fn show_main(app: &AppHandle) {
    if let Some(window) = app.get_webview_window(MAIN) {
        // If the app finished loading a while ago but never rendered (it sets
        // __nodReady then), reload it instead of showing a blank page. Pages
        // that are still loading are left alone.
        let _ = window.eval(
            "if (!window.__nodReady && document.readyState === 'complete' \
             && Date.now() - (window.__nodLoadedAt ?? 0) > 5000) { \
             window.__TAURI_INTERNALS__?.invoke('log_error', \
             { message: 'main not ready on show, reloading' }); location.reload() }",
        );
        let _ = window.unminimize();
        let _ = window.show();
        platform::bring_to_front(&window);
        let _ = window.set_focus();
    }
}

/// Opens the main window on a specific item (e.g. to read a long plan).
pub fn show_item(app: &AppHandle, id: &str) {
    show_main(app);
    let _ = app.emit_to(MAIN, EVENT_FOCUS_ITEM, id);
}

/// Called first thing in every launch. A process the user just started may
/// set the foreground; this lets the already running instance (which gets our
/// arguments through the single-instance plugin) use that right.
pub fn allow_foreground_handoff() {
    platform::allow_foreground_handoff();
}

#[cfg(windows)]
mod platform {
    use tauri::WebviewWindow;
    use windows::Win32::UI::WindowsAndMessaging::{
        ASFW_ANY, AllowSetForegroundWindow, FLASHW_TIMERNOFG, FLASHW_TRAY, FLASHWINFO,
        FlashWindowEx, GetForegroundWindow, HWND_NOTOPMOST, HWND_TOPMOST, SWP_NOMOVE, SWP_NOSIZE,
        SWP_SHOWWINDOW, SetForegroundWindow, SetWindowPos,
    };

    pub fn allow_foreground_handoff() {
        // SAFETY: no pointers involved; fails harmlessly without the right.
        unsafe {
            let _ = AllowSetForegroundWindow(ASFW_ANY);
        }
    }

    pub fn bring_to_front(window: &WebviewWindow) {
        let Ok(hwnd) = window.hwnd() else { return };
        // SAFETY: plain Win32 calls on our own window handle.
        unsafe {
            if SetForegroundWindow(hwnd).as_bool() {
                return;
            }
            // Raise it above everything without stealing the topmost state.
            let flags = SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW;
            let _ = SetWindowPos(hwnd, Some(HWND_TOPMOST), 0, 0, 0, 0, flags);
            let _ = SetWindowPos(hwnd, Some(HWND_NOTOPMOST), 0, 0, 0, 0, flags);
            if SetForegroundWindow(hwnd).as_bool() || GetForegroundWindow() == hwnd {
                return;
            }
            // Last resort: at least flash the taskbar button until it's used.
            let info = FLASHWINFO {
                cbSize: size_of::<FLASHWINFO>() as u32,
                hwnd,
                dwFlags: FLASHW_TRAY | FLASHW_TIMERNOFG,
                uCount: 0,
                dwTimeout: 0,
            };
            let _ = FlashWindowEx(&info);
        }
    }
}

#[cfg(not(windows))]
mod platform {
    use tauri::WebviewWindow;

    pub fn allow_foreground_handoff() {}

    pub fn bring_to_front(_window: &WebviewWindow) {}
}
