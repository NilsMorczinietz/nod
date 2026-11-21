package io.github.nilsmorczinietz.nod.data.api

import io.github.nilsmorczinietz.nod.data.model.StreamMessage
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Events emitted by [StreamClient.events]. */
sealed interface StreamEvent {
    data object Connected : StreamEvent
    data object Disconnected : StreamEvent

    /** The relay rejected our token (device revoked). The flow completes after this. */
    data object Unauthorized : StreamEvent
    data class Message(val message: StreamMessage) : StreamEvent
}

/**
 * Live connection to `/v1/stream`. Reconnects with exponential backoff and
 * sends an application-level "ping" every 30 s (answered by the relay without
 * waking the Durable Object). A missing "pong" forces a reconnect.
 */
class StreamClient(
    private val http: OkHttpClient,
    private val json: Json,
) {
    fun events(auth: Auth): Flow<StreamEvent> = channelFlow {
        var backoffMs = INITIAL_BACKOFF_MS
        while (isActive) {
            val closed = CompletableDeferred<Unit>()
            var lastPong = System.currentTimeMillis()
            var unauthorized = false
            val request = Request.Builder()
                .url(auth.relayUrl.trimEnd('/') + "/v1/stream")
                .header("Authorization", "Bearer ${auth.token}")
                .build()

            val socket = http.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    backoffMs = INITIAL_BACKOFF_MS
                    trySend(StreamEvent.Connected)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text == "pong") {
                        lastPong = System.currentTimeMillis()
                        return
                    }
                    runCatching { json.decodeFromString(StreamMessage.serializer(), text) }
                        .onSuccess { trySend(StreamEvent.Message(it)) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(NORMAL_CLOSURE, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    unauthorized = code == REVOKED_CLOSE_CODE
                    closed.complete(Unit)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    unauthorized = response?.code == 401 || response?.code == 403
                    closed.complete(Unit)
                }
            })

            val heartbeat = launch {
                while (isActive) {
                    delay(PING_INTERVAL_MS)
                    if (System.currentTimeMillis() - lastPong > 2 * PING_INTERVAL_MS + 5_000) {
                        socket.cancel() // half-open connection; triggers onFailure
                        break
                    }
                    socket.send("ping")
                }
            }

            try {
                closed.await()
            } finally {
                heartbeat.cancel()
                socket.close(NORMAL_CLOSURE, null)
            }
            if (unauthorized) {
                send(StreamEvent.Unauthorized)
                return@channelFlow
            }
            send(StreamEvent.Disconnected)
            delay(backoffMs)
            backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val REVOKED_CLOSE_CODE = 4001
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val PING_INTERVAL_MS = 30_000L
    }
}
