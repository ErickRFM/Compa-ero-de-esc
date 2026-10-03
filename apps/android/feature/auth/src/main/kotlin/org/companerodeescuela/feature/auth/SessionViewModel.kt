package org.companerodeescuela.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.UserRole

data class SessionUiState(
    val checking: Boolean = true,
    val authenticated: Boolean = false,
    val submitting: Boolean = false,
    val displayName: String? = null,
    val roles: Set<UserRole> = emptySet(),
    val errorMessage: String? = null,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val tokenStore: SessionTokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    init {
        observeSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            tokenStore.observeAccessToken().collectLatest { token ->
                val claims = token?.let(SessionTokenInspector::inspect)
                val now = Instant.now().epochSecond

                if (claims == null || claims.expiresAtEpochSeconds <= now) {
                    if (token != null) {
                        runCatching { tokenStore.clear() }
                    }
                    _state.update {
                        it.copy(
                            checking = false,
                            authenticated = false,
                            submitting = false,
                            displayName = null,
                            roles = emptySet(),
                        )
                    }
                    return@collectLatest
                }

                _state.update {
                    it.copy(
                        checking = false,
                        authenticated = true,
                        submitting = false,
                        displayName = claims.displayName ?: it.displayName,
                        roles = claims.roles,
                        errorMessage = null,
                    )
                }

                val millisUntilExpiry =
                    ((claims.expiresAtEpochSeconds - now) * 1_000L).coerceAtLeast(1L)
                delay(millisUntilExpiry)

                val current = tokenStore.readAccessToken()
                if (current == token) {
                    tokenStore.clear()
                }
            }
        }
    }

    fun login(username: String, password: String) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, errorMessage = null) }
            when (val result = repository.login(username, password)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        checking = false,
                        authenticated = true,
                        submitting = false,
                        displayName = result.value.displayName,
                        roles = result.value.roles,
                        errorMessage = null,
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        checking = false,
                        authenticated = false,
                        submitting = false,
                        errorMessage = result.error.userMessage,
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}
