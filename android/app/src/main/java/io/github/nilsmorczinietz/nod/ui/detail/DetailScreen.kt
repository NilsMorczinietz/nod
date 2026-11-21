package io.github.nilsmorczinietz.nod.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.data.model.Item
import io.github.nilsmorczinietz.nod.data.model.ItemKind
import io.github.nilsmorczinietz.nod.data.model.ItemStatus
import io.github.nilsmorczinietz.nod.data.model.Question
import io.github.nilsmorczinietz.nod.ui.components.ClockTime
import io.github.nilsmorczinietz.nod.ui.components.CodeBlock
import io.github.nilsmorczinietz.nod.ui.components.ExpiryText
import io.github.nilsmorczinietz.nod.ui.components.NodButton
import io.github.nilsmorczinietz.nod.ui.components.NodButtonStyle
import io.github.nilsmorczinietz.nod.ui.components.Skeleton
import io.github.nilsmorczinietz.nod.ui.components.Badge
import io.github.nilsmorczinietz.nod.ui.components.Eyebrow
import io.github.nilsmorczinietz.nod.ui.components.surfaceSm
import io.github.nilsmorczinietz.nod.ui.components.askerLine
import io.github.nilsmorczinietz.nod.ui.components.statusOf
import io.github.nilsmorczinietz.nod.ui.theme.NodMotion
import io.github.nilsmorczinietz.nod.ui.theme.NodShapes
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    val selections by viewModel.selections.collectAsStateWithLifecycle()
    val submitting by viewModel.submitting.collectAsStateWithLifecycle()
    val colors = NodTheme.colors

    val alreadyAnswered = stringResource(R.string.message_already_answered)
    val alreadyAnsweredBy = stringResource(R.string.message_already_answered_by)
    val failed = stringResource(R.string.message_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DetailEvent.Done -> onBack()
                is DetailEvent.AlreadyAnswered -> onMessage(event.by?.let { alreadyAnsweredBy.format(it) } ?: alreadyAnswered)
                is DetailEvent.Failed -> onMessage(failed)
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        val current = item
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.foreground)
            }
            Spacer(Modifier.weight(1f))
            when {
                current == null -> Unit
                current.status == ItemStatus.Pending -> ExpiryText(current.expiresAt)
                else -> Badge(statusOf(current))
            }
        }
        if (current == null) {
            LoadingSkeleton()
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text("${askerLine(current)} · ${current.session.host}", style = NodType.meta, color = colors.mutedForeground)
            Spacer(Modifier.height(10.dp))
            Text(current.title, style = NodType.h2, color = colors.foreground)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockTime(current.createdAt)
                current.resolution?.let {
                    Text(
                        "  ·  " + stringResource(R.string.detail_answered_on, it.deviceName),
                        style = NodType.xs,
                        color = colors.mutedForeground,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            when (current.kind) {
                ItemKind.Permission -> current.permission?.let { permission ->
                    Eyebrow(permission.tool)
                    Spacer(Modifier.height(10.dp))
                    CodeBlock(permission.detail ?: permission.summary)
                }
                ItemKind.Question -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    current.questions.orEmpty().forEachIndexed { index, question ->
                        QuestionBlock(
                            question = question,
                            selected = selections[index].orEmpty() + current.resolution?.answers?.get(question.question).orEmpty(),
                            enabled = current.status == ItemStatus.Pending,
                            onToggle = { label -> viewModel.toggle(index, label, question.multiSelect) },
                        )
                    }
                }
                ItemKind.Plan -> current.plan?.let { MarkdownText(it.markdown) }
                else -> current.body?.let { Text(it, style = NodType.body, color = colors.foreground) }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (current.status == ItemStatus.Pending) {
            ActionBar(current, selections, busy = submitting != null, viewModel)
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.padding(end = 180.dp), height = 12.dp)
        Skeleton(Modifier.padding(end = 60.dp), height = 22.dp)
        Spacer(Modifier.height(8.dp))
        Skeleton(height = 88.dp)
    }
}

@Composable
private fun QuestionBlock(
    question: Question,
    selected: Set<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
) {
    val colors = NodTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(question.question, style = NodType.heading, color = colors.foreground)
        if (question.multiSelect) {
            Text(stringResource(R.string.question_multi_hint), style = NodType.xs, color = colors.mutedForeground)
        }
        Spacer(Modifier.height(2.dp))
        Column(
            Modifier.fillMaxWidth().background(colors.muted, NodShapes.xl).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        question.options.forEach { option ->
            val isSelected = option.label in selected
            val indicatorShape = if (question.multiSelect) NodShapes.sm else CircleShape
            Row(
                Modifier
                    .fillMaxWidth()
                    // Active option = card + surfaceSm, like the segmented control.
                    .then(if (isSelected) Modifier.surfaceSm(NodShapes.lg) else Modifier)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = if (question.multiSelect) Role.Checkbox else Role.RadioButton,
                        onClick = { onToggle(option.label) },
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(if (isSelected) colors.primary else Color.Transparent, indicatorShape)
                        .border(1.5.dp, if (isSelected) colors.primary else colors.mutedForeground, indicatorShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.buttonPrimaryForeground, modifier = Modifier.size(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(option.label, style = NodType.small, color = if (isSelected) colors.foreground else colors.mutedForeground)
                    option.description?.let { Text(it, style = NodType.xs, color = colors.mutedForeground) }
                }
            }
        }
        }
    }
}

/**
 * Pinned decision bar. Approve is the only primary button; Deny is outlined.
 * "Deny with note" / "Request changes" open an inline text field.
 */
@Composable
private fun ActionBar(
    item: Item,
    selections: Map<Int, Set<String>>,
    busy: Boolean,
    viewModel: DetailViewModel,
) {
    val colors = NodTheme.colors
    var noteOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var note by rememberSaveable(item.id) { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().background(colors.background)) {
        HorizontalDivider(thickness = 1.dp, color = colors.rule)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedVisibility(visible = noteOpen, enter = fadeIn(NodMotion.enter()), exit = fadeOut(NodMotion.enter())) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(if (item.kind == ItemKind.Plan) R.string.note_hint_plan else R.string.note_hint_deny),
                            style = NodType.body,
                        )
                    },
                    textStyle = NodType.body,
                    shape = NodShapes.lg,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.rule,
                        cursorColor = colors.primary,
                        focusedContainerColor = colors.input,
                        unfocusedContainerColor = colors.input,
                    ),
                )
            }
            when (item.kind) {
                ItemKind.Permission, ItemKind.Plan -> {
                    if (noteOpen) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NodButton(stringResource(R.string.action_cancel), { noteOpen = false }, Modifier.weight(1f), NodButtonStyle.Secondary, enabled = !busy)
                            NodButton(
                                stringResource(if (item.kind == ItemKind.Plan) R.string.action_send_changes else R.string.action_deny),
                                { viewModel.deny(note) },
                                Modifier.weight(1f),
                                NodButtonStyle.Secondary,
                                enabled = !busy && note.isNotBlank(),
                                destructive = item.kind == ItemKind.Permission,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (item.kind == ItemKind.Plan) {
                                NodButton(stringResource(R.string.action_request_changes), { noteOpen = true }, Modifier.weight(1f), NodButtonStyle.Secondary, enabled = !busy)
                            } else {
                                NodButton(stringResource(R.string.action_deny), { viewModel.deny() }, Modifier.weight(1f), NodButtonStyle.Secondary, enabled = !busy)
                            }
                            NodButton(stringResource(R.string.action_approve), { viewModel.allow() }, Modifier.weight(1f), enabled = !busy)
                        }
                        if (item.kind == ItemKind.Permission) {
                            // Two muted text actions under the main buttons, centred (ref: island request).
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                NodButton(stringResource(R.string.action_deny_with_note), { noteOpen = true }, style = NodButtonStyle.Text, enabled = !busy)
                                if (item.permission?.canAlwaysAllow == true) {
                                    Spacer(Modifier.width(12.dp))
                                    NodButton(stringResource(R.string.action_always_allow), { viewModel.allow(always = true) }, style = NodButtonStyle.Text, enabled = !busy)
                                }
                            }
                        }
                    }
                }
                ItemKind.Question -> {
                    val complete = item.questions.orEmpty().indices.all { selections[it].orEmpty().isNotEmpty() }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NodButton(stringResource(R.string.action_skip), { viewModel.deny() }, Modifier.weight(1f), NodButtonStyle.Secondary, enabled = !busy)
                        NodButton(stringResource(R.string.action_send_answer), { viewModel.submitAnswers() }, Modifier.weight(1f), enabled = complete && !busy)
                    }
                }
                else -> Unit
            }
        }
    }
}
