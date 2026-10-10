package org.companerodeescuela.feature.tutoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.GroupRepresentativeAssignment
import org.companerodeescuela.shared.contracts.RepresentativeStatus
import org.companerodeescuela.shared.contracts.UserRole

data class StudentRepresentativesState(
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val assignments: List<GroupRepresentativeAssignment> = emptyList(),
    val error: String? = null,
    val notice: String? = null,
)

/** The session, not a UI-selected role, controls the lifetime of student invitations. */
@HiltViewModel
class StudentRepresentativesViewModel @Inject constructor(
    private val repository: GroupRepresentativeRepository,
    private val tokens: SessionTokenStore,
) : ViewModel() {
    private val mutable = MutableStateFlow(StudentRepresentativesState())
    val state: StateFlow<StudentRepresentativesState> = mutable.asStateFlow()
    private var accountKey: String? = null
    private var generation: Long = 0
    private var request: Job? = null

    init {
        viewModelScope.launch {
            tokens.observeAccessToken().map { token ->
                SessionTokenInspector.inspect(token.orEmpty())?.takeIf { UserRole.STUDENT in it.roles }
                    ?.let { "${it.userId}:${it.sessionId.orEmpty()}" }
            }.distinctUntilChanged().collect { key ->
                generation++
                request?.cancel()
                accountKey = key
                mutable.value = StudentRepresentativesState(loading = key != null)
                if (key != null) refresh()
            }
        }
    }

    fun refresh() {
        val key = accountKey ?: run {
            mutable.value = StudentRepresentativesState(loading = false)
            return
        }
        val expected = generation
        request?.cancel()
        request = viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            when (val response = repository.getMyAssignments()) {
                is Outcome.Success -> if (sameAccount(key, expected)) {
                    mutable.update { it.copy(loading = false, assignments = response.value, error = null) }
                }
                is Outcome.Failure -> if (sameAccount(key, expected)) {
                    mutable.update { it.copy(loading = false, assignments = emptyList(), error = response.error.userMessage) }
                }
            }
        }
    }

    fun decide(id: String, accept: Boolean) {
        val key = accountKey ?: return
        val current = mutable.value
        if (current.loading || current.submitting ||
            current.assignments.none { it.id == id && it.status == RepresentativeStatus.PENDING }) return
        val expected = generation
        request?.cancel()
        request = viewModelScope.launch {
            mutable.update { it.copy(submitting = true, error = null, notice = null) }
            val result = if (accept) repository.accept(id) else repository.decline(id)
            if (!sameAccount(key, expected)) return@launch
            when (result) {
                is Outcome.Success -> {
                    mutable.update { currentState ->
                        currentState.copy(submitting = false,
                            assignments = currentState.assignments.map {
                                if (it.id == id) result.value else it
                            },
                            notice = if (accept) "Nombramiento aceptado." else "Invitación rechazada.")
                    }
                }
                is Outcome.Failure -> {
                    mutable.update { it.copy(submitting = false, error = result.error.userMessage) }
                    // The authoritative list may have changed or the appointment may be revoked.
                    refresh()
                }
            }
        }
    }

    private fun sameAccount(key: String, expected: Long): Boolean =
        accountKey == key && generation == expected
}
