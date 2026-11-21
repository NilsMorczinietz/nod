package io.github.nilsmorczinietz.nod

import android.content.Context
import io.github.nilsmorczinietz.nod.data.NodRepository
import io.github.nilsmorczinietz.nod.data.api.NodApi
import io.github.nilsmorczinietz.nod.data.api.StreamClient
import io.github.nilsmorczinietz.nod.data.delivery.DeliveryChecker
import io.github.nilsmorczinietz.nod.data.update.UpdateChecker
import io.github.nilsmorczinietz.nod.data.model.NodJson
import io.github.nilsmorczinietz.nod.data.store.AppearancePrefs
import io.github.nilsmorczinietz.nod.data.store.DeliveryPrefs
import io.github.nilsmorczinietz.nod.data.store.FirebaseConfigStore
import io.github.nilsmorczinietz.nod.data.store.SessionStore
import io.github.nilsmorczinietz.nod.data.store.TokenCipher
import io.github.nilsmorczinietz.nod.notifications.Notifier
import io.github.nilsmorczinietz.nod.push.PushHandler
import io.github.nilsmorczinietz.nod.push.PushRegistrar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Manual dependency graph; one instance per process, owned by [NodApp]. */
class AppContainer(context: Context) {
    /** Lives as long as the process; used for work that must outlive a screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api = NodApi(http, NodJson)
    val sessionStore = SessionStore(context, TokenCipher())
    val appearance = AppearancePrefs(context)
    val deliveryPrefs = DeliveryPrefs(context)
    val notifier = Notifier(context)
    val pushHandler = PushHandler(notifier, sessionStore)
    val push = PushRegistrar(context, FirebaseConfigStore(context), sessionStore, api)
    val delivery = DeliveryChecker(context, push, appScope)
    val updates = UpdateChecker(context, http, NodJson, BuildConfig.VERSION_NAME, appScope)
    val repository = NodRepository(
        scope = appScope,
        api = api,
        stream = StreamClient(http, NodJson),
        sessionStore = sessionStore,
        push = push,
        notifier = notifier,
    )
}
