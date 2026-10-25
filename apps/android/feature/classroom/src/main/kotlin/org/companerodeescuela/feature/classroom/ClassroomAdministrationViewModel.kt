@file:Suppress("ktlint:standard:no-wildcard-imports")

package org.companerodeescuela.feature.classroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.common.result.*
import org.companerodeescuela.core.network.MfaRepository
import org.companerodeescuela.feature.admin.AdminRepository
import org.companerodeescuela.shared.contracts.*
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class ClassroomAdministrationState(
    val loading: Boolean = true,
    val working: Boolean = false,
    val actor: UserSummary? = null,
    val users: List<UserSummary> = emptyList(),
    val institutions: List<InstitutionSummary> = emptyList(),
    val groups: List<AcademicGroupSummary> = emptyList(),
    val classrooms: List<ClassroomSummary> = emptyList(),
    val operationId: String? = null,
    val operationResource: String? = null,
    val failure: AppError? = null,
    val completed: Boolean = false,
    val ownerKey: String? = null,
    val activeInvite: ClassInvite? = null,
)

/** Extends the existing classroom tools; all selected identities and scope metadata come from live API reads. */
@HiltViewModel
class ClassroomAdministrationViewModel
    @Inject
    constructor(
        private val classrooms: ClassroomRepository,
        private val groups: AcademicGroupRepository,
        private val admin: AdminRepository,
        private val academic: AcademicRepository,
        private val mfa: MfaRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(ClassroomAdministrationState())
        val state: StateFlow<ClassroomAdministrationState> = mutable.asStateFlow()
        private var owner: String? = null
        private var generation = 0L
        private var loadJob: Job? = null
        private var actionJob: Job? = null

        private data class Prepared(
            val resource: String,
            val operationId: String,
            val digest: String,
            val execute: suspend (String) -> Outcome<*>,
        )

        private var pending: Prepared? = null
        private var proof: StepUpProof? = null

        init {
            viewModelScope.launch {
                mfa.observeOwner().collect { next ->
                    generation++
                    owner = next
                    loadJob?.cancel()
                    actionJob?.cancel()
                    pending = null
                    proof = null
                    mutable.value =
                        ClassroomAdministrationState(
                            loading = next != null,
                            ownerKey = next,
                            failure = if (next == null) AppError.Http(401) else null,
                        )
                    if (next != null) refresh()
                }
            }
        }

        private fun current(
            captured: Long,
            capturedOwner: String?,
        ) = captured == generation && capturedOwner != null && owner == capturedOwner

        fun refresh() {
            if (owner == null) return
            generation++
            loadJob?.cancel()
            actionJob?.cancel()
            pending = null
            proof = null
            val captured = generation
            val capturedOwner = owner
            mutable.value =
                mutable.value.copy(
                    loading = true,
                    working = false,
                    operationId = null,
                    operationResource = null,
                    failure = null,
                    activeInvite = null,
                )
            loadJob =
                viewModelScope.launch {
                    val actor = admin.currentUser()
                    val users = if (actor is Outcome.Success) admin.academicDirectory() else actor
                    val directory = if (users is Outcome.Success) admin.institutions() else users
                    val actualGroups = if (directory is Outcome.Success) groups.groups() else directory
                    val actualClasses = if (actualGroups is Outcome.Success) classrooms.classrooms() else actualGroups
                    if (!current(captured, capturedOwner)) return@launch
                    val error =
                        listOf(
                            actor,
                            users,
                            directory,
                            actualGroups,
                            actualClasses,
                        ).filterIsInstance<Outcome.Failure>().firstOrNull()?.error
                    if (error != null) {
                        mutable.value = ClassroomAdministrationState(loading = false, ownerKey = capturedOwner, failure = error)
                        return@launch
                    }
                    val profile = (actor as Outcome.Success).value
                    if (!profile.active ||
                        profile.accountStatus != AccountStatus.ACTIVE ||
                        !profile.emailVerified ||
                        profile.roles.none { it == UserRole.ADMIN || it == UserRole.SUPER_ADMIN }
                    ) {
                        mutable.value =
                            ClassroomAdministrationState(loading = false, ownerKey = capturedOwner, failure = AppError.Http(403))
                        return@launch
                    }
                    mutable.value =
                        mutable.value.copy(
                            loading = false,
                            actor = profile,
                            users = (users as Outcome.Success<List<UserSummary>>).value,
                            institutions = (directory as Outcome.Success<List<InstitutionSummary>>).value,
                            groups = (actualGroups as Outcome.Success<List<AcademicGroupSummary>>).value,
                            classrooms = (actualClasses as Outcome.Success<List<ClassroomSummary>>).value,
                        )
                }
        }

        private fun ready() = owner != null && !mutable.value.loading && !mutable.value.working && mutable.value.actor != null

        private fun canPrepare() = ready() && pending == null

        private fun invalid() {
            mutable.value = mutable.value.copy(failure = AppError.Http(422))
        }

        private fun identifier(value: String) =
            value.isNotBlank() && value == value.trim() && value.length <= 128 && value.none(Char::isISOControl)

        private fun scoped(group: AcademicGroupSummary) =
            group.institutionId != null &&
                group.periodId != null &&
                group.scopeRevision >= 0 &&
                group.active &&
                (group.startsAtEpochSeconds ?: -1) >= 0 &&
                (group.startsAtEpochSeconds ?: 0) < (group.endsAtEpochSeconds ?: 0) &&
                (group.endsAtEpochSeconds ?: 0) > clock.instant().epochSecond &&
                (UserRole.SUPER_ADMIN in mutable.value.actor!!.roles || group.institutionId == mutable.value.actor!!.institutionId)

        private fun eligible(
            user: UserSummary,
            role: UserRole,
            institution: String,
        ) = user.active &&
            user.accountStatus == AccountStatus.ACTIVE &&
            user.emailVerified &&
            role in user.roles &&
            user.institutionId == institution

        private fun prepare(
            resource: String,
            operation: String,
            digest: String,
            execute: suspend (String) -> Outcome<*>,
        ) {
            pending = Prepared(resource, operation, digest, execute)
            proof = null
            mutable.value = mutable.value.copy(operationId = operation, operationResource = resource, failure = null, completed = false)
        }

        fun prepareGroup(
            id: String,
            name: String,
            institution: String,
            period: String,
            start: Long,
            end: Long,
        ) {
            if (!canPrepare()) return
            val actor = mutable.value.actor ?: return
            val normalized = id.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), "-")
            if (!identifier(normalized) ||
                name.trim().length !in 2..120 ||
                name.any(Char::isISOControl) ||
                !identifier(period) ||
                start < 0 ||
                start >= end ||
                end > 4102444800L ||
                end <= clock.instant().epochSecond ||
                mutable.value.institutions.none { it.id == institution } ||
                (UserRole.SUPER_ADMIN !in actor.roles && actor.institutionId != institution)
            ) {
                return invalid()
            }
            val request = CreateAcademicGroupRequest(normalized, name.trim(), institution, period, start, end, UUID.randomUUID().toString())
            prepare(
                normalized,
                request.operationId!!,
                PrivilegedCommandDigests.groupCreation(normalized, request),
            ) { evidence -> groups.create(request.copy(proof = evidence)) }
        }

        fun prepareMember(
            groupId: String,
            userId: String,
        ) {
            if (!canPrepare()) return
            val group = mutable.value.groups.find { it.id == groupId && scoped(it) } ?: return invalid()
            val student = mutable.value.users.find { it.id == userId } ?: return invalid()
            if (!eligible(student, UserRole.STUDENT, group.institutionId!!)) return invalid()
            val request = AssignAcademicGroupMemberRequest(userId, UUID.randomUUID().toString(), group.scopeRevision)
            prepare(
                group.id,
                request.operationId!!,
                PrivilegedCommandDigests.groupMembership(group.id, request),
            ) { evidence -> groups.assignMember(group.id, request.copy(proof = evidence)) }
        }

        fun prepareClass(
            groupId: String,
            teacherId: String,
            name: String,
            room: String,
        ) {
            if (!canPrepare()) return
            val group = mutable.value.groups.find { it.id == groupId && scoped(it) } ?: return invalid()
            val teacher = mutable.value.users.find { it.id == teacherId } ?: return invalid()
            if (!eligible(teacher, UserRole.TEACHER, group.institutionId!!) ||
                name.trim().length !in 2..120 ||
                name.any(Char::isISOControl) ||
                room.length > 120 ||
                room.any(Char::isISOControl)
            ) {
                return invalid()
            }
            val request =
                CreateClassroomRequest(
                    name.trim(),
                    room =
                        room.trim().takeIf {
                            it.isNotBlank()
                        },
                    groupId = group.id,
                    groupName = group.name,
                    teacherId = teacher.id,
                    teacherDisplayName = teacher.displayName,
                    operationId = UUID.randomUUID().toString(),
                    groupScopeRevision = group.scopeRevision,
                )
            prepare(
                group.id,
                request.operationId!!,
                PrivilegedCommandDigests.classroomAssignment(group.id, request),
            ) { evidence -> classrooms.create(request.copy(proof = evidence)) }
        }

        fun prepareSchedule(
            classId: String,
            day: String,
            start: String,
            end: String,
        ) {
            if (!canPrepare()) return
            val assigned = mutable.value.classrooms.find { it.id == classId && it.status == ClassroomStatus.ACTIVE } ?: return invalid()
            val group = mutable.value.groups.find { it.id == assigned.groupId && scoped(it) } ?: return invalid()
            val teacher = mutable.value.users.find { it.id == assigned.teacherId } ?: return invalid()
            if (!eligible(teacher, UserRole.TEACHER, group.institutionId!!)) return invalid()
            val times =
                runCatching {
                    Triple(
                        DayOfWeek.valueOf(day),
                        LocalTime.parse(start),
                        LocalTime.parse(end),
                    )
                }.getOrNull() ?: return invalid()
            if (times.second >= times.third || start.length != 5 || end.length != 5) return invalid()
            val request =
                UpsertScheduleBlockRequest(
                    ownerId = group.id,
                    dayOfWeek = times.first.name,
                    startTime = start,
                    endTime = end,
                    subjectId = assigned.id,
                    subjectName = assigned.name,
                    teacherId = assigned.teacherId,
                    teacherName = assigned.teacherDisplayName,
                    room = assigned.room,
                    groupId = group.id,
                    groupName = group.name,
                    operationId = UUID.randomUUID().toString(),
                    groupScopeRevision = group.scopeRevision,
                )
            prepare(
                group.id,
                request.operationId!!,
                PrivilegedCommandDigests.scheduleUpsert(group.id, request),
            ) { evidence -> academic.publishSchoolSchedule(request.copy(proof = evidence)) }
        }

        fun generateInvite(id: String) {
            if (!canPrepare() ||
                mutable.value.classrooms.none { it.id == id && it.canManageEnrollment && it.status == ClassroomStatus.ACTIVE }
            ) {
                return
            }
            val captured = generation
            val capturedOwner = owner
            mutable.value = mutable.value.copy(working = true, failure = null, activeInvite = null)
            actionJob =
                viewModelScope.launch {
                    val result = classrooms.createInvite(id)
                    if (!current(captured, capturedOwner)) return@launch
                    mutable.value =
                        when (result) {
                            is Outcome.Success -> mutable.value.copy(working = false, activeInvite = result.value, failure = null)
                            is Outcome.Failure -> mutable.value.copy(working = false, failure = result.error)
                        }
                }
        }

        fun revokeInvite() {
            if (!canPrepare()) return
            val invite = mutable.value.activeInvite ?: return
            val captured = generation
            val capturedOwner = owner
            mutable.value = mutable.value.copy(working = true, failure = null)
            actionJob =
                viewModelScope.launch {
                    val result = classrooms.revokeInvite(invite.classroomId, invite.id)
                    if (!current(captured, capturedOwner)) return@launch
                    mutable.value =
                        when (result) {
                            is Outcome.Success -> mutable.value.copy(working = false, activeInvite = null, failure = null)
                            is Outcome.Failure -> mutable.value.copy(working = false, failure = result.error)
                        }
                }
        }

        fun cancelOperation() {
            generation++
            actionJob?.cancel()
            pending = null
            proof = null
            mutable.value =
                mutable.value.copy(
                    operationId = null,
                    operationResource = null,
                    working = false,
                    failure = null,
                    activeInvite = null,
                )
        }

        fun authorize(
            password: String,
            otp: String,
        ) {
            if (!ready()) return
            val command = pending ?: return
            val cached = proof?.takeIf { it.expiresAtEpochSeconds > clock.instant().epochSecond }
            if (cached == null && (password.length !in 1..128 || otp.length != 6 || otp.any { it !in '0'..'9' })) return invalid()
            val captured = generation
            val capturedOwner = owner
            mutable.value = mutable.value.copy(working = true, failure = null)
            actionJob =
                viewModelScope.launch {
                    val result =
                        if (cached !=
                            null
                        ) {
                            Outcome.Success(cached)
                        } else {
                            mfa.stepUp(
                                MfaStepUpRequest(
                                    password,
                                    otp,
                                    StepUpAction.MANAGE_ACADEMICS,
                                    command.resource,
                                    command.operationId,
                                    command.digest,
                                ),
                            )
                        }
                    if (!current(captured, capturedOwner) || pending !== command) return@launch
                    if (result is Outcome.Failure) {
                        proof = null
                        mutable.value = mutable.value.copy(working = false, failure = result.error)
                        return@launch
                    }
                    val evidence = (result as Outcome.Success).value
                    if (evidence.expiresAtEpochSeconds <=
                        clock.instant().epochSecond
                    ) {
                        proof = null
                        mutable.value = mutable.value.copy(working = false, failure = AppError.Http(403))
                        return@launch
                    }
                    proof = evidence
                    val changed = command.execute(evidence.proof)
                    if (!current(captured, capturedOwner) || pending !== command) return@launch
                    when (changed) {
                        is Outcome.Success -> {
                            pending = null
                            proof = null
                            mutable.value =
                                mutable.value.copy(
                                    loading = true,
                                    working = false,
                                    operationId = null,
                                    operationResource = null,
                                    completed = true,
                                    failure = null,
                                )
                            refresh()
                        }
                        is Outcome.Failure -> {
                            if ((changed.error as? AppError.Http)?.status?.let { it < 500 } == true) proof = null
                            mutable.value = mutable.value.copy(working = false, failure = changed.error)
                        }
                    }
                }
        }
    }
