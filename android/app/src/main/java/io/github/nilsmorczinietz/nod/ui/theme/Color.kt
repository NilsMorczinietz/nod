package io.github.nilsmorczinietz.nod.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Nod palette; names and values mirror design/tokens.json (v4) exactly. */
@Immutable
data class NodColors(
    val background: Color,
    val card: Color,
    val popover: Color,
    val muted: Color,
    val accent: Color,
    val foreground: Color,
    val mutedForeground: Color,
    val primary: Color,
    val buttonPrimary: Color,
    val buttonPrimaryForeground: Color,
    val primaryGlow: Color,
    val primaryDrop: Color,
    val rule: Color,
    val input: Color,
    val destructive: Color,
    val danger: Color,
    val waiting: Color,
    val waitingText: Color,
    val success: Color,
    /**
     * Brand-coloured TEXT: teal in light mode, plain foreground in dark mode
     * (teal stays for non-text accents: button fill, dots, focus, selection).
     */
    val accentText: Color,
    /** The 1 dp inset highlight of `surfaceSm` (white 90 % light / 6 % dark). */
    val surfaceHighlight: Color,
    val isDark: Boolean,
)

val LightNodColors = NodColors(
    background = Color(0xFFF1F6F6),
    card = Color(0xFFFAFCFC),
    popover = Color(0xFFFFFFFF),
    muted = Color(0xFFE7ECEC),
    accent = Color(0xFFDDE7E6),
    foreground = Color(0xFF061414),
    mutedForeground = Color(0xFF425B5A),
    primary = Color(0xFF00595A),
    buttonPrimary = Color(0xFF007273),
    buttonPrimaryForeground = Color(0xFFFFFFFF),
    primaryGlow = Color(0xFF00A4A4),
    primaryDrop = Color(0x26062E2E),
    rule = Color(0xFFE7ECEC),
    input = Color(0xFFE5EDED),
    destructive = Color(0xFFD40924),
    danger = Color(0xFFD40924),
    waiting = Color(0xFFF99C00),
    waitingText = Color(0xFFB75000),
    success = Color(0xFF009767),
    accentText = Color(0xFF00595A),
    surfaceHighlight = Color(0xE6FFFFFF),
    isDark = false,
)

val DarkNodColors = NodColors(
    background = Color(0xFF010707),
    card = Color(0xFF031010),
    popover = Color(0xFF051515),
    muted = Color(0xFF0A1414),
    accent = Color(0xFF0B1E1E),
    foreground = Color(0xFFEEF3F3),
    mutedForeground = Color(0xFF7B9595),
    primary = Color(0xFF00A4A4),
    buttonPrimary = Color(0xFF007273),
    buttonPrimaryForeground = Color(0xFFFFFFFF),
    primaryGlow = Color(0xFF72D0D0),
    primaryDrop = Color(0x73000000),
    rule = Color(0xFF1B2323),
    input = Color(0xFF0C1919),
    destructive = Color(0xFFA20519),
    danger = Color(0xFFFF837B),
    waiting = Color(0xFFF99C00),
    waitingText = Color(0xFFFCBB00),
    success = Color(0xFF00BB7F),
    accentText = Color(0xFFEEF3F3),
    surfaceHighlight = Color(0x0FFFFFFF),
    isDark = true,
)

val LocalNodColors = staticCompositionLocalOf { LightNodColors }
