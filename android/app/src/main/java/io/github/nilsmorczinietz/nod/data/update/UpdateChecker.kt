package io.github.nilsmorczinietz.nod.data.update

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** A newer release on GitHub. */
data class UpdateInfo(val version: String, val downloadUrl: String, val notesUrl: String)

sealed interface UpdateState {
    data object Unknown : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object Failed : UpdateState
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

private val Context.updateStore: DataStore<Preferences> by preferencesDataStore(name = "updates")

/**
 * Checks GitHub Releases for a newer APK (no Play Store). The last result is
 * cached so the inbox banner shows without network; the network is asked at
 * most every 12 hours unless the user checks manually.
 */
class UpdateChecker(
    context: Context,
    private val http: OkHttpClient,
    private val json: Json,
    private val currentVersion: String,
    private val scope: CoroutineScope,
) {
    private val store = context.applicationContext.updateStore
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The version the user dismissed in the inbox banner, if any. */
    val dismissedVersion: Flow<String?> = store.data.map { it[DISMISSED] }

    init {
        scope.launch {
            val prefs = store.data.first()
            val cached = prefs[LATEST_VERSION]
            _state.value = if (cached != null && isNewer(cached, currentVersion)) {
                UpdateState.Available(UpdateInfo(cached, prefs[LATEST_DOWNLOAD].orEmpty(), prefs[LATEST_NOTES].orEmpty()))
            } else {
                UpdateState.Unknown
            }
        }
    }

    /** Checks if the last check is older than 12 h (called on app start). */
    suspend fun checkIfDue() {
        val last = store.data.first()[LAST_CHECK] ?: 0L
        if (System.currentTimeMillis() - last >= CHECK_INTERVAL_MS) check()
    }

    suspend fun check() {
        _state.value = UpdateState.Checking
        _state.value = try {
            val release = fetchLatest()
            store.edit { it[LAST_CHECK] = System.currentTimeMillis() }
            val version = release?.tagName?.removePrefix("v")
            if (release == null || version == null || !isNewer(version, currentVersion)) {
                UpdateState.UpToDate
            } else {
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                val info = UpdateInfo(version, apk?.downloadUrl ?: release.htmlUrl, release.htmlUrl)
                store.edit {
                    it[LATEST_VERSION] = info.version
                    it[LATEST_DOWNLOAD] = info.downloadUrl
                    it[LATEST_NOTES] = info.notesUrl
                }
                UpdateState.Available(info)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            UpdateState.Failed
        }
    }

    suspend fun dismiss(version: String) {
        store.edit { it[DISMISSED] = version }
    }

    /** Latest non-draft, non-prerelease release; null when the repo has none yet. */
    private suspend fun fetchLatest(): GithubRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Nod-Android/$currentVersion")
            .build()
        http.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> error("HTTP ${response.code}")
                else -> json.decodeFromString(GithubRelease.serializer(), response.body.string())
                    .takeUnless { it.draft || it.prerelease }
            }
        }
    }

    companion object {
        private const val TAG = "NodUpdate"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
        const val REPO_URL = "https://github.com/NilsMorczinietz/nod"
        const val RELEASES_URL = "$REPO_URL/releases"
        private const val LATEST_URL = "https://api.github.com/repos/NilsMorczinietz/nod/releases/latest"

        private val LAST_CHECK = longPreferencesKey("last_check")
        private val LATEST_VERSION = stringPreferencesKey("latest_version")
        private val LATEST_DOWNLOAD = stringPreferencesKey("latest_download")
        private val LATEST_NOTES = stringPreferencesKey("latest_notes")
        private val DISMISSED = stringPreferencesKey("dismissed_version")

        /** Semantic version comparison ("0.10.0" > "0.9.3"); suffixes like "-beta" are ignored. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
