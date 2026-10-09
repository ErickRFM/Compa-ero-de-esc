package org.companerodeescuela.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

enum class SessionNotice { EXPIRED, CONNECTION }

data class SessionUiState(
    val checking: Boolean = true,
    val authenticated: Boolean = false,
    val submitting: Boolean = false,
    val userId: String? = null,
    val displayName: String? = null,
    val roles: Set<UserRole> = emptySet(),
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val failure: AppError? = null,
    val noticeReason: SessionNotice? = null,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val tokenStore: SessionTokenStore,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()
    private var credentialJob: Job? = null
    private var signingOut = false

    init { observeSession() }

    private fun observeSession() {
        viewModelScope.launch {
            var sawToken = false
            tokenStore.observeAccessToken().collectLatest { token ->
                if (token == null) {
                    val ended = sawToken && !signingOut
                    sawToken = false
                    publishSignedOut(if (ended) SessionNotice.EXPIRED else _state.value.noticeReason)
                    return@collectLatest
                }
                sawToken = true
                if (SessionTokenInspector.inspect(token) == null) {
                    tokenStore.clear()
                    publishSignedOut(SessionNotice.EXPIRED)
                    return@collectLatest
                }
                // Claims provide expiry bookkeeping; only /auth/me grants restored navigation.
                when (val verified = repository.currentUser()) {
                    is Outcome.Failure -> {
                        val status = (verified.error as? AppError.Http)?.status
                        publishSignedOut(if (status == 401 || status == 403) SessionNotice.EXPIRED else SessionNotice.CONNECTION)
                        return@collectLatest
                    }
                    is Outcome.Success -> publishAuthenticated(verified.value)
                }
                val current = tokenStore.readAccessToken() ?: return@collectLatest
                val claims = SessionTokenInspector.inspect(current) ?: return@collectLatest
                val remaining = ((claims.expiresAtEpochSeconds - clock.instant().epochSecond) * 1_000L).coerceAtLeast(0L)
                if (remaining > 0L) delay(remaining)
                if (tokenStore.readAccessToken() == current) {
                    when (val refreshed = repository.refreshSession()) {
                        is Outcome.Success -> Unit // Token emission rechecks the server authority.
                        is Outcome.Failure -> publishSignedOut(
                            if ((refreshed.error as? AppError.Http)?.status == 401) SessionNotice.EXPIRED else SessionNotice.CONNECTION)
                    }
                }
            }
        }
    }

    private fun publishAuthenticated(user: UserSummary) {
        if (signingOut) return
        _state.update {
            it.copy(checking = false, authenticated = true, submitting = false, userId = user.id,
                displayName = user.displayName, roles = user.roles, errorMessage = null, noticeMessage = null,
                failure = null, noticeReason = null)
        }
    }

    private fun publishSignedOut(notice: SessionNotice? = null) {
        val message = when (notice) {
            SessionNotice.EXPIRED -> "Tu sesión terminó. Inicia sesión de nuevo."
            SessionNotice.CONNECTION -> "No pudimos renovar tu sesión. Revisa tu conexión e inténtalo de nuevo."
            null -> null
        }
        _state.update {
            it.copy(checking = false, authenticated = false, submitting = signingOut, userId = null,
                displayName = null, roles = emptySet(), errorMessage = null, noticeMessage = message,
                failure = null, noticeReason = notice)
        }
    }

    private fun beginCredentials(): Boolean {
        if (_state.value.submitting || credentialJob?.isActive == true || signingOut) return false
        _state.update { it.copy(submitting = true, errorMessage = null, noticeMessage = null, failure = null, noticeReason = null) }
        return true
    }

    fun login(username: String, password: String) {
        if (!beginCredentials()) return
        credentialJob = viewModelScope.launch {
            publishCredentialResult(repository.login(username, password), login = true)
        }
    }

    fun register(displayName: String, email: String, password: String, accountType: RegistrationAccountType) {
        if (!beginCredentials()) return
        credentialJob = viewModelScope.launch {
            publishCredentialResult(repository.register(displayName, email, password, accountType), login = false)
        }
    }

    private fun publishCredentialResult(result: Outcome<UserSummary>, login: Boolean) {
        when (result) {
            is Outcome.Success -> Unit // Token observation publishes the newer /auth/me authority.
            is Outcome.Failure -> _state.update {
                it.copy(checking = false, authenticated = false, submitting = signingOut, userId = null,
                    displayName = null, roles = emptySet(),
                    errorMessage = if (login && (result.error as? AppError.Http)?.status == 401)
                        "Usuario o contraseña incorrectos." else result.error.userMessage,
                    noticeMessage = null, failure = result.error, noticeReason = null)
            }
        }
    }

    fun logout() {
        if (signingOut) return
        signingOut = true
        credentialJob?.cancel()
        publishSignedOut()
        viewModelScope.launch {
            try { repository.logout() } finally { signingOut = false; publishSignedOut() }
        }
    }
}
