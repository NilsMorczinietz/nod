package io.github.nilsmorczinietz.nod.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.data.model.Decision
import io.github.nilsmorczinietz.nod.data.model.Item
import io.github.nilsmorczinietz.nod.data.model.ItemKind
import io.github.nilsmorczinietz.nod.data.model.ItemStatus
import io.github.nilsmorczinietz.nod.ui.theme.NodShapes
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType
import io.github.nilsmorczinietz.nod.ui.theme.rememberReduceMotion

/**
 * Section eyebrow: a 24×1 dp rule in primary/30, then Geist Mono 11 uppercase
 * in primary. [inline] eyebrows (inside cards) are muted and have no rule.
 */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, inline: Boolean = false) {
    val colors = NodTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!inline) {
            Box(Modifier.width(24.dp).height(1.dp).background(colors.accentText.copy(alpha = 0.3f)))
            Box(Modifier.width(10.dp))
        }
        Text(text.uppercase(), style = NodType.eyebrow, color = if (inline) colors.mutedForeground else colors.accentText, maxLines = 1)
    }
}

enum class BadgeTone { Approved, Denied, Waiting, Neutral, Success }

/** Outcome label plus its badge tone. */
data class ItemStatusLabel(val label: String, val tone: BadgeTone)

@Composable
fun statusOf(item: Item): ItemStatusLabel = when (item.status) {
    ItemStatus.Pending -> ItemStatusLabel(stringResource(R.string.status_waiting), BadgeTone.Waiting)
    ItemStatus.Expired -> ItemStatusLabel(stringResource(R.string.status_expired), BadgeTone.Neutral)
    ItemStatus.Cancelled -> ItemStatusLabel(stringResource(R.string.status_withdrawn), BadgeTone.Neutral)
    ItemStatus.Info -> when (item.kind) {
        ItemKind.Failed -> ItemStatusLabel(stringResource(R.string.status_failed), BadgeTone.Denied)
        ItemKind.Attention -> ItemStatusLabel(stringResource(R.string.status_needs_you), BadgeTone.Waiting)
        else -> ItemStatusLabel(stringResource(R.string.status_done), BadgeTone.Approved)
    }
    ItemStatus.Resolved -> when (item.resolution?.decision) {
        Decision.Deny -> ItemStatusLabel(stringResource(R.string.status_denied), BadgeTone.Denied)
        Decision.Answer -> ItemStatusLabel(stringResource(R.string.status_answered), BadgeTone.Approved)
        else -> ItemStatusLabel(stringResource(R.string.status_approved), BadgeTone.Approved)
    }
}

/** Badge per tokens.badge: radius 8, 6×2 dp padding, 12/500, tinted by tone. */
@Composable
fun Badge(status: ItemStatusLabel, modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    val (fg, bg) = when (status.tone) {
        BadgeTone.Approved -> colors.accentText to colors.primary.copy(alpha = if (colors.isDark) 0.15f else 0.10f)
        BadgeTone.Denied -> colors.danger to colors.destructive.copy(alpha = if (colors.isDark) 0.20f else 0.10f)
        BadgeTone.Waiting -> colors.waitingText to colors.waiting.copy(alpha = 0.10f)
        BadgeTone.Neutral -> colors.mutedForeground to colors.muted
        BadgeTone.Success -> colors.success to colors.success.copy(alpha = if (colors.isDark) 0.15f else 0.10f)
    }
    Row(
        modifier.background(bg, NodShapes.badge).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (status.tone == BadgeTone.Waiting) Box(Modifier.size(6.dp).background(colors.waiting, CircleShape))
        Text(status.label, style = NodType.xs, color = fg, maxLines = 1)
    }
}

/** 6 dp primary dot; pulses (opacity 1 → 0.5, 2 s) while [live], static with reduce motion. */
@Composable
fun LiveDot(color: Color, live: Boolean, modifier: Modifier = Modifier) {
    val pulse = if (live && !rememberReduceMotion()) {
        val value by rememberInfiniteTransition(label = "live").animateFloat(
            initialValue = 1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
            label = "livePulse",
        )
        value
    } else {
        1f
    }
    Box(modifier.size(6.dp).alpha(pulse).background(color, CircleShape))
}

/** "Cursor · acme": who is asking. */
@Composable
fun askerLine(item: Item): String = stringResource(R.string.asker_line, item.session.agentName, item.session.project)
