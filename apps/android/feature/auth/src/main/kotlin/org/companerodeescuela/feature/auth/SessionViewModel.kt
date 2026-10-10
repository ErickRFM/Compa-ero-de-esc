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
    val accountStatus: org.companerodeescuela.shared.contracts.AccountStatus = org.companerodeescuela.shared.contracts.AccountStatus.ACTIVE,
    val registrationAccountType: RegistrationAccountType? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val verificationDelivery: org.companerodeescuela.shared.contracts.VerificationDeliveryStatus? = null,
    val institutions: List<org.companerodeescuela.shared.contracts.InstitutionSummary> = emptyList(),
    val institutionsLoading: Boolean = false,
    val institutionsFailure: AppError? = null,
    val verificationFailure: AppError? = null,

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
                accountStatus = user.accountStatus, registrationAccountType = user.registrationAccountType,
                email = user.email, emailVerified = user.emailVerified, verificationDelivery = user.verificationDelivery,
                verificationFailure = null,
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
                accountStatus = org.companerodeescuela.shared.contracts.AccountStatus.ACTIVE,
                registrationAccountType = null, email = null, emailVerified = false, verificationDelivery = null,
                verificationFailure = null,
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

    fun register(displayName: String, email: String, password: String, accountType: RegistrationAccountType) =
        register(org.companerodeescuela.shared.contracts.RegisterRequest(displayName, email, password, accountType))

    fun register(request: org.companerodeescuela.shared.contracts.RegisterRequest) {
        if (!beginCredentials()) return
        credentialJob = viewModelScope.launch { publishCredentialResult(repository.register(request), login = false) }
    }

    private var institutionsJob: Job? = null
    fun loadRegistrationInstitutions() {
        if (institutionsJob?.isActive == true) return
        _state.update { it.copy(institutionsLoading = true, institutionsFailure = null) }
        institutionsJob = viewModelScope.launch {
            when (val result = repository.institutions()) {
                is Outcome.Success -> _state.update { it.copy(institutions = result.value, institutionsLoading = false) }
                is Outcome.Failure -> _state.update { it.copy(institutions = emptyList(), institutionsLoading = false, institutionsFailure = result.error) }
            }
        }
    }

    fun verifyEmail(token: String) {
        if (!_state.value.authenticated || _state.value.accountStatus != org.companerodeescuela.shared.contracts.AccountStatus.PENDING_VERIFICATION || !beginCredentials()) return
        _state.update { it.copy(verificationFailure = null) }
        credentialJob = viewModelScope.launch {
            when (val result = repository.confirmVerification(token)) {
                is Outcome.Success -> Unit // Fresh token emission rechecks live authority.
                is Outcome.Failure -> _state.update { it.copy(submitting = false, verificationFailure = result.error) }
            }
        }
    }

    fun resendVerification() {
        if (!_state.value.authenticated || _state.value.accountStatus != org.companerodeescuela.shared.contracts.AccountStatus.PENDING_VERIFICATION || !beginCredentials()) return
        _state.update { it.copy(verificationFailure = null) }
        credentialJob = viewModelScope.launch {
            when (val result = repository.resendVerification()) {
                is Outcome.Success -> _state.update { it.copy(submitting = false, verificationDelivery = result.value.status) }
                is Outcome.Failure -> _state.update { it.copy(submitting = false, verificationFailure = result.error) }
            }
        }
    }

    fun refreshIdentity() {
        if (!_state.value.authenticated || !beginCredentials()) return
        credentialJob = viewModelScope.launch {
            when (val result = repository.currentUser()) {
                is Outcome.Success -> publishAuthenticated(result.value)
                is Outcome.Failure -> _state.update { it.copy(submitting = false, verificationFailure = result.error) }
            }
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
