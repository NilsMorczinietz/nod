package io.github.nilsmorczinietz.nod.data.store

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI memory for the delivery checklist: onboarding shown, banner dismissed. */
class DeliveryPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _dismissedBanner = MutableStateFlow(prefs.getString(KEY_DISMISSED, null))

    /** The onboarding step shows once per pairing (device id). */
    fun isOnboarded(deviceId: String): Boolean = prefs.getString(KEY_ONBOARDED, null) == deviceId

    fun setOnboarded(deviceId: String) = prefs.edit { putString(KEY_ONBOARDED, deviceId) }

    /**
     * Signature of the problems the user dismissed in the inbox banner. The
     * banner comes back when a different problem appears.
     */
    val dismissedBanner: StateFlow<String?> = _dismissedBanner.asStateFlow()

    fun dismissBanner(signature: String) {
        prefs.edit { putString(KEY_DISMISSED, signature) }
        _dismissedBanner.value = signature
    }

    private companion object {
        const val FILE = "delivery"
        const val KEY_ONBOARDED = "onboarded_device"
        const val KEY_DISMISSED = "dismissed_banner"
    }
}
