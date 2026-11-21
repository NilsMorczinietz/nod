package io.github.nilsmorczinietz.nod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nilsmorczinietz.nod.ui.theme.NodShapes
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType

/**
 * Geist Mono 13 on `muted` with a 1 dp `rule` border, radius 6. Lines starting with "+ " / "- " get a
 * success / danger tint across the full width, like a diff. With
 * [scrollable], long lines scroll horizontally; otherwise they wrap.
 */
@Composable
fun CodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    scrollable: Boolean = true,
) {
    val colors = NodTheme.colors
    val lines = text.lines().take(maxLines)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(NodShapes.code)
            .background(colors.muted)
            .border(1.dp, colors.rule, NodShapes.code),
    ) {
        Column(
            Modifier
                .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()).width(IntrinsicSize.Max) else Modifier)
                // Every line spans the full block so diff tints reach the right edge.
                .widthIn(min = maxWidth)
                .padding(vertical = 10.dp),
        ) {
            lines.forEach { line ->
                val tint = when {
                    line.startsWith("+ ") -> colors.success
                    line.startsWith("- ") -> colors.danger
                    else -> null
                }
                Text(
                    line.ifEmpty { " " },
                    style = NodType.code,
                    color = colors.foreground,
                    softWrap = !scrollable,
                    maxLines = if (scrollable) 1 else 3,
                    overflow = if (scrollable) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tint?.copy(alpha = 0.12f) ?: Color.Transparent)
                        .padding(horizontal = 12.dp),
                )
            }
        }
    }
}
