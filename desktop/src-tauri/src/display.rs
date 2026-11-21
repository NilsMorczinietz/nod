//! Display settings that affect how large the island should be.

/// The Windows "Make text bigger" setting (Settings › Accessibility › Text size),
/// stored as a percentage (100–225) in HKCU\Software\Microsoft\Accessibility.
#[cfg(windows)]
pub fn text_scale() -> f64 {
    use windows::Win32::System::Registry::{HKEY_CURRENT_USER, RRF_RT_REG_DWORD, RegGetValueW};
    use windows::core::w;

    let mut value: u32 = 100;
    let mut size = size_of::<u32>() as u32;
    // SAFETY: the out pointer and size describe a valid u32.
    let status = unsafe {
        RegGetValueW(
            HKEY_CURRENT_USER,
            w!("Software\\Microsoft\\Accessibility"),
            w!("TextScaleFactor"),
            RRF_RT_REG_DWORD,
            None,
            Some((&raw mut value).cast()),
            Some(&raw mut size),
        )
    };
    if status.is_err() {
        return 1.0;
    }
    f64::from(value.clamp(100, 225)) / 100.0
}

#[cfg(not(windows))]
pub fn text_scale() -> f64 {
    1.0
}
