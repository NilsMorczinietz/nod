package io.github.nilsmorczinietz.nod.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.data.model.Item
import io.github.nilsmorczinietz.nod.data.model.ItemKind
import io.github.nilsmorczinietz.nod.data.model.Question
import io.github.nilsmorczinietz.nod.ui.theme.NodType

/**
 * Claude ended its turn with a question in prose: one question with options
 * (usually Yes / No) that also takes a free-text reply.
 */
val Item.replyQuestion: Question?
    get() = questions?.singleOrNull()?.takeIf { kind == ItemKind.Question && it.allowText }

/** What was answered: the free-text reply, else the chosen option(s). */
val Item.replyText: String?
    get() = resolution?.message?.takeIf(String::isNotBlank)
        ?: resolution?.answers?.values?.flatten()?.joinToString(", ")?.takeIf(String::isNotBlank)

/**
 * The options as equal secondary buttons (it's a reply, not an approval),
 * then "Reply…" with Send, which is the one primary action.
 */
@Composable
fun ReplyComposer(
    question: Question,
    enabled: Boolean,
    onOption: (String) -> Unit,
    onReply: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
) {
    var text by rememberSaveable(question.question) { mutableStateOf("") }
    val send = { if (enabled && text.isNotBlank()) onReply(text.trim()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (question.options.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    NodButton(
                        option.label,
                        onClick = { onOption(option.label) },
                        modifier = Modifier.weight(1f),
                        style = NodButtonStyle.Secondary,
                        enabled = enabled,
                        height = height,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NodTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.reply_placeholder),
                textStyle = NodType.body,
                height = height,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            NodButton(
                stringResource(R.string.action_send),
                onClick = send,
                modifier = Modifier.widthIn(min = 72.dp),
                enabled = enabled && text.isNotBlank(),
                height = height,
            )
        }
    }
}
