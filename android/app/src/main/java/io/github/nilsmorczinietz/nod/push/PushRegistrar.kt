package io.github.nilsmorczinietz.nod.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import io.github.nilsmorczinietz.nod.data.api.NodApi
import io.github.nilsmorczinietz.nod.data.model.FirebaseClientConfig
import io.github.nilsmorczinietz.nod.data.store.FirebaseConfigStore
import io.github.nilsmorczinietz.nod.data.store.Session
import io.github.nilsmorczinietz.nod.data.store.SessionStore
import kotlinx.coroutines.tasks.await

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

    /** Synchronous; call from Application.onCreate before FCM can deliver anything. */
    fun initFromStore() {
        configStore.load()?.let(::initialize)
    }

    /** Called after pairing: persist the config, start Firebase and upload the token. */
    suspend fun configure(config: FirebaseClientConfig?, session: Session) {
        configStore.save(config)
        if (config == null) return
        initialize(config)
        registerToken(session)
    }

    /**
     * Makes sure the relay knows our FCM token: re-uploads the last known one
     * and asks FCM to register, which reports new tokens via [onToken].
     */
    suspend fun registerToken(session: Session? = null) {
        if (!isAvailable) return
        val current = session ?: sessionStore.current() ?: return
        configStore.loadToken()?.let { upload(current, it) }
        runCatching { FirebaseMessaging.getInstance().register().await() }
            .onFailure { Log.w(TAG, "FCM registration failed", it) }
    }

    /** Called by [NodMessagingService.onRegistered] whenever FCM issues a token. */
    suspend fun onToken(token: String) {
        configStore.saveToken(token)
        sessionStore.current()?.let { upload(it, token) }
    }

    suspend fun reset() {
        if (isAvailable) runCatching { FirebaseMessaging.getInstance().unregister().await() }
        configStore.save(null)
        configStore.saveToken(null)
    }

    private suspend fun upload(session: Session, token: String) {
        runCatching { api.putPushToken(session.auth, token) }
            .onFailure { Log.w(TAG, "Push token upload failed", it) }
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

    private companion object {
        const val TAG = "NodPush"
    }
}
