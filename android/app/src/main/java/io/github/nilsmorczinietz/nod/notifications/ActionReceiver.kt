package io.github.nilsmorczinietz.nod.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import io.github.nilsmorczinietz.nod.NodApp
import io.github.nilsmorczinietz.nod.R
import io.github.nilsmorczinietz.nod.data.api.ResolveResult
import io.github.nilsmorczinietz.nod.data.model.Decision
import io.github.nilsmorczinietz.nod.data.model.ResolveInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Intent contract between [Notifier] and [ActionReceiver]. */
object ResolveIntent {
    const val ACTION = "io.github.nilsmorczinietz.nod.action.RESOLVE"
    const val EXTRA_ITEM_ID = "item_id"
    const val EXTRA_DECISION = "decision"
    const val EXTRA_ANSWER_INDEX = "answer_index"

    /** RemoteInput key of the optional note ("Deny with note", "Reject with note"). */
    const val KEY_NOTE = "note"
    const val ALLOW = "allow"
    const val DENY = "deny"
    const val ANSWER = "answer"
}

/**
 * Handles Allow / Deny / option buttons tapped directly in a notification,
 * without opening the app. Uses goAsync() so the network call may take a few
 * seconds while the process stays alive.
 */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ResolveIntent.ACTION) return
        val itemId = intent.getStringExtra(ResolveIntent.EXTRA_ITEM_ID) ?: return
        val decision = intent.getStringExtra(ResolveIntent.EXTRA_DECISION) ?: return
        val answerIndex = intent.getIntExtra(ResolveIntent.EXTRA_ANSWER_INDEX, -1)
        val note = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(ResolveIntent.KEY_NOTE)
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val container = (context.applicationContext as NodApp).container
        val pending = goAsync()

        container.appScope.launch {
            try {
                withTimeout(TIMEOUT_MS) {
                    val input = when (decision) {
                        ResolveIntent.ALLOW -> ResolveInput(Decision.Allow)
                        ResolveIntent.DENY -> ResolveInput(Decision.Deny, message = note)
                        else -> {
                            // Fetch the item: answers are keyed by the exact question text.
                            val question = container.repository.loadItem(itemId)?.questions?.singleOrNull()
                            val label = question?.options?.getOrNull(answerIndex)?.label
                                ?: error("Question not available")
                            ResolveInput(Decision.Answer, answers = mapOf(question.question to listOf(label)))
                        }
                    }
                    when (val result = container.repository.resolve(itemId, input)) {
                        is ResolveResult.Resolved -> container.notifier.cancel(itemId)
                        is ResolveResult.AlreadyFinished -> container.notifier.showStatus(
                            itemId,
                            context.getString(R.string.status_already_answered),
                            result.item.resolution?.deviceName?.let { context.getString(R.string.status_answered_on, it) }
                                ?: context.getString(R.string.status_no_longer_pending),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Resolving $itemId from notification failed", e)
                container.notifier.showStatus(
                    itemId,
                    context.getString(R.string.status_send_failed),
                    context.getString(R.string.status_send_failed_hint),
                )
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "NodAction"
        const val TIMEOUT_MS = 8_000L
    }
}
