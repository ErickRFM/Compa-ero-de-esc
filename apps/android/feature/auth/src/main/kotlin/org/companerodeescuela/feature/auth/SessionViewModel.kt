package org.companerodeescuela.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.Outcome

data class SessionUiState(
    val checking: Boolean = true,
    val authenticated: Boolean = false,
    val submitting: Boolean = false,
    val displayName: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    checking = false,
                    authenticated = repository.hasSession(),
                )
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
            _state.value = SessionUiState(checking = false)
        }
    }
}
