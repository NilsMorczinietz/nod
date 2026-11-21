package io.github.nilsmorczinietz.nod.push

import io.github.nilsmorczinietz.nod.NodApp
import io.github.nilsmorczinietz.nod.data.store.SessionStore
import io.github.nilsmorczinietz.nod.notifications.Notifier
import io.github.nilsmorczinietz.nod.notifications.PushItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Turns a relay push payload (FCM data map, see PushPayload) into notifications. */
class PushHandler(
    private val notifier: Notifier,
    private val sessionStore: SessionStore,
) {
    /** Must be called off the main thread (FCM delivers on a worker thread). */
    fun handle(data: Map<String, String>) {
        when (data["type"]) {
            "dismiss" -> data["id"]?.let(notifier::cancel)
            "item" -> {
                val item = PushItem.from(data) ?: return
                // While the app is visible the live stream already shows it, with animation.
                if (NodApp.isInForeground) return
                val (requireUnlock, showDetails) = runBlocking {
                    sessionStore.requireUnlock.first() to sessionStore.showOnLockScreen.first()
                }
                notifier.show(item, requireUnlock, showDetails)
            }
        }
    }
}
