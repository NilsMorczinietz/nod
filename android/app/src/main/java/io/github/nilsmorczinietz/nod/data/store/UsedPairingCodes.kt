package io.github.nilsmorczinietz.nod.data.store

import android.content.Context
import androidx.core.content.edit

/**
 * Pairing codes this phone already paired with (or declined to). Codes are single-use, but
 * Android re-delivers a task's original `nod://pair` intent when it recreates
 * the activity (e.g. after an app update); such a stale link must not ask to
 * pair again. Failed attempts are not recorded, so they can be retried with
 * the same link.
 */
class UsedPairingCodes(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isUsed(code: String): Boolean = code.normalized() in prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun markUsed(code: String) {
        val recent = (prefs.getStringSet(KEY, emptySet()).orEmpty() + code.normalized()).toList().takeLast(MAX)
        prefs.edit { putStringSet(KEY, recent.toSet()) }
    }

    private fun String.normalized() = trim().uppercase()

    private companion object {
        const val FILE = "pairing"
        const val KEY = "used_codes"
        const val MAX = 20
    }
}
