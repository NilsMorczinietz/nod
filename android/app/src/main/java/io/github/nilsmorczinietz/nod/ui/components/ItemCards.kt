package io.github.nilsmorczinietz.nod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.data.model.Decision
import io.github.nilsmorczinietz.nod.data.model.Item
import io.github.nilsmorczinietz.nod.data.model.ItemKind
import io.github.nilsmorczinietz.nod.data.model.ItemStatus
import io.github.nilsmorczinietz.nod.data.model.ResolveInput
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType

private val GroupShape = RoundedCornerShape(24.dp)

/**
 * A column group like Pushary's board: `muted`/40 track, radius 24, a header
 * with a status dot, the title and a tabular count, then its cards.
 */
@Composable
fun BoardGroup(
    title: String,
    count: Int,
    dot: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NodTheme.colors
    Column(modifier.fillMaxWidth().background(colors.muted.copy(alpha = 0.4f), GroupShape).padding(8.dp)) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(title, style = NodType.small, color = colors.foreground)
            Spacer(Modifier.width(8.dp))
            Text(count.toString(), style = NodType.small.copy(fontWeight = NodType.body.fontWeight), color = colors.mutedForeground)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** "Cursor · nod · now" */
@Composable
private fun MetaLine(vararg parts: String) {
    val colors = NodTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        parts.filter(String::isNotBlank).forEachIndexed { index, part ->
            if (index > 0) Text("  ·  ", style = NodType.meta, color = colors.mutedForeground.copy(alpha = 0.5f))
            Text(part, style = NodType.meta, color = colors.mutedForeground, maxLines = 1)
        }
    }
}

/**
 * A request waiting for you (Pushary's "Waiting for you" card): title and
 * meta, a hairline, the mono command, the amber expiry line and compact
 * Deny / Approve buttons (Approve on the right).
 */
@Composable
fun RequestCard(
    item: Item,
    resolving: Decision?,
    onOpen: () -> Unit,
    onQuickDecision: (Decision) -> Unit,
    modifier: Modifier = Modifier,
    onResolve: (ResolveInput) -> Unit = {},
) {
    val colors = NodTheme.colors
    val pending = item.status == ItemStatus.Pending
    val reply = item.replyQuestion
    Column(
        modifier = modifier
            .enterMotion(item.id)
            .surfaceSm(waiting = pending && resolving == null)
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(12.dp)) {
            // A reply question leads with the question itself ("Shall I also update the docs?").
            Text(reply?.question ?: item.title, style = NodType.heading, color = colors.foreground, maxLines = if (reply != null) 4 else 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            MetaLine(item.session.agentName, item.session.project, relativeTime(item.createdAt))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            val command = item.permission?.summary?.takeIf(String::isNotBlank)
            if (command != null) {
                Text(command, style = NodType.mono, color = colors.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else if (reply == null && !item.body.isNullOrBlank()) {
                Text(item.body, style = NodType.body.copy(fontSize = NodType.small.fontSize), color = colors.mutedForeground, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            if (pending) ExpiryText(item.expiresAt) else Badge(statusOf(item))
            Spacer(Modifier.height(12.dp))
            val busy = resolving != null || !pending
            if (reply != null) {
                ReplyComposer(
                    reply,
                    enabled = !busy,
                    onOption = { onResolve(ResolveInput(Decision.Answer, answers = mapOf(reply.question to listOf(it)))) },
                    onReply = { onResolve(ResolveInput(Decision.Answer, answers = emptyMap(), message = it)) },
                )
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item.kind) {
                    ItemKind.Permission -> {
                        NodButton(stringResource(R.string.action_deny), { onQuickDecision(Decision.Deny) }, Modifier.weight(1f), NodButtonStyle.Secondary, enabled = !busy, height = 40.dp)
                        NodButton(stringResource(R.string.action_approve), { onQuickDecision(Decision.Allow) }, Modifier.weight(1f), enabled = !busy, height = 40.dp)
                    }
                    // Approve is the only primary action; these open the detail.
                    ItemKind.Question -> NodButton(stringResource(R.string.action_answer), onOpen, Modifier.fillMaxWidth(), NodButtonStyle.Secondary, enabled = !busy, height = 40.dp)
                    ItemKind.Plan -> NodButton(stringResource(R.string.action_review_plan), onOpen, Modifier.fillMaxWidth(), NodButtonStyle.Secondary, enabled = !busy, height = 40.dp)
                    else -> Unit
                }
            }
        }
    }
}

/** An agent session that is currently working (derived from recent activity). */
data class WorkingSession(val project: String, val agent: String, val since: Long, val detail: String)

@Composable
fun WorkingCard(session: WorkingSession, modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    Column(modifier.fillMaxWidth().enterMotion(session.project).surfaceSm().padding(12.dp)) {
        Text(session.project, style = NodType.heading, color = colors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        MetaLine(session.agent, relativeTime(session.since))
        if (session.detail.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(session.detail, style = NodType.meta, color = colors.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Pushary's "Decision ledger": a `surface` card with a header and a count,
 * then single-line rows (mono command · badge · time) separated by hairlines.
 */
@Composable
fun DecisionLedger(items: List<Item>, todayCount: Int, onOpen: (Item) -> Unit, modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    Column(modifier.fillMaxWidth().surfaceSm()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ledger_title), style = NodType.small, color = colors.foreground, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.ledger_today, todayCount), style = NodType.meta, color = colors.mutedForeground)
        }
        items.forEachIndexed { index, item ->
            if (index > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.rule))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val replied = item.replyText?.takeIf { item.replyQuestion != null }
                val command = when {
                    replied != null && item.resolution?.message != null -> stringResource(R.string.ledger_replied, replied)
                    replied != null -> replied
                    else -> item.permission?.summary?.takeIf(String::isNotBlank) ?: item.replyQuestion?.question ?: item.title
                }
                Text(
                    command,
                    style = NodType.mono,
                    color = colors.foreground.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Badge(statusOf(item))
                Text(relativeTime(item.createdAt), style = NodType.meta, color = colors.mutedForeground, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** One cell of the stat row: Geist Pixel number over a muted 12 sp caption. */
data class Stat(val value: Int, val label: String)

/** Equal cells separated by hairlines; numbers are computed from real data only. */
@Composable
fun StatRow(stats: List<Stat>, modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).surfaceSm()) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.rule))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stat.value.toString(), style = NodType.stat, color = colors.accentText)
                Text(stat.label, style = NodType.meta, color = colors.mutedForeground, maxLines = 1)
            }
        }
    }
}
