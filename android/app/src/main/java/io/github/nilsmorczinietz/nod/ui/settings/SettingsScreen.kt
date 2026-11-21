package io.github.nilsmorczinietz.nod.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.nilsmorczinietz.nod.data.model.RoutingMode
import io.github.nilsmorczinietz.nod.data.store.ThemeMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nilsmorczinietz.nod.BuildConfig
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.ui.appContainer
import io.github.nilsmorczinietz.nod.ui.components.NodButton
import io.github.nilsmorczinietz.nod.ui.components.NodButtonStyle
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType
import io.github.nilsmorczinietz.nod.ui.components.Eyebrow
import io.github.nilsmorczinietz.nod.ui.components.openUrl
import io.github.nilsmorczinietz.nod.data.update.UpdateChecker
import io.github.nilsmorczinietz.nod.data.update.UpdateState
import androidx.compose.foundation.clickable
import io.github.nilsmorczinietz.nod.ui.components.surfaceSm
import io.github.nilsmorczinietz.nod.ui.theme.NodShapes
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = NodTheme.colors
    val context = LocalContext.current
    val notifier = appContainer().notifier
    var notificationsEnabled by remember { mutableStateOf(notifier.canPost()) }
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }
    // Re-check when returning from the system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsEnabled = notifier.canPost() }

    val failed = stringResource(R.string.message_failed)
    LaunchedEffect(viewModel) { viewModel.errors.collect { onMessage(failed) } }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.foreground)
            }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = NodType.h2,
                color = colors.foreground,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            SectionHeader(stringResource(R.string.settings_section_routing))
            // Segmented-style selector: muted track, active option = card + surfaceSm.
            Column(
                Modifier.fillMaxWidth().background(colors.muted, NodShapes.xl).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RoutingMode.entries.forEach { mode ->
                    ModeRow(mode, selected = state.mode == mode, onSelect = { viewModel.setMode(mode) })
                }
            }

            SectionHeader(stringResource(R.string.settings_section_appearance))
            ThemeSelector(state.themeMode, viewModel::setThemeMode)
            // Configuration.fontWeightAdjustment ("Bold text") exists from Android 12.
            if (Build.VERSION.SDK_INT >= 31) {
                Spacer(Modifier.height(8.dp))
                Group {
                    ToggleRow(
                        title = stringResource(R.string.settings_system_bold),
                        subtitle = stringResource(R.string.settings_system_bold_hint),
                        checked = state.useSystemBold,
                        onChange = viewModel::setUseSystemBold,
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_section_requests))
            Group {
                ToggleRow(
                    title = stringResource(R.string.settings_lockscreen_details),
                    subtitle = stringResource(R.string.settings_lockscreen_details_hint),
                    checked = state.showOnLockScreen,
                    onChange = viewModel::setShowOnLockScreen,
                )
                HorizontalDivider(color = colors.rule)
                ToggleRow(
                    title = stringResource(R.string.settings_require_unlock),
                    subtitle = stringResource(
                        if (Build.VERSION.SDK_INT >= 31) R.string.settings_require_unlock_hint else R.string.settings_require_unlock_unsupported,
                    ),
                    checked = state.requireUnlock,
                    onChange = viewModel::setRequireUnlock,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_delivery))
            Group {
                InfoRow(
                    stringResource(R.string.settings_notifications),
                    stringResource(if (notificationsEnabled) R.string.settings_notifications_on else R.string.settings_notifications_off),
                    valueColor = if (notificationsEnabled) colors.success else colors.waiting,
                )
                if (!notificationsEnabled) {
                    NodButton(
                        stringResource(R.string.action_open_settings),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                        style = NodButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(color = colors.rule)
                InfoRow(
                    stringResource(R.string.settings_push),
                    stringResource(if (state.pushAvailable) R.string.settings_push_on else R.string.settings_push_off),
                    valueColor = if (state.pushAvailable) colors.success else colors.mutedForeground,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_device))
            Group {
                InfoRow(stringResource(R.string.settings_device), state.deviceName)
                HorizontalDivider(color = colors.rule)
                InfoRow(stringResource(R.string.settings_relay), state.relayUrl.removePrefix("https://"), mono = true)
            }

            SectionHeader(stringResource(R.string.settings_section_about))
            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
            Group {
                InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME, mono = true)
                HorizontalDivider(color = colors.rule)
                UpdateRow(updateState, onCheck = viewModel::checkForUpdates, onOpen = context::openUrl)
                HorizontalDivider(color = colors.rule)
                LinkRow(stringResource(R.string.settings_release_notes)) {
                    context.openUrl((updateState as? UpdateState.Available)?.info?.notesUrl ?: UpdateChecker.RELEASES_URL)
                }
                HorizontalDivider(color = colors.rule)
                LinkRow(stringResource(R.string.settings_source_code)) { context.openUrl(UpdateChecker.REPO_URL) }
            }

            Spacer(Modifier.height(12.dp))
            NodButton(
                stringResource(R.string.action_unpair),
                onClick = { confirmUnpair = true },
                style = NodButtonStyle.Secondary,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text(stringResource(R.string.unpair_title)) },
            text = { Text(stringResource(R.string.unpair_body)) },
            confirmButton = {
                TextButton(onClick = { confirmUnpair = false; viewModel.unpair() }) {
                    Text(stringResource(R.string.action_unpair), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text(stringResource(R.string.action_cancel), color = colors.foreground) }
            },
            containerColor = colors.popover,
            titleContentColor = colors.foreground,
            textContentColor = colors.mutedForeground,
        )
    }
}

@Composable
private fun UpdateRow(state: UpdateState, onCheck: () -> Unit, onOpen: (String) -> Unit) {
    val colors = NodTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(R.string.settings_updates), style = NodType.small, color = colors.foreground)
            val status = when (state) {
                is UpdateState.Available -> stringResource(R.string.update_available, state.info.version)
                UpdateState.Checking -> stringResource(R.string.update_checking)
                UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                UpdateState.Failed -> stringResource(R.string.update_failed)
                UpdateState.Unknown -> null
            }
            status?.let { Text(it, style = NodType.meta, color = colors.mutedForeground) }
        }
        if (state is UpdateState.Available) {
            NodButton(stringResource(R.string.action_download), { onOpen(state.info.downloadUrl) }, style = NodButtonStyle.Text)
        } else {
            NodButton(
                stringResource(R.string.action_check_updates),
                onCheck,
                style = NodButtonStyle.Text,
                enabled = state != UpdateState.Checking,
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = NodType.small, color = NodTheme.colors.foreground, modifier = Modifier.weight(1f))
        Text("↗", style = NodType.small, color = NodTheme.colors.mutedForeground)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Eyebrow(text, Modifier.padding(top = 20.dp, bottom = 2.dp))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .surfaceSm()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

/** Segmented control (h 36, radius 10): active = card + surfaceSm, inactive muted text. */
@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = NodTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.muted, NodShapes.lg).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val active = mode == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 34.dp)
                    .then(if (active) Modifier.surfaceSm(NodShapes.md) else Modifier)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(mode) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(
                        when (mode) {
                            ThemeMode.System -> R.string.theme_system
                            ThemeMode.Light -> R.string.theme_light
                            ThemeMode.Dark -> R.string.theme_dark
                        },
                    ),
                    style = NodType.xs.copy(fontSize = 12.5.sp),
                    color = if (active) colors.foreground else colors.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun ModeRow(mode: RoutingMode, selected: Boolean, onSelect: () -> Unit) {
    val colors = NodTheme.colors
    val (title, subtitle) = when (mode) {
        RoutingMode.Away -> R.string.mode_away to R.string.mode_away_hint
        RoutingMode.Always -> R.string.mode_always to R.string.mode_always_hint
        RoutingMode.Updates -> R.string.mode_updates to R.string.mode_updates_hint
        RoutingMode.Terminal -> R.string.mode_terminal to R.string.mode_terminal_hint
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.surfaceSm(NodShapes.lg) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(stringResource(title), style = NodType.small, color = if (selected) colors.foreground else colors.mutedForeground)
        Text(stringResource(subtitle), style = NodType.xs.copy(fontWeight = FontWeight.Normal), color = colors.mutedForeground)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = NodTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = NodType.small, color = colors.foreground)
            Text(subtitle, style = NodType.xs, color = colors.mutedForeground)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.buttonPrimaryForeground,
                checkedTrackColor = colors.buttonPrimary,
                checkedBorderColor = colors.buttonPrimary,
                uncheckedThumbColor = colors.mutedForeground,
                uncheckedTrackColor = colors.muted,
                uncheckedBorderColor = colors.muted,
            ),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = NodTheme.colors.mutedForeground, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NodType.small, color = NodTheme.colors.foreground, modifier = Modifier.padding(end = 16.dp))
        Text(
            value,
            style = if (mono) NodType.mono else NodType.body,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
