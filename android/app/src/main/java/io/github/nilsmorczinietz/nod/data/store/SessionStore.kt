package io.github.nilsmorczinietz.nod.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.nilsmorczinietz.nod.data.api.Auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A paired relay connection. */
data class Session(
    val relayUrl: String,
    val deviceId: String,
    val deviceName: String,
    val token: String,
) {
    val auth: Auth get() = Auth(relayUrl, token)
}

/** App theme choice; notifications always follow the system. */
enum class ThemeMode { System, Light, Dark }

/** Fresh installs start in light mode (the reference design); the choice is kept once made. */
val DEFAULT_THEME = ThemeMode.Light

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nod")

/** Persists the pairing (token encrypted via [TokenCipher]) and local preferences. */
class SessionStore(
    context: Context,
    private val cipher: TokenCipher,
) {
    private val store = context.applicationContext.dataStore

    val session: Flow<Session?> = store.data
        .map { prefs ->
            val relay = prefs[RELAY_URL] ?: return@map null
            val deviceId = prefs[DEVICE_ID] ?: return@map null
            val token = prefs[TOKEN]?.let(cipher::decrypt) ?: return@map null
            Session(relay, deviceId, prefs[DEVICE_NAME].orEmpty(), token)
        }
        .distinctUntilChanged()

    /**
     * Require device unlock before "Approve" works from a notification (Android 12+).
     * Default off: one-tap approval from the lock screen is the point of the app. Only an
     * explicit choice is stored, so installs that never touched it follow the default.
     */
    val requireUnlock: Flow<Boolean> = store.data.map { it[REQUIRE_UNLOCK] ?: false }.distinctUntilChanged()

    /** Show the question and command on the lock screen (otherwise only project + buttons). */
    val showOnLockScreen: Flow<Boolean> = store.data.map { it[LOCKSCREEN_DETAILS] ?: true }.distinctUntilChanged()

    val themeMode: Flow<ThemeMode> = store.data
        .map { prefs -> prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: DEFAULT_THEME }
        .distinctUntilChanged()

    suspend fun current(): Session? = session.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun save(session: Session) {
        val encrypted = cipher.encrypt(session.token)
        store.edit {
            it[RELAY_URL] = session.relayUrl
            it[DEVICE_ID] = session.deviceId
            it[DEVICE_NAME] = session.deviceName
            it[TOKEN] = encrypted
        }
    }

    suspend fun setRequireUnlock(value: Boolean) {
        store.edit { it[REQUIRE_UNLOCK] = value }
    }

    suspend fun setShowOnLockScreen(value: Boolean) {
        store.edit { it[LOCKSCREEN_DETAILS] = value }
    }

    suspend fun clear() {
        store.edit {
            it.remove(RELAY_URL)
            it.remove(DEVICE_ID)
            it.remove(DEVICE_NAME)
            it.remove(TOKEN)
        }
        cipher.deleteKey()
    }

    private companion object {
        val RELAY_URL = stringPreferencesKey("relay_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val TOKEN = stringPreferencesKey("device_token")
        val REQUIRE_UNLOCK = booleanPreferencesKey("require_unlock")
        val THEME = stringPreferencesKey("theme_mode")
        val LOCKSCREEN_DETAILS = booleanPreferencesKey("lockscreen_details")
    }
}
