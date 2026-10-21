package com.alotube.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.auth.AccountRepository
import com.alotube.app.data.auth.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class LoginStatus { BROWSING, SAVING, DONE }

data class LoginUiState(
    val status: LoginStatus = LoginStatus.BROWSING,
    /** true = no se pudo completar el inicio de sesión (cookies ausentes o error al guardar). */
    val error: Boolean = false
)

class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun dismissError() {
        _state.update { it.copy(error = false) }
    }

    /**
     * La página terminó de cargar en youtube.com. Se validan las cookies (SAPISID y
     * __Secure-3PAPISID); si están, se lee el correo (opcional) y se guarda la sesión cifrada.
     */
    fun onYoutubePageLoaded(youtubeCookies: String?, googleCookies: String?) {
        if (_state.value.status != LoginStatus.BROWSING) return

        if (youtubeCookies == null || !SessionStore.hasSessionKeys(youtubeCookies)) {
            _state.update { it.copy(error = true) }
            return
        }

        _state.update { it.copy(status = LoginStatus.SAVING, error = false) }
        viewModelScope.launch {
            try {
                val profile = withTimeoutOrNull(6_000) { AccountRepository.fetchProfile(googleCookies) }
                val saved = SessionStore.save(getApplication(), youtubeCookies, profile?.email, profile?.name)
                _state.update {
                    if (saved) it.copy(status = LoginStatus.DONE)
                    else it.copy(status = LoginStatus.BROWSING, error = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(status = LoginStatus.BROWSING, error = true) }
            }
        }
    }
}
