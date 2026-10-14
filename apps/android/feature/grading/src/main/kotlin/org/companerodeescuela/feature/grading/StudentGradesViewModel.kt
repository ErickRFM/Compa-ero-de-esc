package org.companerodeescuela.feature.grading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.feature.classroom.ClassroomRepository
import org.companerodeescuela.shared.contracts.ClassroomStatus
import org.companerodeescuela.shared.contracts.ClassroomSummary
import org.companerodeescuela.shared.contracts.GradebookSnapshot
import javax.inject.Inject

data class StudentGradesState(
    val classrooms: List<ClassroomSummary> = emptyList(),
    val selectedClassroomId: String = "",
    val gradingPeriod: String = "",
    val loadingClassrooms: Boolean = true,
    val loadingGrades: Boolean = false,
    val classroomError: String? = null,
    val gradesError: String? = null,
    val snapshot: GradebookSnapshot? = null,
    val hasQueried: Boolean = false,
)

@HiltViewModel
class StudentGradesViewModel
    @Inject
    constructor(
        private val classroomRepository: ClassroomRepository,
        private val gradebookRepository: GradebookRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(StudentGradesState())
        val state: StateFlow<StudentGradesState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                when (val result = classroomRepository.classrooms()) {
                    is Outcome.Success -> {
                        val available =
                            result.value.filter { it.status == ClassroomStatus.ACTIVE }
                        _state.update {
                            it.copy(
                                classrooms = available,
                                selectedClassroomId = available.firstOrNull()?.id.orEmpty(),
                                loadingClassrooms = false,
                            )
                        }
                    }
                    is Outcome.Failure ->
                        _state.update {
                            it.copy(loadingClassrooms = false, classroomError = result.error.userMessage)
                        }
                }
            }
        }

        fun selectClassroom(id: String) {
            if (_state.value.loadingGrades) return
            if (_state.value.classrooms.none { it.id == id }) return
            _state.update { it.copy(selectedClassroomId = id, snapshot = null, gradesError = null, hasQueried = false) }
        }

        fun setGradingPeriod(period: String) {
            if (_state.value.loadingGrades) return
            _state.update { it.copy(gradingPeriod = period, snapshot = null, gradesError = null, hasQueried = false) }
        }

        fun loadGrades() {
            val current = _state.value
            if (current.loadingGrades || current.selectedClassroomId.isBlank() || current.gradingPeriod.isBlank()) return
            viewModelScope.launch {
                _state.update { it.copy(loadingGrades = true, gradesError = null, snapshot = null, hasQueried = false) }
                when (val result = gradebookRepository.studentView(current.selectedClassroomId, current.gradingPeriod.trim())) {
                    is Outcome.Success ->
                        _state.update {
                            it.copy(loadingGrades = false, snapshot = result.value, hasQueried = true)
                        }
                    is Outcome.Failure ->
                        _state.update {
                            it.copy(loadingGrades = false, gradesError = result.error.userMessage)
                        }
                }
            }
        }
    }
