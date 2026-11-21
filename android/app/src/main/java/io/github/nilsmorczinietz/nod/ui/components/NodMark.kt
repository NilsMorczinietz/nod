package io.github.nilsmorczinietz.nod.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val GRID = 11

/** (col, row) pixels of the mark; mirrors design/mark.svg (see android/scripts/generate_icons.py). */
private val Cells = listOf(
    1 to 7, 1 to 8, 2 to 8, 2 to 9, 3 to 2, 3 to 3, 3 to 4, 3 to 9, 4 to 1, 4 to 2, 4 to 3, 4 to 4,
    4 to 5, 4 to 9, 5 to 1, 5 to 2, 5 to 3, 5 to 4, 5 to 5, 5 to 9, 6 to 1, 6 to 2, 6 to 3, 6 to 4,
    6 to 5, 6 to 9, 7 to 2, 7 to 3, 7 to 4, 7 to 9, 8 to 8, 8 to 9, 9 to 7, 9 to 8,
)

/** The Nod pixel mark: a head above an arc, drawn as rounded grid cells. */
@Composable
fun NodMark(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val cell = this.size.minDimension / GRID
        val inner = cell * 0.86f
        val inset = (cell - inner) / 2
        val radius = CornerRadius(inner * 0.22f)
        Cells.forEach { (col, row) ->
            drawRoundRect(color, Offset(col * cell + inset, row * cell + inset), Size(inner, inner), radius)
        }
    }
}
