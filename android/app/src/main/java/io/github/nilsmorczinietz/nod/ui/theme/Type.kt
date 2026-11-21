package io.github.nilsmorczinietz.nod.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.nilsmorczinietz.nod.R

private val GEIST_WEIGHTS = listOf(300, 400, 500, 600)
private val GEIST_MONO_WEIGHTS = listOf(400, 500)

/**
 * One entry per weight, each pinning the variable font's `wght` axis. [wghtOffset]
 * is subtracted from the axis value to cancel a weight the platform adds on top
 * (see FontWeights.kt); the entry's nominal weight stays the same.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, weights: List<Int>, wghtOffset: Int = 0) = FontFamily(
    weights.map { weight ->
        Font(
            resId = resId,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight((weight - wghtOffset).coerceIn(1, 1000))),
        )
    },
)

/** Geist, Geist Mono (variable) and Geist Pixel Square, all SIL OFL 1.1 (see third_party/geist). */
val Geist = variableFamily(R.font.geist, GEIST_WEIGHTS)
val GeistMono = variableFamily(R.font.geist_mono, GEIST_MONO_WEIGHTS)
val GeistPixel = FontFamily(Font(R.font.geist_pixel_square, FontWeight.Medium))

/** [Geist] and [GeistMono] with their `wght` axis lowered by [offset]. */
internal fun weightCompensatedFamilies(offset: Int): Map<FontFamily, FontFamily> = mapOf(
    Geist to variableFamily(R.font.geist, GEIST_WEIGHTS, offset),
    GeistMono to variableFamily(R.font.geist_mono, GEIST_MONO_WEIGHTS, offset),
)

/**
 * Type system from design/tokens.json (v4), built on a given Geist / Geist Mono
 * family pair (see FontWeights.kt for why the families can vary).
 */
@Immutable
class NodTypeScale(geist: FontFamily, mono: FontFamily) {
    /** Screen titles ("2 waiting"): light weight, tight tracking. */
    val h2 = TextStyle(fontFamily = geist, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.022).em)

    /** Panel titles. */
    val h3 = TextStyle(fontFamily = geist, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.01).em)

    /** Card titles. */
    val heading = TextStyle(fontFamily = geist, fontSize = 16.sp, lineHeight = 21.6.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em)
    val body = TextStyle(fontFamily = geist, fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val small = TextStyle(fontFamily = geist, fontSize = 14.sp, lineHeight = 20.3.sp, fontWeight = FontWeight.Medium)
    val xs = TextStyle(fontFamily = geist, fontSize = 12.sp, lineHeight = 16.8.sp, fontWeight = FontWeight.Medium)

    /** Geist Mono 11/500, uppercase (apply `.uppercase()`), tracking 0.2em. */
    val eyebrow = TextStyle(fontFamily = mono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.em)

    /** Big numbers computed from real data, in Geist Pixel Square with tabular figures. */
    val stat = TextStyle(fontFamily = GeistPixel, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum")

    /** Countdown under a command ("expires in 1:32"): 11 sp, tabular. */
    val countdown = TextStyle(fontFamily = geist, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")

    /** Meta lines ("Cursor · nod · now"). */
    val meta = TextStyle(fontFamily = geist, fontSize = 12.sp, lineHeight = 16.8.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")

    /** Big plain statements in Geist Pixel ("Nothing waiting."). */
    val pixelHeadline = TextStyle(fontFamily = GeistPixel, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em)

    /** Times, ledgers. */
    val mono = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 18.6.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")

    /** Commands and diffs. */
    val code = TextStyle(fontFamily = mono, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
}

/** Default scale; [ProvideNodFonts] swaps it when the system adds font weight. */
val LocalNodTypeScale = staticCompositionLocalOf { NodTypeScale(Geist, GeistMono) }

/** Styles of the current [NodTypeScale]; see [NodTypeScale] for what each one is for. */
object NodType {
    val h2: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.h2
    val h3: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.h3
    val heading: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.heading
    val body: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.body
    val small: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.small
    val xs: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.xs
    val eyebrow: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.eyebrow
    val stat: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.stat
    val countdown: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.countdown
    val meta: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.meta
    val pixelHeadline: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.pixelHeadline
    val mono: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.mono
    val code: TextStyle @Composable @ReadOnlyComposable get() = LocalNodTypeScale.current.code
}

/** Every Material slot maps to Geist so no component falls back to Roboto. */
fun nodTypography(type: NodTypeScale) = Typography(
    displayLarge = type.h2,
    displayMedium = type.h2,
    displaySmall = type.h2,
    headlineLarge = type.h2,
    headlineMedium = type.h2,
    headlineSmall = type.h3,
    titleLarge = type.h3,
    titleMedium = type.heading,
    titleSmall = type.heading,
    bodyLarge = type.body,
    bodyMedium = type.body,
    bodySmall = type.xs,
    labelLarge = type.small,
    labelMedium = type.xs,
    labelSmall = type.xs,
)
