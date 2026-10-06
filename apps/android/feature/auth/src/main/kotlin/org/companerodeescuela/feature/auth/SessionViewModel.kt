package org.companerodeescuela.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.UserRole

data class SessionUiState(
    val checking: Boolean = true,
    val authenticated: Boolean = false,
    val submitting: Boolean = false,
    val userId: String? = null,
    val displayName: String? = null,
    val roles: Set<UserRole> = emptySet(),
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val tokenStore: SessionTokenStore,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    init {
        observeSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            tokenStore.observeAccessToken().collectLatest { token ->
                if (token == null) {
                    publishSignedOut()
                    return@collectLatest
                }

                val claims = SessionTokenInspector.inspect(token)
                if (claims == null) {
                    runCatching { tokenStore.clear() }
                    publishSignedOut()
                    return@collectLatest
                }

                val millisUntilExpiry =
                    ((claims.expiresAtEpochSeconds - clock.instant().epochSecond) * 1_000L)
                        .coerceAtLeast(0L)
                if (millisUntilExpiry > 0L) {
                    publishAuthenticated(claims.userId, claims.displayName, claims.roles)
                    delay(millisUntilExpiry)
                }

                val current = tokenStore.readAccessToken()
                if (current == token) {
                    when (val refreshed = repository.refreshSession()) {
                        is Outcome.Success -> {
                            val renewed = SessionTokenInspector.inspect(refreshed.value)
                            if (renewed != null) {
                                publishAuthenticated(renewed.userId, renewed.displayName, renewed.roles)
                            }
                        }
                        is Outcome.Failure -> {
                            val refreshError = refreshed.error
                            val message = if (refreshError is AppError.Http && refreshError.status == 401) {
                                "Tu sesión terminó. Inicia sesión de nuevo."
                            } else {
                                "No pudimos renovar tu sesión. Revisa tu conexión e inténtalo de nuevo."
                            }
                            publishSignedOut(noticeMessage = message)
                        }
                    }
                }
            }
        }
    }

    private fun publishAuthenticated(
        userId: String,
        displayName: String?,
        roles: Set<UserRole>,
    ) {
        _state.update {
            it.copy(
                checking = false,
                authenticated = true,
                submitting = false,
                userId = userId,
                displayName = displayName ?: it.displayName,
                roles = roles,
                errorMessage = null,
                noticeMessage = null,
            )
        }
    }

    private fun publishSignedOut(noticeMessage: String? = null) {
        _state.update {
            it.copy(
                checking = false,
                authenticated = false,
                submitting = false,
                userId = null,
                displayName = null,
                roles = emptySet(),
                errorMessage = null,
                noticeMessage = noticeMessage,
            )
        }
    }

    fun login(username: String, password: String) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    submitting = true,
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
            when (val result = repository.login(username, password)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        checking = false,
                        authenticated = true,
                        submitting = false,
                        displayName = result.value.displayName,
                        roles = result.value.roles,
                        errorMessage = null,
                        noticeMessage = null,
                    )
                }
                is Outcome.Failure -> _state.update {
                    val message = when (val error = result.error) {
                        is AppError.Http -> if (error.status == 401) {
                            "Usuario o contraseña incorrectos."
                        } else {
                            error.userMessage
                        }
                        else -> error.userMessage
                    }
                    it.copy(
                        checking = false,
                        authenticated = false,
                        submitting = false,
                        displayName = null,
                        roles = emptySet(),
                        errorMessage = message,
                        noticeMessage = null,
                    )
                }
            }
        }
    }

    fun register(
        displayName: String,
        email: String,
        password: String,
        accountType: RegistrationAccountType,
    ) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    submitting = true,
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
            when (
                val result = repository.register(
                    displayName = displayName,
                    email = email,
                    password = password,
                    accountType = accountType,
                )
            ) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        checking = false,
                        authenticated = true,
                        submitting = false,
                        displayName = result.value.displayName,
                        roles = result.value.roles,
                        errorMessage = null,
                        noticeMessage = null,
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        checking = false,
                        authenticated = false,
                        submitting = false,
                        displayName = null,
                        roles = emptySet(),
                        errorMessage = result.error.userMessage,
                        noticeMessage = null,
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
