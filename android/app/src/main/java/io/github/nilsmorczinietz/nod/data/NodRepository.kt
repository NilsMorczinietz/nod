package io.github.nilsmorczinietz.nod.data

import android.os.Build
import io.github.nilsmorczinietz.nod.data.api.ApiException
import io.github.nilsmorczinietz.nod.data.api.NodApi
import io.github.nilsmorczinietz.nod.data.api.ResolveResult
import io.github.nilsmorczinietz.nod.data.api.StreamClient
import io.github.nilsmorczinietz.nod.data.api.StreamEvent
import io.github.nilsmorczinietz.nod.data.model.HubSettings
import io.github.nilsmorczinietz.nod.data.model.Item
import io.github.nilsmorczinietz.nod.data.model.ItemStatus
import io.github.nilsmorczinietz.nod.data.model.RegisterDeviceInput
import io.github.nilsmorczinietz.nod.data.model.ResolveInput
import io.github.nilsmorczinietz.nod.data.model.RoutingMode
import io.github.nilsmorczinietz.nod.data.model.SettingsPatch
import io.github.nilsmorczinietz.nod.data.model.StreamMessage
import io.github.nilsmorczinietz.nod.data.store.Session
import io.github.nilsmorczinietz.nod.data.store.SessionStore
import io.github.nilsmorczinietz.nod.notifications.Notifier
import io.github.nilsmorczinietz.nod.push.PushRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Whether the live WebSocket is up. */
enum class ConnectionState { Offline, Connecting, Live }

/** Pairing as seen by the UI; [Loading] avoids flashing the pairing screen on start. */
sealed interface PairingState {
    data object Loading : PairingState
    data object Unpaired : PairingState
    data class Paired(val session: Session) : PairingState
}

/** One-shot events for the UI. */
sealed interface NodEvent {
    /** A request this device was showing got answered on another device. */
    data class AnsweredElsewhere(val item: Item) : NodEvent
}

/**
 * Single source of truth for items, settings and the pairing. Items arrive
 * via REST refreshes and the live stream; both merge into [items].
 */
class NodRepository(
    private val scope: CoroutineScope,
    private val api: NodApi,
    private val stream: StreamClient,
    private val sessionStore: SessionStore,
    private val push: PushRegistrar,
    private val notifier: Notifier,
) {
    val pairing: StateFlow<PairingState> = sessionStore.session
        .map { it?.let(PairingState::Paired) ?: PairingState.Unpaired }
        .stateIn(scope, SharingStarted.Eagerly, PairingState.Loading)

    private val session: Session? get() = (pairing.value as? PairingState.Paired)?.session

    private val _items = MutableStateFlow<Map<String, Item>>(emptyMap())
    val items: StateFlow<Map<String, Item>> = _items.asStateFlow()

    private val _settings = MutableStateFlow(HubSettings())
    val settings: StateFlow<HubSettings> = _settings.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.Offline)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<NodEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    // ------------------------------------------------------------------ pairing

    suspend fun pair(relayUrl: String, code: String) {
        val relay = relayUrl.trim().trimEnd('/')
        val name = "${Build.MANUFACTURER.replaceFirstChar(Char::titlecase)} ${Build.MODEL}".trim()
        val response = api.register(relay, RegisterDeviceInput(code = code.trim(), name = name))
        val newSession = Session(relay, response.device.id, response.device.name, response.token)
        sessionStore.save(newSession)
        push.configure(response.firebase, newSession)
    }

    suspend fun unpair() {
        session?.let { current -> runCatching { api.unpair(current.auth) } }
        forget()
    }

    /** Drops local pairing state (after unpairing or when the relay revoked us). */
    private suspend fun forget() {
        push.reset()
        sessionStore.clear()
        _items.value = emptyMap()
        notifier.cancelAll()
    }

    // ------------------------------------------------------------------ items

    suspend fun refresh() {
        val current = session ?: return
        guard {
            val me = api.me(current.auth)
            _settings.value = me.settings
            _items.value = api.items(current.auth).associateBy(Item::id)
        }
    }

    suspend fun loadItem(id: String): Item? {
        _items.value[id]?.let { return it }
        val current = session ?: return null
        return runCatching { api.item(current.auth, id) }.getOrNull()?.also(::upsert)
    }

    suspend fun resolve(id: String, input: ResolveInput): ResolveResult {
        val current = session ?: error("Not paired")
        val result = guard { api.resolve(current.auth, id, input) }
        upsert(result.item)
        notifier.cancel(id)
        return result
    }

    suspend fun setMode(mode: RoutingMode) = updateSettings(SettingsPatch(mode = mode))

    /** Resuming restores whatever mode was active before pausing. */
    suspend fun resume() = updateSettings(SettingsPatch(paused = false))

    private suspend fun updateSettings(patch: SettingsPatch) {
        val current = session ?: return
        _settings.value = guard { api.updateSettings(current.auth, patch) }
    }

    private fun upsert(item: Item) = _items.update { it + (item.id to item) }

    /** Runs an API call and forgets the pairing when the relay says our token is gone. */
    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: ApiException) {
        if (e.status == 401) forget()
        throw e
    }

    // ------------------------------------------------------------------ live

    /**
     * Keeps the WebSocket open for as long as the caller's coroutine lives.
     * Called from the process lifecycle, i.e. only while the app is visible.
     */
    suspend fun runLive() {
        pairing.map { (it as? PairingState.Paired)?.session }.distinctUntilChanged().collectLatest { current ->
            if (current == null) {
                _connection.value = ConnectionState.Offline
                return@collectLatest
            }
            _connection.value = ConnectionState.Connecting
            runCatching { refresh() }
            try {
                stream.events(current.auth).collect { event -> onStreamEvent(current, event) }
            } finally {
                _connection.value = ConnectionState.Offline
            }
        }
    }

    private suspend fun onStreamEvent(current: Session, event: StreamEvent) {
        when (event) {
            StreamEvent.Connected -> _connection.value = ConnectionState.Live
            StreamEvent.Disconnected -> _connection.value = ConnectionState.Connecting
            StreamEvent.Unauthorized -> forget()
            is StreamEvent.Message -> when (val message = event.message) {
                is StreamMessage.Hello -> {
                    _settings.value = message.settings
                    val pendingIds = message.pending.map(Item::id).toSet()
                    val stalePending = _items.value.values.any { it.status == ItemStatus.Pending && it.id !in pendingIds }
                    _items.update { map -> map + message.pending.associateBy(Item::id) }
                    // Something was answered while we were away: fetch its final state.
                    if (stalePending) runCatching { refresh() }
                }
                is StreamMessage.NewItem -> upsert(message.item)
                is StreamMessage.Update -> {
                    val previous = _items.value[message.item.id]
                    upsert(message.item)
                    val resolution = message.item.resolution
                    if (previous?.status == ItemStatus.Pending && resolution != null && resolution.deviceId != current.deviceId) {
                        _events.tryEmit(NodEvent.AnsweredElsewhere(message.item))
                    }
                    if (message.item.status != ItemStatus.Pending) notifier.cancel(message.item.id)
                }
                is StreamMessage.Settings -> _settings.value = message.settings
            }
        }
    }
}
