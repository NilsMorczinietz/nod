//! The always-on-top overlay window that hosts the Island (or the corner stack).
//!
//! The frontend measures what it needs and reports a logical size; a height of
//! 0 hides the window. The window is anchored according to the user's
//! position preference. On Windows it is shown without activation, so the
//! overlay never steals focus from the editor or terminal.
use tauri::{AppHandle, Emitter, Manager, PhysicalPosition, PhysicalSize, WebviewWindow};

use crate::error::Result;
use crate::state::AppState;
use crate::store::OverlayPosition;

pub const LABEL: &str = "overlay";
/// Asks the overlay to open the recent-activity overview.
pub const EVENT_OVERVIEW: &str = "nod://overview";

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

/// Shows the overlay (if hidden) so the frontend can render and measure new content.
pub fn reveal(app: &AppHandle) {
    if let Some(window) = window(app)
        && !platform::is_visible(&window)
    {
        let scale = window.scale_factor().unwrap_or(1.0);
        let (width, height) = window
            .inner_size()
            .map(|s| (s.width as f64 / scale, s.height as f64 / scale))
            .unwrap_or((480.0, 120.0));
        let _ = layout(app, width, height.max(1.0));
    }
}

/// Opens the overview in the island (from the tray).
pub fn show_overview(app: &AppHandle) {
    reveal(app);
    let _ = app.emit_to(LABEL, EVENT_OVERVIEW, ());
}

/// Resizes and anchors the overlay; a height below 1 hides it.
pub fn layout(app: &AppHandle, width: f64, height: f64) -> Result<()> {
    let Some(window) = window(app) else {
        return Ok(());
    };
    if height < 1.0 || width < 1.0 {
        return platform::hide(&window);
    }
    let position = app
        .state::<AppState>()
        .config
        .lock()
        .expect("config lock")
        .prefs
        .position;
    if let Some((origin, size)) = frame(&window, position, width, height)? {
        platform::show_at(&window, origin, size)?;
    }
    Ok(())
}

#[cfg(windows)]
mod platform {
    use tauri::{PhysicalPosition, PhysicalSize, WebviewWindow};
    use windows::Win32::UI::WindowsAndMessaging::{
        HWND_TOPMOST, IsWindowVisible, SET_WINDOW_POS_FLAGS, SW_HIDE, SWP_NOACTIVATE,
        SWP_SHOWWINDOW, SetWindowPos, ShowWindow,
    };

    use crate::error::Result;

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
