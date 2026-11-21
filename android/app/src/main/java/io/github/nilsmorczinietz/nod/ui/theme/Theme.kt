package io.github.nilsmorczinietz.nod.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Radii (tokens: radius). Badges use md (8), not fully round. */
object NodShapes {
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(8.dp)
    val lg = RoundedCornerShape(10.dp)
    val xl = RoundedCornerShape(14.dp)
    val card = xl
    val button = md
    val badge = md
    val code = sm
}

/** Motion tokens (tokens: motion). */
object NodMotion {
    val EnterEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val ButtonEasing = CubicBezierEasing(0.165f, 0.84f, 0.44f, 1f)
    const val ENTER_MS = 200
    const val BUTTON_MS = 300
    const val ENTER_OFFSET_DP = 4

    fun <T> enter() = tween<T>(ENTER_MS, easing = EnterEasing)
    fun <T> button() = tween<T>(BUTTON_MS, easing = ButtonEasing)
}

object NodTheme {
    val colors: NodColors
        @Composable @ReadOnlyComposable get() = LocalNodColors.current
}

/** True when the user turned animations off ("Remove animations"); motion then crossfades only. */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun NodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkNodColors else LightNodColors
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = colors.buttonPrimary,
        onPrimary = colors.buttonPrimaryForeground,
        secondary = colors.primary,
        background = colors.background,
        onBackground = colors.foreground,
        surface = colors.card,
        onSurface = colors.foreground,
        surfaceVariant = colors.muted,
        onSurfaceVariant = colors.mutedForeground,
        surfaceContainer = colors.card,
        surfaceContainerHigh = colors.popover,
        surfaceContainerHighest = colors.popover,
        outline = colors.rule,
        outlineVariant = colors.rule,
        error = colors.danger,
    )
    CompositionLocalProvider(LocalNodColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = nodTypography(LocalNodTypeScale.current),
            shapes = Shapes(small = NodShapes.sm, medium = NodShapes.md, large = NodShapes.xl),
            content = content,
        )
    }
}
