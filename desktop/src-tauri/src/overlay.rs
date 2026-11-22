//! The always-on-top overlay window that hosts the Island (or the corner stack).
//!
//! The frontend measures what it needs and reports a logical size; a height of
//! 0 hides the window. The window is anchored according to the user's
//! position preference. On Windows it is shown without activation, so the
//! overlay never steals focus from the editor or terminal.
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

use tauri::{AppHandle, Emitter, Manager, PhysicalPosition, PhysicalSize, WebviewWindow};

use crate::error::Result;
use crate::state::AppState;
use crate::store::OverlayPosition;

pub const LABEL: &str = "overlay";
/// Asks the overlay to open the recent-activity overview.
pub const EVENT_OVERVIEW: &str = "nod://overview";
/// A revealed window the frontend doesn't claim within this time is hidden again.
const REVEAL_TIMEOUT: Duration = Duration::from_secs(3);

/// Counts layout requests from the frontend (see `reveal`).
static LAYOUTS: AtomicU64 = AtomicU64::new(0);

fn window(app: &AppHandle) -> Option<WebviewWindow> {
    app.get_webview_window(LABEL)
}

/// Computes the overlay's physical frame. Spacing to the screen edges is
/// padding inside the (transparent) window, so shadows are never clipped.
fn frame(
    window: &WebviewWindow,
    position: OverlayPosition,
    width: f64,
    height: f64,
) -> Result<Option<(PhysicalPosition<i32>, PhysicalSize<u32>)>> {
    let Some(monitor) = window.primary_monitor()?.or(window.current_monitor()?) else {
        return Ok(None);
    };
    let scale = monitor.scale_factor();
    let area = monitor.work_area();
    let width = ((width * scale).round() as u32).min(area.size.width);
    let height = ((height * scale).round() as u32).min(area.size.height);
    let (x, y) = match position {
        OverlayPosition::Island => (
            area.position.x + (area.size.width as i32 - width as i32) / 2,
            area.position.y,
        ),
        OverlayPosition::Corner => (
            area.position.x + area.size.width as i32 - width as i32,
            area.position.y + area.size.height as i32 - height as i32,
        ),
    };
    Ok(Some((
        PhysicalPosition::new(x, y),
        PhysicalSize::new(width, height),
    )))
}

/// Shows the overlay (if hidden) so the frontend can render and measure new
/// content; WebView2 doesn't render a hidden window.
///
/// The transparent window catches clicks even where nothing is drawn, so if the
/// frontend doesn't lay it out soon (still loading, or nothing to show), it is
/// hidden again instead of sitting invisibly over the top of the screen.
pub fn reveal(app: &AppHandle) {
    let Some(window) = window(app) else { return };
    if platform::is_visible(&window) {
        return;
    }
    let scale = window.scale_factor().unwrap_or(1.0);
    let (width, height) = window
        .inner_size()
        .map(|s| (s.width as f64 / scale, s.height as f64 / scale))
        .unwrap_or((480.0, 120.0));
    let seen = LAYOUTS.load(Ordering::SeqCst);
    if place(app, width, height.max(1.0)).is_err() {
        return;
    }
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        tokio::time::sleep(REVEAL_TIMEOUT).await;
        if LAYOUTS.load(Ordering::SeqCst) != seen {
            return;
        }
        let main = app.clone();
        let _ = app.run_on_main_thread(move || {
            // Re-check on the UI thread: a layout may have arrived meanwhile.
            if LAYOUTS.load(Ordering::SeqCst) == seen
                && let Some(window) = main.get_webview_window(LABEL)
            {
                let _ = platform::hide(&window);
            }
        });
    });
}

/// Opens the overview in the island (from the tray).
pub fn show_overview(app: &AppHandle) {
    reveal(app);
    let _ = app.emit_to(LABEL, EVENT_OVERVIEW, ());
}

/// Resizes and anchors the overlay on the frontend's request; a height below 1
/// hides it.
pub fn layout(app: &AppHandle, width: f64, height: f64) -> Result<()> {
    LAYOUTS.fetch_add(1, Ordering::SeqCst);
    place(app, width, height)
}

fn place(app: &AppHandle, width: f64, height: f64) -> Result<()> {
    let Some(window) = window(app) else {
        return Ok(());
    };
    if !width.is_finite() || !height.is_finite() || height < 1.0 || width < 1.0 {
        return platform::hide(&window);
    }
    let position = app
        .state::<AppState>()
        .config
        .lock()
        .expect("config lock")
        .prefs
        .position;
    let Some((origin, size)) = frame(&window, position, width, height)? else {
        return Ok(());
    };
    // Every move/resize/show makes WebView2 and the GPU driver rebuild the
    // transparent surface; skip calls that would change nothing.
    if platform::is_visible(&window)
        && window.outer_position().ok() == Some(origin)
        && window.outer_size().ok() == Some(size)
    {
        return Ok(());
    }
    platform::show_at(&window, origin, size)
}

/// Makes clicks on the overlay never activate it (buttons work without focus).
pub fn init(app: &AppHandle) -> Result<()> {
    match window(app) {
        Some(window) => platform::make_non_activating(&window),
        None => Ok(()),
    }
}

/// Gives the island keyboard focus (the user clicked into the reply field),
/// remembering which window had it.
pub fn take_focus(app: &AppHandle) -> Result<()> {
    match window(app) {
        Some(window) => platform::take_focus(&window),
        None => Ok(()),
    }
}

/// Hands keyboard focus back to the window that had it before.
pub fn release_focus() {
    platform::release_focus();
}

#[cfg(windows)]
mod platform {
    use std::sync::Mutex;

    use tauri::{PhysicalPosition, PhysicalSize, WebviewWindow};
    use windows::Win32::Foundation::HWND;
    use windows::Win32::UI::WindowsAndMessaging::{
        GWL_EXSTYLE, GetForegroundWindow, GetWindowLongPtrW, HWND_TOPMOST, IsWindowVisible,
        SET_WINDOW_POS_FLAGS, SW_HIDE, SWP_NOACTIVATE, SWP_SHOWWINDOW, SetForegroundWindow,
        SetWindowLongPtrW, SetWindowPos, ShowWindow, WS_EX_NOACTIVATE,
    };

    use crate::error::Result;

    /// The window that had focus before the island took it (as a raw handle).
    static PREVIOUS: Mutex<Option<isize>> = Mutex::new(None);

    pub fn make_non_activating(window: &WebviewWindow) -> Result<()> {
        let hwnd = window.hwnd()?;
        // SAFETY: plain style bit update on our own window.
        unsafe {
            let style = GetWindowLongPtrW(hwnd, GWL_EXSTYLE);
            SetWindowLongPtrW(hwnd, GWL_EXSTYLE, style | WS_EX_NOACTIVATE.0 as isize);
        }
        Ok(())
    }

    pub fn take_focus(window: &WebviewWindow) -> Result<()> {
        let own = window.hwnd()?;
        // SAFETY: querying the foreground window has no preconditions.
        let previous = unsafe { GetForegroundWindow() };
        if previous != own && !previous.is_invalid() {
            *PREVIOUS.lock().expect("focus lock") = Some(previous.0 as isize);
        }
        // Allowed: the click that triggers this was the last input event.
        window.set_focus()?;
        Ok(())
    }

    pub fn release_focus() {
        if let Some(previous) = PREVIOUS.lock().expect("focus lock").take() {
            // SAFETY: a stale handle just makes the call fail.
            unsafe {
                let _ = SetForegroundWindow(HWND(previous as *mut _));
            }
        }
    }

    pub fn is_visible(window: &WebviewWindow) -> bool {
        window
            .hwnd()
            .is_ok_and(|hwnd| unsafe { IsWindowVisible(hwnd).as_bool() })
    }

    pub fn show_at(
        window: &WebviewWindow,
        position: PhysicalPosition<i32>,
        size: PhysicalSize<u32>,
    ) -> Result<()> {
        let hwnd = window.hwnd()?;
        // Only "show" when hidden: re-showing a visible window forces a repaint flash.
        let show = if is_visible(window) {
            SET_WINDOW_POS_FLAGS(0)
        } else {
            SWP_SHOWWINDOW
        };
        // One call: move, resize, raise to topmost and show – without activating.
        unsafe {
            let _ = SetWindowPos(
                hwnd,
                Some(HWND_TOPMOST),
                position.x,
                position.y,
                size.width as i32,
                size.height as i32,
                SWP_NOACTIVATE | show,
            );
        }
        Ok(())
    }

    pub fn hide(window: &WebviewWindow) -> Result<()> {
        if !is_visible(window) {
            return Ok(());
        }
        let hwnd = window.hwnd()?;
        unsafe {
            let _ = ShowWindow(hwnd, SW_HIDE);
        }
        Ok(())
    }
}

#[cfg(not(windows))]
mod platform {
    use tauri::{PhysicalPosition, PhysicalSize, WebviewWindow};

    use crate::error::Result;

    pub fn make_non_activating(_window: &WebviewWindow) -> Result<()> {
        Ok(())
    }

    pub fn take_focus(window: &WebviewWindow) -> Result<()> {
        Ok(window.set_focus()?)
    }

    pub fn release_focus() {}

    pub fn is_visible(window: &WebviewWindow) -> bool {
        window.is_visible().unwrap_or(false)
    }

    pub fn show_at(
        window: &WebviewWindow,
        position: PhysicalPosition<i32>,
        size: PhysicalSize<u32>,
    ) -> Result<()> {
        window.set_size(size)?;
        window.set_position(position)?;
        window.show()?;
        Ok(())
    }

    pub fn hide(window: &WebviewWindow) -> Result<()> {
        Ok(window.hide()?)
    }
}
