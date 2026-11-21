package io.github.nilsmorczinietz.nod.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nilsmorczinietz.nod.ui.theme.NodMotion
import io.github.nilsmorczinietz.nod.ui.theme.NodShapes
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.rememberReduceMotion

/**
 * `surfaceSm` from the tokens: `card` fill, no border, a hairline shadow and a
 * 1 dp inset highlight. A [waiting] card gets the 1 dp amber/50 outline instead.
 */
@Composable
fun Modifier.surfaceSm(
    shape: Shape = NodShapes.card,
    fill: Color = NodTheme.colors.card,
    waiting: Boolean = false,
): Modifier {
    val colors = NodTheme.colors
    val shadowColor = if (colors.isDark) Color(0x66000000) else Color(0x14000000)
    return this
        .shadow(1.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(fill)
        .border(1.dp, if (waiting) colors.waiting.copy(alpha = 0.5f) else colors.surfaceHighlight, shape)
}

/** Enter motion: opacity + 4 dp rise, 200 ms (opacity only with reduce motion). */
@Composable
fun Modifier.enterMotion(key: Any?): Modifier {
    val reduce = rememberReduceMotion()
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, NodMotion.enter()) }
    return graphicsLayer {
        alpha = progress.value
        if (!reduce) translationY = (1f - progress.value) * NodMotion.ENTER_OFFSET_DP.dp.toPx()
    }
}

/** Static placeholder shaped like the content it stands in for. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 14.dp, shape: Shape = NodShapes.sm) {
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(NodTheme.colors.muted))
}
