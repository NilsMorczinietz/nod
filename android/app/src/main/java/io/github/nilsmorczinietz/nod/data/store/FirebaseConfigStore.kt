package io.github.nilsmorczinietz.nod.data.store

import android.content.Context
import androidx.core.content.edit
import io.github.nilsmorczinietz.nod.data.model.FirebaseClientConfig
import io.github.nilsmorczinietz.nod.data.model.NodJson

/**
 * Stores the (public, non-secret) Firebase client config received at pairing
 * and the last FCM registration token.
 * Uses SharedPreferences on purpose: Firebase must be initialised synchronously
 * in Application.onCreate, before FCM delivers any message to the process.
 */
class FirebaseConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("firebase", Context.MODE_PRIVATE)

    fun load(): FirebaseClientConfig? = prefs.getString(KEY, null)?.let {
        runCatching { NodJson.decodeFromString(FirebaseClientConfig.serializer(), it) }.getOrNull()
    }

    fun save(config: FirebaseClientConfig?) = prefs.edit {
        if (config == null) remove(KEY) else putString(KEY, NodJson.encodeToString(FirebaseClientConfig.serializer(), config))
    }

    fun loadToken(): String? = prefs.getString(TOKEN_KEY, null)

    fun saveToken(token: String?) = prefs.edit {
        if (token == null) remove(TOKEN_KEY) else putString(TOKEN_KEY, token)
    }

    /** Remembers which token the relay accepted for which device, so the app knows push really works. */
    fun saveUploaded(deviceId: String?, token: String?) = prefs.edit {
        if (deviceId == null || token == null) remove(UPLOADED_KEY) else putString(UPLOADED_KEY, "$deviceId:$token")
    }

    fun isUploaded(token: String?): Boolean =
        token != null && prefs.getString(UPLOADED_KEY, null)?.substringAfter(':') == token

    private companion object {
        const val KEY = "client_config"
        const val TOKEN_KEY = "fcm_token"
        const val UPLOADED_KEY = "fcm_token_uploaded"
    }
}
