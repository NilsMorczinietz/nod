//! "Is the user at this PC?" — any keyboard or mouse input within the last
//! three minutes. The relay uses this in mode "away" to keep the phone quiet
//! while the desktop island is in front of the user.

/// Input more recent than this counts as present.
const IDLE_AFTER_MS: u32 = 180_000;

#[cfg(windows)]
pub fn user_active() -> bool {
    use windows::Win32::System::SystemInformation::GetTickCount;
    use windows::Win32::UI::Input::KeyboardAndMouse::{GetLastInputInfo, LASTINPUTINFO};

    let mut info = LASTINPUTINFO {
        cbSize: size_of::<LASTINPUTINFO>() as u32,
        dwTime: 0,
    };
    // SAFETY: `info` is a valid, correctly sized LASTINPUTINFO.
    if !unsafe { GetLastInputInfo(&mut info) }.as_bool() {
        return true; // Unknown: assume present so the phone isn't spammed.
    }
    // Both are 32-bit tick counts; wrapping_sub handles the 49.7-day rollover.
    let idle = unsafe { GetTickCount() }.wrapping_sub(info.dwTime);
    idle < IDLE_AFTER_MS
}

/// Other platforms: always report present (the island is the primary surface).
#[cfg(not(windows))]
pub fn user_active() -> bool {
    let _ = IDLE_AFTER_MS;
    true
}
