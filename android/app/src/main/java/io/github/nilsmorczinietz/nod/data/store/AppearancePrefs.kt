package io.github.nilsmorczinietz.nod.data.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Appearance settings that must be known before the first frame (no DataStore
 * round trip), backed by a tiny SharedPreferences file.
 */
class AppearancePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _useSystemBold = MutableStateFlow(prefs.getBoolean(KEY_SYSTEM_BOLD, false))

    /**
     * Whether the system "Bold text" setting may change Nod's font weights.
     * Off by default so the Geist weights render as designed.
     */
    val useSystemBold: StateFlow<Boolean> = _useSystemBold.asStateFlow()

    fun setUseSystemBold(value: Boolean) {
        prefs.edit().putBoolean(KEY_SYSTEM_BOLD, value).apply()
        _useSystemBold.value = value
    }

    private companion object {
        const val FILE = "appearance"
        const val KEY_SYSTEM_BOLD = "use_system_bold"
    }
}
