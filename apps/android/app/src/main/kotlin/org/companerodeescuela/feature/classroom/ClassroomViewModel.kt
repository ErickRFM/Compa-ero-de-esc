package org.companerodeescuela.feature.classroom

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
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.ClassInvite
import org.companerodeescuela.shared.contracts.ClassroomSummary

data class ClassroomUiState(
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val classrooms: List<ClassroomSummary> = emptyList(),
    val groups: List<AcademicGroupSummary> = emptyList(),
    val activeInvite: ClassInvite? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class ClassroomViewModel @Inject constructor(
    private val repository: ClassroomRepository,
    private val groupRepository: AcademicGroupRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClassroomUiState())
    val state: StateFlow<ClassroomUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            when (val outcome = repository.classrooms()) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        classrooms = outcome.value,
                        errorMessage = null,
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = outcome.error.userMessage,
                    )
                }
            }
            when (val outcome = groupRepository.groups()) {
                is Outcome.Success -> _state.update {
                    it.copy(groups = outcome.value)
                }
                is Outcome.Failure -> Unit
            }
        }
    }

    fun join(code: String) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    submitting = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            when (val outcome = repository.join(code)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            submitting = false,
                            successMessage = "Te uniste a " + outcome.value.classroom.name + ".",
                        )
                    }
                    refresh()
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = outcome.error.userMessage,
                    )
                }
            }
        }
    }


    fun createGroup(id: String, name: String) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, errorMessage = null, successMessage = null) }
            when (val outcome = groupRepository.create(id, name)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            submitting = false,
                            successMessage = "Grupo " + outcome.value.name + " creado.",
                        )
                    }
                    refresh()
                }
                is Outcome.Failure -> _state.update {
                    it.copy(submitting = false, errorMessage = outcome.error.userMessage)
                }
            }
        }
    }

    fun assignGroupMember(groupId: String, userId: String) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, errorMessage = null, successMessage = null) }
            when (val outcome = groupRepository.assignMember(groupId, userId)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            submitting = false,
                            successMessage = "Cuenta vinculada al grupo " + outcome.value.groupName + ".",
                        )
                    }
                    refresh()
                }
                is Outcome.Failure -> _state.update {
                    it.copy(submitting = false, errorMessage = outcome.error.userMessage)
                }
            }
        }
    }

    fun createClassroom(
        name: String,
        description: String?,
        room: String?,
        groupId: String,
        groupName: String,
        teacherId: String,
        teacherDisplayName: String,
    ) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    submitting = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            when (
                val outcome = repository.create(
                    name = name,
                    description = description,
                    room = room,
                    groupId = groupId,
                    groupName = groupName,
                    teacherId = teacherId,
                    teacherDisplayName = teacherDisplayName,
                )
            ) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            submitting = false,
                            successMessage = "Clase " + outcome.value.name + " asignada a " + outcome.value.groupName + ".",
                        )
                    }
                    refresh()
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = outcome.error.userMessage,
                    )
                }
            }
        }
    }

    fun generateInvite(classroomId: String) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    submitting = true,
                    activeInvite = null,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            when (val outcome = repository.createInvite(classroomId, ttlMinutes = 30)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        submitting = false,
                        activeInvite = outcome.value,
                        successMessage = "Clave generada. Expira en 30 minutos.",
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = outcome.error.userMessage,
                    )
                }
            }
        }
    }

    fun revokeInvite() {
        val invite = _state.value.activeInvite ?: return
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, errorMessage = null) }
            when (
                val outcome = repository.revokeInvite(
                    classroomId = invite.classroomId,
                    inviteId = invite.id,
                )
            ) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        submitting = false,
                        activeInvite = null,
                        successMessage = "Invitación revocada.",
                    )
                }
                is Outcome.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = outcome.error.userMessage,
                    )
                }
            }
        }
    }
}
