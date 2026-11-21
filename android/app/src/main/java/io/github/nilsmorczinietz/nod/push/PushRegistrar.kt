package io.github.nilsmorczinietz.nod.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import io.github.nilsmorczinietz.nod.data.api.ApiException
import io.github.nilsmorczinietz.nod.data.api.NodApi
import io.github.nilsmorczinietz.nod.data.model.FirebaseClientConfig
import io.github.nilsmorczinietz.nod.data.store.FirebaseConfigStore
import io.github.nilsmorczinietz.nod.data.store.Session
import io.github.nilsmorczinietz.nod.data.store.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** Whether requests can reach this phone while Nod is closed. Shown in Settings › Delivery. */
sealed interface PushState {
    /** The relay has no Firebase project; Nod only gets requests while it is open. */
    data object NotConfigured : PushState

    data object Registering : PushState

    /** The relay accepted this device's FCM token. */
    data object Connected : PushState

    data class Failed(val reason: String) : PushState
}

/**
 * Initialises Firebase at runtime from the config the relay handed out during
 * pairing, so one generic APK works with anyone's Firebase project and no
 * google-services.json is needed at build time.
 */
class PushRegistrar(
    private val context: Context,
    private val configStore: FirebaseConfigStore,
    private val sessionStore: SessionStore,
    private val api: NodApi,
) {
    /** True once Firebase is up; push is simply disabled otherwise. */
    var isAvailable: Boolean = false
        private set

    private val _state = MutableStateFlow<PushState>(PushState.NotConfigured)
    val state: StateFlow<PushState> = _state.asStateFlow()

    private val registration = Mutex()

    /** Synchronous; call from Application.onCreate before FCM can deliver anything. */
    fun initFromStore() {
        val config = configStore.load()
        if (config == null) {
            _state.value = PushState.NotConfigured
            return
        }
        initialize(config)
        _state.value = when {
            !isAvailable -> PushState.Failed(FIREBASE_DID_NOT_START)
            configStore.isUploaded(configStore.loadToken()) -> PushState.Connected
            else -> PushState.Registering
        }
    }

    /**
     * Persists the relay's Firebase config and starts Firebase. Synchronous on
     * purpose: it must finish before the new session is saved, because saving
     * it switches the UI to "paired" and cancels the pairing screen's scope.
     * Call [registerToken] afterwards from a scope that outlives the screen.
     */
    fun configure(config: FirebaseClientConfig?) {
        configStore.save(config)
        configStore.saveUploaded(null, null)
        if (config == null) {
            Log.i(TAG, "Relay has no Firebase config; push stays off")
            _state.value = PushState.NotConfigured
            return
        }
        initialize(config)
        _state.value = if (isAvailable) PushState.Registering else PushState.Failed(FIREBASE_DID_NOT_START)
    }

    /**
     * Makes sure the relay knows our FCM token: re-uploads the last known one
     * and asks FCM to register, which reports (new) tokens via [onToken].
     * Every outcome ends in [state], so a failure is never silent. Safe to call
     * again (app start, "Retry" in Settings).
     */
    suspend fun registerToken(session: Session? = null) = registration.withLock {
        if (configStore.load() == null) {
            _state.value = PushState.NotConfigured
            return@withLock
        }
        if (!isAvailable) {
            _state.value = PushState.Failed(FIREBASE_DID_NOT_START)
            return@withLock
        }
        val current = session ?: sessionStore.current() ?: return@withLock
        if (_state.value !is PushState.Connected) _state.value = PushState.Registering

        configStore.loadToken()?.let { upload(current, it) }

        val registered = runCatching {
            withTimeout(REGISTER_TIMEOUT) { FirebaseMessaging.getInstance().register().await() }
        }
        registered.exceptionOrNull()?.let { error ->
            Log.w(TAG, "FCM registration failed", error)
            if (_state.value !is PushState.Connected) _state.value = PushState.Failed("Firebase: ${error.reason()}")
            return@withLock
        }
        if (_state.value is PushState.Connected) return@withLock

        // A new token arrives through NodMessagingService.onRegistered -> onToken.
        val settled = withTimeoutOrNull(TOKEN_TIMEOUT) { state.first { it is PushState.Connected || it is PushState.Failed } }
        if (settled == null) {
            Log.w(TAG, "FCM registered, but no token arrived")
            _state.value = PushState.Failed("Firebase sent no token")
        }
    }

    /** Called by [NodMessagingService.onRegistered] whenever FCM issues a token. */
    suspend fun onToken(token: String) {
        Log.i(TAG, "FCM token received")
        configStore.saveToken(token)
        sessionStore.current()?.let { upload(it, token) }
    }

    suspend fun reset() {
        if (isAvailable) runCatching { FirebaseMessaging.getInstance().unregister().await() }
        configStore.save(null)
        configStore.saveToken(null)
        configStore.saveUploaded(null, null)
        _state.value = PushState.NotConfigured
    }

    private suspend fun upload(session: Session, token: String) {
        runCatching { api.putPushToken(session.auth, token) }
            .onSuccess {
                Log.i(TAG, "Push token registered with the relay")
                configStore.saveUploaded(session.deviceId, token)
                _state.value = PushState.Connected
            }
            .onFailure {
                Log.w(TAG, "Push token upload failed", it)
                _state.value = PushState.Failed("Relay: ${it.reason()}")
            }
    }

    private fun initialize(config: FirebaseClientConfig) {
        val options = FirebaseOptions.Builder()
            .setProjectId(config.projectId)
            .setApplicationId(config.applicationId)
            .setApiKey(config.apiKey)
            .setGcmSenderId(config.senderId)
            .build()
        isAvailable = runCatching {
            val existing = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            if (existing != null && existing.options != options) existing.delete()
            if (existing == null || existing.options != options) FirebaseApp.initializeApp(context, options)
            true
        }.onFailure { Log.e(TAG, "Firebase init failed", it) }.getOrDefault(false)
    }

    private fun Throwable.reason(): String = when (this) {
        is ApiException -> "HTTP $status ($code)"
        else -> (cause?.message ?: message)?.takeIf { it.isNotBlank() } ?: "unknown error"
    }

    private companion object {
        const val TAG = "NodPush"
        const val FIREBASE_DID_NOT_START = "Firebase didn't start"
        val REGISTER_TIMEOUT = 30.seconds
        val TOKEN_TIMEOUT = 30.seconds
    }
}
