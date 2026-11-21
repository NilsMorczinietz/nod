package io.github.nilsmorczinietz.nod.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.createFontFamilyResolver

/**
 * Renders Nod's designed font weights even when the system "Bold text" setting
 * is on (Android 12+, `Configuration.fontWeightAdjustment`, usually +300).
 *
 * The adjustment reaches Compose text twice: Compose adds it to the requested
 * weight when it picks a font, and the platform adds it to the `wght` axis of
 * variable fonts. Geist Light headings then render as Black. Unless the user
 * opts in ([useSystemBold], Settings › Appearance), this provides a font
 * resolver without the first and Geist families whose `wght` axis is lowered by
 * the second.
 */
@Composable
fun ProvideNodFonts(useSystemBold: Boolean, content: @Composable () -> Unit) {
    val adjustment = LocalConfiguration.current.fontWeightAdjustmentOrZero()
    if (useSystemBold || adjustment == 0) {
        content()
        return
    }
    val context = LocalContext.current
    val resolver = remember(context, adjustment) { createFontFamilyResolver(context.withoutFontWeightAdjustment()) }
    val scale = remember(adjustment) {
        val families = weightCompensatedFamilies(adjustment)
        NodTypeScale(families.getValue(Geist), families.getValue(GeistMono))
    }
    CompositionLocalProvider(LocalFontFamilyResolver provides resolver, LocalNodTypeScale provides scale, content = content)
}

private fun Configuration.fontWeightAdjustmentOrZero(): Int =
    if (Build.VERSION.SDK_INT >= 31 && fontWeightAdjustment != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED) fontWeightAdjustment else 0

/** Same context, but Compose's font matching no longer adds the system adjustment. */
private fun Context.withoutFontWeightAdjustment(): Context {
    if (Build.VERSION.SDK_INT < 31) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { fontWeightAdjustment = 0 })
}
