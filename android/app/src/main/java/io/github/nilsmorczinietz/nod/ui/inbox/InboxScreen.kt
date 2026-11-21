package io.github.nilsmorczinietz.nod.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.data.ConnectionState
import io.github.nilsmorczinietz.nod.ui.components.Eyebrow
import io.github.nilsmorczinietz.nod.ui.components.openUrl
import io.github.nilsmorczinietz.nod.ui.components.BoardGroup
import io.github.nilsmorczinietz.nod.ui.components.DecisionLedger
import io.github.nilsmorczinietz.nod.ui.components.WorkingCard
import io.github.nilsmorczinietz.nod.ui.components.LiveDot
import io.github.nilsmorczinietz.nod.ui.components.NodButton
import io.github.nilsmorczinietz.nod.ui.components.NodButtonStyle
import io.github.nilsmorczinietz.nod.ui.components.NodMark
import io.github.nilsmorczinietz.nod.ui.components.RequestCard
import io.github.nilsmorczinietz.nod.ui.components.Stat
import io.github.nilsmorczinietz.nod.ui.components.StatRow
import io.github.nilsmorczinietz.nod.ui.components.surfaceSm
import io.github.nilsmorczinietz.nod.ui.theme.NodShapes
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onOpenItem: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = NodTheme.colors
    val waiting = state.pending.count { it.id !in state.resolving }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(state.connection, onOpenSettings)
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "headline") {
                    Column(Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Eyebrow(stringResource(R.string.section_overview))
                        if (waiting == 0) {
                            // Empty state: a plain statement in Geist Pixel (teal), one muted line below.
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.inbox_nothing_waiting), style = NodType.pixelHeadline, color = colors.accentText)
                                Text(stringResource(R.string.inbox_nothing_waiting_body), style = NodType.body, color = colors.mutedForeground)
                            }
                        } else {
                            Text(pluralStringResource(R.plurals.inbox_waiting, waiting, waiting), style = NodType.h2, color = colors.foreground)
                        }
                    }
                }
                item(key = "stats") {
                    StatRow(
                        listOf(
                            Stat(state.projectsToday, stringResource(R.string.stat_projects_today)),
                            Stat(state.approvedToday, stringResource(R.string.stat_approved_today)),
                            Stat(state.deniedToday, stringResource(R.string.stat_denied_today)),
                        ),
                    )
                }
                if (state.paused) {
                    item(key = "paused") { PausedNotice(onResume = viewModel::resume, modifier = Modifier.animateItem()) }
                }
                if (state.pending.isNotEmpty()) {
                    item(key = "waiting") {
                        BoardGroup(stringResource(R.string.group_waiting), waiting, colors.waiting, Modifier.animateItem()) {
                            state.pending.forEach { item ->
                                key(item.id) {
                                    RequestCard(
                                        item = item,
                                        resolving = state.resolving[item.id],
                                        onOpen = { onOpenItem(item.id) },
                                        onQuickDecision = { decision -> viewModel.quickResolve(item, decision) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.working.isNotEmpty()) {
                    item(key = "working") {
                        BoardGroup(stringResource(R.string.group_working), state.working.size, colors.success, Modifier.animateItem()) {
                            state.working.forEach { session -> key(session.project + session.since) { WorkingCard(session) } }
                        }
                    }
                }
                if (state.history.isNotEmpty()) {
                    item(key = "ledger") {
                        DecisionLedger(state.history, state.decisionsToday, onOpen = { onOpenItem(it.id) }, modifier = Modifier.animateItem())
                    }
                }
                update?.let { info ->
                    item(key = "update") {
                        UpdateBanner(
                            version = info.version,
                            onDownload = { context.openUrl(info.downloadUrl) },
                            onDismiss = { viewModel.dismissUpdate(info.version) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (state.history.isEmpty() && state.pending.isEmpty()) {
                    item(key = "hint") { TryIt(Modifier.animateItem().padding(top = 8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(connection: ConnectionState, onOpenSettings: () -> Unit) {
    val colors = NodTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NodMark(colors.accentText, size = 22.dp)
        Spacer(Modifier.size(6.dp))
        Text(stringResource(R.string.app_name), style = NodType.small, color = colors.foreground)
        Spacer(Modifier.weight(1f))
        val (label, tone) = when (connection) {
            ConnectionState.Live -> R.string.connection_live to colors.primary
            ConnectionState.Connecting -> R.string.connection_connecting to colors.waitingText
            ConnectionState.Offline -> R.string.connection_offline to colors.mutedForeground
        }
        // Pushary's "Live" badge: primary/10 (dark /15) with a pulsing 6 dp dot.
        Row(
            Modifier
                .background(tone.copy(alpha = if (colors.isDark) 0.15f else 0.10f), NodShapes.badge)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveDot(tone, live = connection == ConnectionState.Live)
            Spacer(Modifier.size(6.dp))
            // Teal only for the dot; the label text follows accentText (white in dark mode).
            Text(stringResource(label), style = NodType.xs, color = if (connection == ConnectionState.Live) colors.accentText else tone)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title), tint = colors.mutedForeground)
        }
    }
}

/** Empty state: one sentence with the command to try. */
@Composable
private fun TryIt(modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    val command = "nod test permission"
    val text = stringResource(R.string.inbox_try_it, command)
    val start = text.indexOf(command)
    Text(
        buildAnnotatedString {
            append(text)
            if (start >= 0) addStyle(SpanStyle(fontFamily = NodType.code.fontFamily, color = colors.foreground), start, start + command.length)
        },
        style = NodType.body,
        color = colors.mutedForeground,
        modifier = modifier.padding(start = 4.dp),
    )
}

/** Quiet, dismissible hint that a newer APK is on GitHub. */
@Composable
private fun UpdateBanner(version: String, onDownload: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    Row(
        modifier.fillMaxWidth().surfaceSm().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.update_available, version), style = NodType.small, color = colors.foreground, modifier = Modifier.weight(1f))
        NodButton(stringResource(R.string.action_download), onDownload, style = NodButtonStyle.Text)
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_dismiss), tint = colors.mutedForeground, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PausedNotice(onResume: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    Row(
        modifier.fillMaxWidth().surfaceSm().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.paused_body), style = NodType.body, color = colors.foreground, modifier = Modifier.weight(1f))
        NodButton(stringResource(R.string.action_resume), onClick = onResume, style = NodButtonStyle.Text)
    }
}
