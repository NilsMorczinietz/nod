package io.github.nilsmorczinietz.nod.ui.delivery

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.data.delivery.CheckState
import io.github.nilsmorczinietz.nod.data.delivery.DeliveryCheck
import io.github.nilsmorczinietz.nod.data.delivery.DeliveryStatus
import io.github.nilsmorczinietz.nod.push.PushState
import io.github.nilsmorczinietz.nod.ui.appContainer
import io.github.nilsmorczinietz.nod.ui.components.Badge
import io.github.nilsmorczinietz.nod.ui.components.BadgeTone
import io.github.nilsmorczinietz.nod.ui.components.Eyebrow
import io.github.nilsmorczinietz.nod.ui.components.ItemStatusLabel
import io.github.nilsmorczinietz.nod.ui.components.NodButton
import io.github.nilsmorczinietz.nod.ui.components.NodButtonStyle
import io.github.nilsmorczinietz.nod.ui.components.openUrl
import io.github.nilsmorczinietz.nod.ui.components.surfaceSm
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.NodType

private const val PUSH_GUIDE_URL =
    "https://github.com/NilsMorczinietz/nod/blob/main/docs/setup.md#4-push-notifications-on-android-optional-recommended"

/** Live statuses, re-read on every ON_RESUME (the user fixes things in system settings). */
@Composable
fun rememberDeliveryStatuses(): List<DeliveryStatus> {
    val checker = appContainer().delivery
    val pushState by checker.pushState.collectAsStateWithLifecycle()
    var resumes by remember { mutableStateOf(0) }
    LifecycleResumeEffect(checker) {
        resumes++
        onPauseOrDispose { }
    }
    return remember(checker, pushState, resumes) { checker.current() }
}

/** Critical checks that are not OK; empty when Nod can reach the lock screen. */
fun List<DeliveryStatus>.criticalProblems(): List<DeliveryCheck> =
    filter { it.check.critical && it.state == CheckState.ActionNeeded }.map { it.check }

/**
 * Opens the screen that fixes [DeliveryCheck]. Notifications first try the
 * runtime permission dialog (Android 13+), then fall back to the settings.
 */
@Composable
fun rememberDeliveryFix(): (DeliveryCheck) -> Unit {
    val context = LocalContext.current
    val checker = appContainer().delivery
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.startFirst(checker.fixIntents(DeliveryCheck.Notifications))
    }
    return remember(context, checker) {
        { check ->
            when {
                check == DeliveryCheck.Notifications && checker.needsNotificationPermission() ->
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                check == DeliveryCheck.Push && checker.pushState.value is PushState.Failed -> checker.retryPush()
                check == DeliveryCheck.Push -> context.openUrl(PUSH_GUIDE_URL)
                else -> context.startFirst(checker.fixIntents(check))
            }
        }
    }
}

private fun Context.startFirst(intents: List<Intent>) {
    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            Log.i("NodDelivery", "No screen for ${intent.action}, trying the next one")
        } catch (e: SecurityException) {
            Log.i("NodDelivery", "Not allowed to open ${intent.action}, trying the next one")
        }
    }
}

/** Rows for a surface: title + status badge, one explaining line, a fix button when needed. */
@Composable
fun DeliveryChecklist(statuses: List<DeliveryStatus>, onFix: (DeliveryCheck) -> Unit, modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    Column(modifier.fillMaxWidth()) {
        statuses.forEachIndexed { index, status ->
            if (index > 0) HorizontalDivider(color = colors.rule)
            DeliveryRow(status, onFix)
        }
    }
}

@Composable
private fun DeliveryRow(status: DeliveryStatus, onFix: (DeliveryCheck) -> Unit) {
    val colors = NodTheme.colors
    val copy = copyFor(status)
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(copy.title), style = NodType.small, color = colors.foreground, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Badge(badgeFor(status.state))
        }
        Spacer(Modifier.height(4.dp))
        Text(inlineCode(stringResource(copy.hint, *copy.hintArgs)), style = NodType.xs, color = colors.mutedForeground)
        if (status.state == CheckState.ActionNeeded || status.state == CheckState.Unknown) {
            NodButton(
                stringResource(copy.fix),
                onClick = { onFix(status.check) },
                style = NodButtonStyle.Secondary,
                height = 32.dp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun badgeFor(state: CheckState) = when (state) {
    CheckState.Ok -> ItemStatusLabel(stringResource(R.string.delivery_ok), BadgeTone.Success)
    CheckState.ActionNeeded -> ItemStatusLabel(stringResource(R.string.delivery_action_needed), BadgeTone.Waiting)
    CheckState.Unknown -> ItemStatusLabel(stringResource(R.string.delivery_check), BadgeTone.Neutral)
    CheckState.Pending -> ItemStatusLabel(stringResource(R.string.delivery_pending), BadgeTone.Neutral)
}

private class RowCopy(val title: Int, val hint: Int, val fix: Int, vararg val hintArgs: Any)

private fun copyFor(status: DeliveryStatus): RowCopy {
    val ok = status.state == CheckState.Ok
    return when (status.check) {
        DeliveryCheck.Notifications -> RowCopy(
            R.string.delivery_notifications,
            if (ok) R.string.delivery_notifications_ok else R.string.delivery_notifications_action,
            R.string.delivery_fix_allow,
        )
        DeliveryCheck.RequestsChannel -> RowCopy(
            R.string.delivery_channel,
            if (ok) R.string.delivery_channel_ok else R.string.delivery_channel_action,
            R.string.delivery_fix_open,
        )
        DeliveryCheck.LockScreen -> RowCopy(
            R.string.delivery_lock_screen,
            when (status.state) {
                CheckState.Ok -> R.string.delivery_lock_screen_ok
                CheckState.ActionNeeded -> R.string.delivery_lock_screen_action
                else -> R.string.delivery_lock_screen_unknown
            },
            R.string.delivery_fix_open,
        )
        DeliveryCheck.Battery -> RowCopy(
            R.string.delivery_battery,
            if (ok) R.string.delivery_battery_ok else R.string.delivery_battery_action,
            R.string.delivery_fix_allow,
        )
        DeliveryCheck.UnusedAppPause -> RowCopy(
            R.string.delivery_unused,
            if (ok) R.string.delivery_unused_ok else R.string.delivery_unused_action,
            R.string.delivery_fix_open,
        )
        DeliveryCheck.Push -> when {
            ok -> RowCopy(R.string.delivery_push, R.string.delivery_push_ok, R.string.delivery_fix_guide)
            status.state == CheckState.Pending -> RowCopy(R.string.delivery_push, R.string.delivery_push_registering, R.string.delivery_fix_guide)
            status.detail != null -> RowCopy(R.string.delivery_push, R.string.delivery_push_failed, R.string.delivery_fix_retry, status.detail)
            else -> RowCopy(R.string.delivery_push, R.string.delivery_push_action, R.string.delivery_fix_guide)
        }
    }
}

/** `code` spans in Geist Mono. */
@Composable
private fun inlineCode(text: String): AnnotatedString {
    val mono = NodType.code.fontFamily
    val foreground = NodTheme.colors.foreground
    return remember(text, mono, foreground) {
        buildAnnotatedString {
            text.split('`').forEachIndexed { index, part ->
                if (index % 2 == 0) append(part) else withStyle(SpanStyle(fontFamily = mono, color = foreground)) { append(part) }
            }
        }
    }
}

/**
 * Full-screen checklist: the skippable step right after pairing
 * ([onboarding] = true) and the target of the inbox banner.
 */
@Composable
fun DeliveryScreen(onboarding: Boolean, onDone: () -> Unit) {
    val colors = NodTheme.colors
    val statuses = rememberDeliveryStatuses()
    val fix = rememberDeliveryFix()
    val allCriticalOk = statuses.criticalProblems().isEmpty()
    BackHandler(onBack = onDone)
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (onboarding) {
            Spacer(Modifier.height(32.dp))
        } else {
            IconButton(onClick = onDone, modifier = Modifier.padding(start = 0.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.foreground)
            }
            Spacer(Modifier.height(8.dp))
        }
        Eyebrow(stringResource(if (onboarding) R.string.delivery_onboarding_eyebrow else R.string.settings_section_delivery))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.delivery_title), style = NodType.h2, color = colors.foreground)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.delivery_body), style = NodType.body, color = colors.mutedForeground)
        Spacer(Modifier.height(20.dp))
        DeliveryChecklist(
            statuses,
            onFix = fix,
            modifier = Modifier.surfaceSm().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        NodButton(
            stringResource(if (!onboarding || allCriticalOk) R.string.action_done else R.string.action_skip_for_now),
            onClick = onDone,
            style = if (allCriticalOk) NodButtonStyle.Primary else NodButtonStyle.Secondary,
            height = 40.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Inbox banner for critical problems; [onFix] opens the checklist. */
@Composable
fun DeliveryBanner(onFix: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NodTheme.colors
    Row(
        modifier.fillMaxWidth().surfaceSm().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(stringResource(R.string.delivery_banner_title), style = NodType.small, color = colors.foreground)
            Text(stringResource(R.string.delivery_banner_body), style = NodType.xs, color = colors.mutedForeground)
        }
        NodButton(stringResource(R.string.delivery_fix), onClick = onFix, style = NodButtonStyle.Text, height = 36.dp)
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_dismiss), tint = colors.mutedForeground)
        }
    }
}
