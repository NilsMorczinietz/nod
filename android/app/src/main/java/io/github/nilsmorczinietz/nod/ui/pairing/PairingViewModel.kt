package io.github.nilsmorczinietz.nod.ui.pairing

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nilsmorczinietz.nod.BuildConfig
import io.github.nilsmorczinietz.nod.data.NodRepository
import io.github.nilsmorczinietz.nod.data.PairingLink
import io.github.nilsmorczinietz.nod.data.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PairingError { InvalidLink, InsecureUrl, InvalidCode, Network }

data class PairingUiState(
    val relayUrl: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: PairingError? = null,
)

class PairingViewModel(private val repository: NodRepository) : ViewModel() {
    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    fun onRelayChange(value: String) = _state.update { it.copy(relayUrl = value.trim(), error = null) }

    fun onCodeChange(value: String) = _state.update { it.copy(code = value.uppercase().take(9), error = null) }

    /** Handles `nod://pair?relay=…&code=…` from the QR scanner or a deep link; pairs right away. */
    fun onPairingLink(raw: String) {
        val link = PairingLink.parse(raw)
        if (link == null) {
            _state.update { it.copy(error = PairingError.InvalidLink) }
            return
        }
        _state.update { it.copy(relayUrl = link.relay, code = link.code, error = null) }
        pair()
    }

    fun pair() {
        val current = _state.value
        if (current.busy || current.relayUrl.isBlank() || current.code.isBlank()) return
        if (!isAllowedUrl(current.relayUrl)) {
            _state.update { it.copy(error = PairingError.InsecureUrl) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val error = try {
                repository.pair(current.relayUrl, current.code)
                null
            } catch (e: ApiException) {
                Log.w(TAG, "Pairing rejected: ${e.status} ${e.code}", e)
                if (e.code == "invalid_pairing_code") PairingError.InvalidCode else PairingError.Network
            } catch (e: Exception) {
                Log.w(TAG, "Pairing failed", e)
                PairingError.Network
            }
            _state.update { it.copy(busy = false, error = error) }
        }
    }

    private companion object {
        const val TAG = "NodPairing"
    }

    private fun isAllowedUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme == "https") return true
        // Plain HTTP only for local test builds (see network_security_config_local.xml).
        return BuildConfig.LOCAL_TEST && uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1")
    }
}
