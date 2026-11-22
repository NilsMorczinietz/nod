package io.github.nilsmorczinietz.nod.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.data.update.InstallState
import io.github.nilsmorczinietz.nod.data.update.UpdateInfo
import io.github.nilsmorczinietz.nod.data.update.UpdateInstaller
import io.github.nilsmorczinietz.nod.ui.appContainer
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme

/** The in-app installer; continues automatically after "Install unknown apps" was granted. */
@Composable
fun rememberUpdateInstaller(): UpdateInstaller {
    val installer = appContainer().updateInstaller
    LifecycleResumeEffect(installer) {
        installer.onResume()
        onPauseOrDispose { }
    }
    return installer
}

/** "Update 0.2.0 available", or where the in-app install currently is. */
@Composable
fun updateStatus(info: UpdateInfo, install: InstallState): String = when (install) {
    InstallState.Idle -> stringResource(R.string.update_available, info.version)
    is InstallState.NeedsPermission -> stringResource(R.string.update_needs_permission)
    is InstallState.Downloading -> install.progress?.let { stringResource(R.string.update_downloading, install.version, (it * 100).toInt()) }
        ?: stringResource(R.string.update_downloading_unknown, install.version)
    is InstallState.Verifying -> stringResource(R.string.update_verifying)
    is InstallState.WaitingForUser -> stringResource(R.string.update_waiting)
    is InstallState.Failed -> stringResource(R.string.update_install_failed, install.reason)
}

/** The single action next to the status: Update, Allow or Retry (nothing while busy). */
@Composable
fun UpdateAction(info: UpdateInfo, install: InstallState, installer: UpdateInstaller, onBrowser: () -> Unit) {
    val context = LocalContext.current
    when (install) {
        InstallState.Idle -> if (info.canInstallInApp) {
            NodButton(stringResource(R.string.action_update), { installer.install(info) }, style = NodButtonStyle.Text)
        } else {
            NodButton(stringResource(R.string.action_open_in_browser), onBrowser, style = NodButtonStyle.Text)
        }
        is InstallState.NeedsPermission -> NodButton(
            stringResource(R.string.action_allow),
            { context.startActivity(installer.permissionIntent()) },
            style = NodButtonStyle.Text,
        )
        is InstallState.Failed -> NodButton(stringResource(R.string.action_retry), { installer.install(info) }, style = NodButtonStyle.Text)
        is InstallState.Downloading, is InstallState.Verifying, is InstallState.WaitingForUser -> Unit
    }
}

/** Thin progress line while downloading. */
@Composable
fun UpdateProgress(install: InstallState, modifier: Modifier = Modifier) {
    if (install !is InstallState.Downloading) return
    val colors = NodTheme.colors
    val progress = install.progress
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier.fillMaxWidth().height(3.dp),
            color = colors.primary,
            trackColor = colors.muted,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(modifier.fillMaxWidth().height(3.dp), color = colors.primary, trackColor = colors.muted)
    }
}
