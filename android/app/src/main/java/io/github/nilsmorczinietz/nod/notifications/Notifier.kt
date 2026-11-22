package io.github.nilsmorczinietz.nod.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import io.github.nilsmorczinietz.nod.MainActivity
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.data.model.ItemKind
import io.github.nilsmorczinietz.nod.data.model.NodJson
import io.github.nilsmorczinietz.nod.data.model.agentOrDefault
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Parsed FCM `type=item` data payload (see PushPayload in the protocol).
 * `canAlwaysAllow` is ignored on purpose: Android allows three actions and
 * those are Allow / Deny / Deny with note; "Always" lives in the app.
 */
data class PushItem(
    val id: String,
    val kind: ItemKind,
    val title: String,
    val body: String,
    val project: String,
    /** Display name of the asking agent, already defaulted to "Claude Code". */
    val agent: String,
    val expiresAt: Long,
    val options: List<String>,
    /** "1" in the payload: the question also takes a free-text reply. */
    val allowText: Boolean = false,
) {
    /** Claude ended its turn with a question in prose: options plus a free-text reply. */
    val isReply: Boolean get() = kind == ItemKind.Question && allowText

    companion object {
        fun from(data: Map<String, String>): PushItem? {
            val id = data["id"] ?: return null
            val kind = ItemKind.entries.firstOrNull { it.name.equals(data["kind"], ignoreCase = true) } ?: return null
            val options = data["options"]?.let {
                runCatching { NodJson.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
            }.orEmpty()
            return PushItem(
                id = id,
                kind = kind,
                title = data["title"].orEmpty(),
                body = data["body"].orEmpty(),
                project = data["project"].orEmpty(),
                agent = agentOrDefault(data["agent"]),
                expiresAt = data["expiresAt"]?.toLongOrNull() ?: 0L,
                options = options.take(3),
                allowText = data["allowText"] == "1",
            )
        }
    }
}

/**
 * Builds and posts all notifications. Each item uses its id as notification
 * tag, so updates and dismissals address the right one.
 */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        // Channel settings are frozen once created, so lock-screen visibility needs a new id.
        LEGACY_CHANNELS.forEach(system::deleteNotificationChannel)
        system.createNotificationChannels(
            listOf(
                // Heads-up and fully visible on the lock screen: answering there is the core of the app.
                NotificationChannel(CHANNEL_REQUESTS, context.getString(R.string.channel_requests), NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = context.getString(R.string.channel_requests_description)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    },
                NotificationChannel(CHANNEL_COMPLETED, context.getString(R.string.channel_completed), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_completed_description) },
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_alerts_description) },
            ),
        )
    }

    fun canPost(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /**
     * Posts [item]. With [showDetails] the full notification (question, command,
     * actions) is public on the lock screen; otherwise the lock screen gets a
     * public version that keeps the actions but hides the command.
     */
    fun show(item: PushItem, requireUnlock: Boolean, showDetails: Boolean) {
        if (!canPost()) return
        // A request that already expired can't be answered any more; don't show it.
        if (item.kind.isInteractive && item.expiresAt in 1..System.currentTimeMillis()) return
        val actions = actionsFor(item, requireUnlock)
        val builder = base(item, channelFor(item.kind), showDetails, actions)
            .setCategory(categoryFor(item.kind))
        actions.forEach(builder::addAction)
        val remaining = item.expiresAt - System.currentTimeMillis()
        if (item.kind.isInteractive && remaining > 0) {
            // Native countdown to the expiry, then the notification removes itself.
            builder
                .setWhen(item.expiresAt)
                // The chronometer replaces the relative time ("in 1m"), which would be redundant.
                .setShowWhen(false)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter(remaining)
                .setAutoCancel(false)
        }
        post(item.id, builder)
    }

    private fun actionsFor(item: PushItem, requireUnlock: Boolean): List<NotificationCompat.Action> = when (item.kind) {
        // Android shows at most three actions; "Approve and remember" lives in the app.
        ItemKind.Permission -> listOf(
            resolveAction(item.id, ResolveIntent.ALLOW, context.getString(R.string.action_approve), requireUnlock),
            resolveAction(item.id, ResolveIntent.DENY, context.getString(R.string.action_deny), requireUnlock = false),
            noteAction(item.id, ResolveIntent.DENY, context.getString(R.string.action_deny_with_note), context.getString(R.string.note_hint_deny)),
        )
        // Reply questions: two options plus "Reply" (free text) fill Android's three actions,
        // so there is no Deny; swiping the notification away leaves the question open.
        ItemKind.Question -> item.options.take(if (item.allowText) 2 else 3).mapIndexed { index, label ->
            resolveAction(item.id, ResolveIntent.ANSWER, label, requireUnlock, answerIndex = index)
        } + listOfNotNull(
            if (item.allowText) {
                noteAction(item.id, ResolveIntent.ANSWER, context.getString(R.string.action_reply), context.getString(R.string.note_hint_reply))
            } else {
                null
            },
        )
        ItemKind.Plan -> listOf(
            resolveAction(item.id, ResolveIntent.ALLOW, context.getString(R.string.action_approve), requireUnlock),
            noteAction(item.id, ResolveIntent.DENY, context.getString(R.string.action_reject_with_note), context.getString(R.string.note_hint_plan)),
        )
        else -> emptyList()
    }

    private fun channelFor(kind: ItemKind) = when (kind) {
        ItemKind.Permission, ItemKind.Question, ItemKind.Plan -> CHANNEL_REQUESTS
        ItemKind.Completed -> CHANNEL_COMPLETED
        ItemKind.Failed, ItemKind.Attention -> CHANNEL_ALERTS
    }

    private fun categoryFor(kind: ItemKind) = when (kind) {
        ItemKind.Completed -> NotificationCompat.CATEGORY_STATUS
        ItemKind.Failed -> NotificationCompat.CATEGORY_ERROR
        else -> NotificationCompat.CATEGORY_REMINDER
    }


    /** Replaces an item's notification with a short status line (e.g. after a failed action). */
    fun showStatus(id: String, title: String, text: String) {
        if (!canPost()) return
        post(
            id,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_nod)
                .setColor(ContextCompat.getColor(context, R.color.nod_primary))
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openIntent(id))
                .setAutoCancel(true)
                .setSilent(true),
        )
    }

    fun cancel(id: String) = manager.cancel(id, NOTIFICATION_ID)

    fun cancelAll() = manager.cancelAll()

    // ---------------------------------------------------------------------

    private fun base(item: PushItem, channel: String, showDetails: Boolean, actions: List<NotificationCompat.Action>): NotificationCompat.Builder {
        val color = ContextCompat.getColor(context, if (item.kind == ItemKind.Failed) R.color.nod_danger else R.color.nod_primary)
        // "<Agent> needs you" / "Run the test suite?": short and plain; the
        // command follows on the expanded line (ref: lock-screen notification).
        val text = when (item.kind) {
            ItemKind.Permission, ItemKind.Plan -> asQuestion(item.title.ifBlank { item.body })
            else -> item.body.ifBlank { item.title }
        }
        val expanded = if (item.kind == ItemKind.Permission && item.body.isNotBlank() && item.body != text) "$text\n${item.body}" else text
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_nod)
            .setColor(color)
            .setContentTitle(context.getString(if (item.isReply) R.string.notif_waiting else titleFor(item.kind), item.agent))
            .setContentText(text)
            .setSubText(item.project)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setContentIntent(openIntent(item.id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(if (item.kind.isInteractive) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(if (showDetails) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .apply {
                // Details hidden: the lock screen shows "<what> · <project>" plus the same buttons, never the command.
                if (!showDetails) {
                    setPublicVersion(
                        NotificationCompat.Builder(context, channel)
                            .setSmallIcon(R.drawable.ic_stat_nod)
                            .setColor(color)
                            .setContentTitle(context.getString(publicTitleFor(item.kind), item.agent, item.project))
                            .setOnlyAlertOnce(true)
                            .apply { actions.forEach(::addAction) }
                            .build(),
                    )
                }
            }
    }

    private fun post(id: String, builder: NotificationCompat.Builder) {
        try {
            manager.notify(id, NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            Log.w("NodNotifier", "Cannot post notification", e)
        }
    }

    private fun openIntent(itemId: String): PendingIntent = PendingIntent.getActivity(
        context,
        itemId.hashCode(),
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_ITEM_ID, itemId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun resolveAction(
        itemId: String,
        decision: String,
        label: String,
        requireUnlock: Boolean,
        answerIndex: Int = -1,
    ): NotificationCompat.Action {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(ResolveIntent.ACTION)
            .putExtra(ResolveIntent.EXTRA_ITEM_ID, itemId)
            .putExtra(ResolveIntent.EXTRA_DECISION, decision)
            .putExtra(ResolveIntent.EXTRA_ANSWER_INDEX, answerIndex)
        val requestCode = 31 * itemId.hashCode() + decision.hashCode() + answerIndex
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(0, label, pending)
            .setAuthenticationRequired(requireUnlock)
            .setShowsUserInterface(false)
            .build()
    }

    /**
     * Action with an inline text field (RemoteInput); the typed note travels to
     * Claude as the deny reason / plan feedback. Needs a mutable PendingIntent.
     */
    private fun noteAction(itemId: String, decision: String, label: String, hint: String): NotificationCompat.Action {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(ResolveIntent.ACTION)
            .putExtra(ResolveIntent.EXTRA_ITEM_ID, itemId)
            .putExtra(ResolveIntent.EXTRA_DECISION, decision)
        val pending = PendingIntent.getBroadcast(
            context,
            31 * itemId.hashCode() + NOTE_REQUEST_OFFSET,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val input = RemoteInput.Builder(ResolveIntent.KEY_NOTE).setLabel(hint).build()
        return NotificationCompat.Action.Builder(0, label, pending)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .setShowsUserInterface(false)
            .build()
    }

    companion object {
        private const val NOTE_REQUEST_OFFSET = 7
        const val CHANNEL_REQUESTS = "requests_v2"
        private val LEGACY_CHANNELS = listOf("requests")
        const val CHANNEL_COMPLETED = "completed"
        const val CHANNEL_ALERTS = "alerts"
        private const val NOTIFICATION_ID = 1

        private fun asQuestion(text: String): String = text.trimEnd('.', ' ').let { if (it.endsWith("?")) it else "$it?" }

        private fun titleFor(kind: ItemKind): Int = when (kind) {
            ItemKind.Permission -> R.string.notif_permission
            ItemKind.Question -> R.string.notif_question
            ItemKind.Plan -> R.string.notif_plan
            ItemKind.Completed -> R.string.notif_completed
            ItemKind.Failed -> R.string.notif_failed
            ItemKind.Attention -> R.string.notif_attention
        }

        private fun publicTitleFor(kind: ItemKind): Int = when (kind) {
            ItemKind.Permission -> R.string.public_permission
            ItemKind.Question -> R.string.public_question
            ItemKind.Plan -> R.string.public_plan
            ItemKind.Completed -> R.string.public_completed
            ItemKind.Failed -> R.string.public_failed
            ItemKind.Attention -> R.string.public_attention
        }
    }
}
