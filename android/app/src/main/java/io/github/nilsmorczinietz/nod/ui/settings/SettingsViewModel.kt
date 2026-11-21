package io.github.nilsmorczinietz.nod.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nilsmorczinietz.nod.data.NodRepository
import io.github.nilsmorczinietz.nod.data.PairingState
import io.github.nilsmorczinietz.nod.data.model.RoutingMode
import io.github.nilsmorczinietz.nod.data.store.AppearancePrefs
import io.github.nilsmorczinietz.nod.data.store.DEFAULT_THEME
import io.github.nilsmorczinietz.nod.data.store.SessionStore
import io.github.nilsmorczinietz.nod.data.store.ThemeMode
import io.github.nilsmorczinietz.nod.data.update.UpdateChecker
import io.github.nilsmorczinietz.nod.data.update.UpdateState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val deviceName: String = "",
    val relayUrl: String = "",
    val mode: RoutingMode = RoutingMode.Away,
    val requireUnlock: Boolean = false,
    val showOnLockScreen: Boolean = true,
    val themeMode: ThemeMode = DEFAULT_THEME,
    val useSystemBold: Boolean = false,
)

class SettingsViewModel(
    private val repository: NodRepository,
    private val sessionStore: SessionStore,
    private val updates: UpdateChecker,
    private val appearance: AppearancePrefs,
) : ViewModel() {

    val updateState: StateFlow<UpdateState> = updates.state

    fun checkForUpdates() {
        viewModelScope.launch { updates.check() }
    }


    private val lockScreen = combine(sessionStore.requireUnlock, sessionStore.showOnLockScreen, ::Pair)
    private val looks = combine(sessionStore.themeMode, appearance.useSystemBold, ::Pair)

    val state: StateFlow<SettingsUiState> = combine(
        repository.pairing,
        repository.settings,
        lockScreen,
        looks,
    ) { pairing, settings, (requireUnlock, showOnLockScreen), (themeMode, useSystemBold) ->
        val session = (pairing as? PairingState.Paired)?.session
        SettingsUiState(
            deviceName = session?.deviceName.orEmpty(),
            relayUrl = session?.relayUrl.orEmpty(),
            mode = settings.mode,
            requireUnlock = requireUnlock,
            themeMode = themeMode,
            useSystemBold = useSystemBold,
            showOnLockScreen = showOnLockScreen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _errors = MutableSharedFlow<String?>(extraBufferCapacity = 2)
    val errors = _errors.asSharedFlow()

    fun setMode(mode: RoutingMode) {
        viewModelScope.launch {
            runCatching { repository.setMode(mode) }.onFailure { _errors.tryEmit(it.message) }
        }
    }

    fun setRequireUnlock(value: Boolean) {
        viewModelScope.launch { sessionStore.setRequireUnlock(value) }
    }

    fun setShowOnLockScreen(value: Boolean) {
        viewModelScope.launch { sessionStore.setShowOnLockScreen(value) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { sessionStore.setThemeMode(mode) }
    }

    /** Takes effect immediately; MainActivity swaps the font resolver. */
    fun setUseSystemBold(value: Boolean) = appearance.setUseSystemBold(value)

    fun unpair() {
        viewModelScope.launch { repository.unpair() }
    }
}
