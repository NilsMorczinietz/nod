package io.github.nilsmorczinietz.nod.data.delivery

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.github.nilsmorczinietz.nod.notifications.Notifier
import io.github.nilsmorczinietz.nod.push.PushRegistrar
import io.github.nilsmorczinietz.nod.push.PushState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What Nod needs from the system to reach the user on the lock screen and in the background. */
enum class DeliveryCheck(val critical: Boolean) {
    Notifications(critical = true),
    RequestsChannel(critical = true),
    LockScreen(critical = true),
    Battery(critical = false),
    UnusedAppPause(critical = false),
    Push(critical = false),
}

enum class CheckState {
    Ok,
    ActionNeeded,

    /** Being set up right now (push registration). */
    Pending,

    /** The app cannot read this setting; the row only explains and links to it. */
    Unknown,
}

/** [detail] carries a failure reason, e.g. why push registration failed. */
data class DeliveryStatus(val check: DeliveryCheck, val state: CheckState, val detail: String? = null)

/**
 * Reads the live state of every [DeliveryCheck] and builds the intents that
 * open the matching system screen. Cheap and synchronous: call it on every
 * ON_RESUME, since the user fixes things outside the app.
 */
class DeliveryChecker(
    context: Context,
    private val push: PushRegistrar,
    private val scope: CoroutineScope,
) {
    private val context = context.applicationContext
    private val notifications = NotificationManagerCompat.from(this.context)

    /** Push registration progress; statuses must be re-read when it changes. */
    val pushState: StateFlow<PushState> get() = push.state

    /** "Retry" for a failed push registration; the outcome shows up in [pushState]. */
    fun retryPush() {
        scope.launch { push.registerToken() }
    }

    fun current(): List<DeliveryStatus> = DeliveryCheck.entries.mapNotNull { check ->
        if (check == DeliveryCheck.Push) return@mapNotNull pushStatus()
        val state = when (check) {
            DeliveryCheck.Notifications -> notifications.areNotificationsEnabled().toState()
            DeliveryCheck.RequestsChannel -> requestsChannelOk().toState()
            DeliveryCheck.LockScreen -> lockScreenNotifications()
            DeliveryCheck.Battery -> ignoresBatteryOptimizations().toState()
            // The "pause app activity" switch only exists from Android 11.
            DeliveryCheck.UnusedAppPause -> if (Build.VERSION.SDK_INT >= 30) {
                context.packageManager.isAutoRevokeWhitelisted.toState()
            } else {
                return@mapNotNull null
            }
            DeliveryCheck.Push -> error("handled above")
        }
        DeliveryStatus(check, state)
    }

    private fun pushStatus(): DeliveryStatus = when (val state = push.state.value) {
        PushState.Connected -> DeliveryStatus(DeliveryCheck.Push, CheckState.Ok)
        PushState.Registering -> DeliveryStatus(DeliveryCheck.Push, CheckState.Pending)
        PushState.NotConfigured -> DeliveryStatus(DeliveryCheck.Push, CheckState.ActionNeeded)
        is PushState.Failed -> DeliveryStatus(DeliveryCheck.Push, CheckState.ActionNeeded, detail = state.reason)
    }

    /** Whether the runtime permission dialog can still be shown instead of the settings screen. */
    fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /**
     * Intents to try in order; the first one the system can open wins. Most
     * checks have a precise screen plus a broader fallback, because OEM
     * settings apps do not implement every action.
     */
    fun fixIntents(check: DeliveryCheck): List<Intent> {
        val pkg = context.packageName
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        val appNotifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        return when (check) {
            DeliveryCheck.Notifications -> listOf(appNotifications, appDetails)
            DeliveryCheck.RequestsChannel -> listOf(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL_REQUESTS),
                appNotifications,
            )
            DeliveryCheck.LockScreen -> listOf(
                Intent(ACTION_LOCK_SCREEN_SETTINGS),
                Intent(ACTION_NOTIFICATION_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            DeliveryCheck.Battery -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", pkg, null)),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                appDetails,
            )
            DeliveryCheck.UnusedAppPause -> listOfNotNull(
                runCatching { IntentCompat.createManageUnusedAppRestrictionsIntent(context, pkg) }.getOrNull(),
                appDetails,
            )
            DeliveryCheck.Push -> emptyList()
        }.map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    private fun requestsChannelOk(): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        val channel = notifications.getNotificationChannel(Notifier.CHANNEL_REQUESTS) ?: return true
        return channel.importance >= NotificationManager.IMPORTANCE_HIGH &&
            channel.lockscreenVisibility != Notification.VISIBILITY_SECRET
    }

    /**
     * `lock_screen_show_notifications` is a hidden secure setting: readable on
     * many devices, but newer releases may refuse; then we only explain.
     */
    private fun lockScreenNotifications(): CheckState = runCatching {
        // Unset means the system default, which is "show".
        Settings.Secure.getInt(context.contentResolver, LOCK_SCREEN_SHOW_NOTIFICATIONS, 1)
    }.fold(
        onSuccess = { if (it == 0) CheckState.ActionNeeded else CheckState.Ok },
        onFailure = { CheckState.Unknown },
    )

    private fun ignoresBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun Boolean.toState() = if (this) CheckState.Ok else CheckState.ActionNeeded

    private companion object {
        const val LOCK_SCREEN_SHOW_NOTIFICATIONS = "lock_screen_show_notifications"

        // Not in the public SDK, but implemented by AOSP / Pixel Settings.
        const val ACTION_LOCK_SCREEN_SETTINGS = "android.settings.LOCK_SCREEN_SETTINGS"
        const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
    }
}
