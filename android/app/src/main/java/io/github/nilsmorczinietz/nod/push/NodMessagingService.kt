package io.github.nilsmorczinietz.nod.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.nilsmorczinietz.nod.NodApp
import kotlinx.coroutines.launch

/** Receives FCM data messages from the relay and turns them into notifications. */
class NodMessagingService : FirebaseMessagingService() {

    private val container get() = (application as NodApp).container

    override fun onMessageReceived(message: RemoteMessage) {
        container.pushHandler.handle(message.data)
    }

    override fun onRegistered(token: String) {
        container.appScope.launch { container.push.onToken(token) }
    }
}
