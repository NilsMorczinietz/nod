package io.github.nilsmorczinietz.nod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nilsmorczinietz.nod.ui.theme.NodShapes
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType

/**
 * Desktop-style single-line input: `input` background, radius 8 and a
 * primary-tinted 1 dp border. Mono by default (links, codes); pass
 * [textStyle] = body for prose such as replies.
 */
@Composable
fun NodTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = NodType.code,
    height: Dp = 48.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    contentDescription: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = NodTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        textStyle = textStyle.copy(color = colors.foreground),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        modifier = modifier
            .height(height)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxSize()
                    .background(colors.input, NodShapes.md)
                    .border(1.dp, colors.primary.copy(alpha = if (focused) 0.9f else 0.45f), NodShapes.md)
                    .padding(start = 14.dp, end = if (trailing == null) 14.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle, color = colors.mutedForeground.copy(alpha = 0.7f), maxLines = 1)
                    field()
                }
                trailing?.invoke()
            }
        },
    )
}
