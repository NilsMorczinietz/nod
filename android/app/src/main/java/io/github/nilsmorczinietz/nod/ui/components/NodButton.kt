package io.github.nilsmorczinietz.nod.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nilsmorczinietz.nod.ui.theme.NodMotion
import io.github.nilsmorczinietz.nod.ui.theme.NodShapes
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType

enum class NodButtonStyle {
    /** `buttonPrimary` with drop shadow and inset teal glow. One per surface (Approve). */
    Primary,

    /** `card` background, 1 dp `rule` border, foreground text. */
    Secondary,

    /** Text only, muted (Deny with note, Approve and remember, Resume). */
    Text,
}

/**
 * Approximates CSS `inset 0 1px 3px 2px <color>`: concentric 1 px rings fading
 * inwards, shifted 1 px down. [strength] fades it (pressed state).
 */
private fun DrawScope.insetGlow(color: Color, radius: Float, strength: Float) {
    if (strength <= 0f) return
    val px = 1.dp.toPx()
    val alphas = floatArrayOf(0.95f, 0.7f, 0.4f, 0.18f, 0.06f)
    alphas.forEachIndexed { i, a ->
        val inset = px * (i + 0.5f)
        drawRoundRect(
            color = color.copy(alpha = a * strength),
            topLeft = Offset(inset, inset + px),
            size = Size(size.width - 2 * inset, size.height - 2 * inset - px),
            cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
            style = Stroke(px),
        )
    }
}

/** CSS `inset 0 2px 6px rgba(0,0,0,.4)`: the pressed primary button. */
private fun DrawScope.insetShade(strength: Float, radius: Float) {
    if (strength <= 0f) return
    val px = 1.dp.toPx()
    floatArrayOf(0.4f, 0.28f, 0.18f, 0.1f, 0.05f, 0.02f).forEachIndexed { i, a ->
        val inset = px * (i + 0.5f)
        drawRoundRect(
            color = Color.Black.copy(alpha = a * strength),
            topLeft = Offset(inset, inset + 2 * px),
            size = Size(size.width - 2 * inset, size.height - 2 * inset - 2 * px),
            cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
            style = Stroke(px),
        )
    }
}

@Composable
fun NodButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NodButtonStyle = NodButtonStyle.Primary,
    enabled: Boolean = true,
    destructive: Boolean = false,
    height: Dp = 44.dp,
) {
    val colors = NodTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 0 = resting (glow), 1 = pressed (inset shade); 300 ms like the reference.
    val press by animateFloatAsState(if (pressed) 1f else 0f, NodMotion.button(), label = "press")
    val content = when {
        style == NodButtonStyle.Primary -> colors.buttonPrimaryForeground
        destructive -> colors.danger
        style == NodButtonStyle.Text -> colors.mutedForeground
        else -> colors.foreground
    }
    val surface = when (style) {
        NodButtonStyle.Primary -> Modifier
            .shadow(2.dp, NodShapes.button, ambientColor = colors.primaryDrop, spotColor = colors.primaryDrop)
            .clip(NodShapes.button)
            .background(colors.buttonPrimary)
            .drawWithContent {
                val r = 8.dp.toPx()
                insetGlow(colors.primaryGlow, r, 1f - press)
                insetShade(press, r)
                drawContent()
            }
        NodButtonStyle.Secondary -> Modifier
            .shadow(1.dp, NodShapes.button, ambientColor = Color(0x0F062E2E), spotColor = Color(0x0F062E2E))
            .clip(NodShapes.button)
            .background(colors.card)
            .border(1.dp, colors.rule, NodShapes.button)
        NodButtonStyle.Text -> Modifier.clip(NodShapes.button)
    }
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .then(surface)
            .clickable(
                interaction,
                if (style == NodButtonStyle.Primary) null else ripple(color = content),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = if (style == NodButtonStyle.Text) 36.dp else height)
            .padding(horizontal = if (style == NodButtonStyle.Text) 6.dp else 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, style = NodType.small, maxLines = 1)
    }
}
