package org.companerodeescuela.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.*
import org.companerodeescuela.core.network.MfaRepository
import org.companerodeescuela.shared.contracts.*

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository:AdminRepository,
    private val mfa:MfaRepository,
    private val clock:Clock,
):ViewModel() {
    private val mutable=MutableStateFlow(AdminUiState())
    val state:StateFlow<AdminUiState> = mutable.asStateFlow()
    private var owner:String?=null
    private var generation=0L
    private var loadJob:Job?=null
    private var actionJob:Job?=null
    private var command:Command?=null
    private var proof:StepUpProof?=null

    private sealed interface Command {
        val target:String
        val operationId:String
        val action:StepUpAction
        val digest:String
        data class Approval(override val target:String,val body:ApproveStaffRequest):Command {
            override val operationId get()=body.operationId
            override val action=StepUpAction.APPROVE_STAFF
            override val digest get()=PrivilegedCommandDigests.staffApproval(target,body)
        }
        data class Lifecycle(override val target:String,val body:AccountLifecycleRequest):Command {
            override val operationId get()=body.operationId
            override val action=StepUpAction.ACCOUNT_LIFECYCLE
            override val digest get()=PrivilegedCommandDigests.accountLifecycle(target,body)
        }
        data class Revocation(override val target:String,val body:RevokeTutorAssignmentRequest):Command {
            override val operationId get()=body.operationId
            override val action=StepUpAction.MANAGE_ACADEMICS
            override val digest get()=PrivilegedCommandDigests.tutorRevocation(target,body)
        }
    }
    init {
        viewModelScope.launch {
            mfa.observeOwner().collect { next ->
                generation++;owner=next;loadJob?.cancel();actionJob?.cancel();command=null;proof=null
                mutable.value=AdminUiState(loading=next!=null,failure=if(next==null) AppError.Http(401) else null,ownerKey=next)
                if(next!=null) refresh()
            }
        }
    }
    private fun current(captured:Long,capturedOwner:String?)=captured==generation && capturedOwner!=null && capturedOwner==owner
    fun refresh() {
        if(owner==null) return
        generation++;loadJob?.cancel();actionJob?.cancel();command=null;proof=null
        val captured=generation;val capturedOwner=owner
        mutable.value=mutable.value.copy(loading=true,working=false,operation=null,failure=null)
        loadJob=viewModelScope.launch {
            val overview=repository.overview()
            if(!current(captured,capturedOwner)) return@launch
            if(overview is Outcome.Failure) {
                mutable.value=AdminUiState(loading=false,failure=overview.error)
                return@launch
            }
            val groups=repository.approvalGroups()
            if(!current(captured,capturedOwner)) return@launch
            val assignments=repository.assignments()
            if(!current(captured,capturedOwner)) return@launch
            mutable.value=mutable.value.copy(loading=false,overview=(overview as Outcome.Success).value,
                groups=(groups as? Outcome.Success)?.value.orEmpty(),groupFailure=(groups as? Outcome.Failure)?.error,
                assignments=(assignments as? Outcome.Success)?.value.orEmpty(),assignmentFailure=(assignments as? Outcome.Failure)?.error)
        }
    }
    private fun ready()=!mutable.value.loading && !mutable.value.working && owner!=null && mutable.value.overview!=null
    private fun invalid() { mutable.value=mutable.value.copy(failure=AppError.Http(422)) }
    private fun prepare(next:Command,name:String) {
        command=next;proof=null
        mutable.value=mutable.value.copy(operation=AdminOperation(next.operationId,next.target,next.action,name),failure=null,completedOperationId=null)
    }
    fun prepareApproval(userId:String,applicationId:String,groupIds:Set<String> = emptySet()) {
        if(!ready()) return
        val target=mutable.value.overview?.users?.find { it.user.id==userId } ?: return invalid()
        val application=target.applications.find { it.id==applicationId && it.status==StaffApplicationStatus.PENDING } ?: return invalid()
        if(!target.user.emailVerified || target.user.accountStatus !in setOf(AccountStatus.PENDING_APPROVAL,AccountStatus.ACTIVE)) return invalid()
        val scopes=mutableListOf<TutorApprovalScope>()
        if(application.role==StaffRole.TUTOR) {
            if(groupIds.isEmpty() || groupIds.size>20) return invalid()
            for(id in groupIds.sorted()) {
                val group=mutable.value.groups.find { it.id==id } ?: return invalid()
                val start=group.startsAtEpochSeconds ?: return invalid()
                val end=group.endsAtEpochSeconds ?: return invalid()
                val period=group.periodId ?: return invalid()
                val validFrom=maxOf(start,clock.instant().epochSecond)
                if(!group.active || group.institutionId!=application.requestedInstitutionId || group.scopeRevision<0 || period.isBlank() || validFrom>=end) return invalid()
                scopes+=TutorApprovalScope(id,period,validFrom,end,group.scopeRevision)
            }
        } else if(groupIds.isNotEmpty()) return invalid()
        prepare(Command.Approval(userId,ApproveStaffRequest(UUID.randomUUID().toString(),target.user.authRevision,applicationId,application.requestedInstitutionId,scopes,"")),target.user.displayName)
    }
    fun prepareLifecycle(userId:String,status:AccountStatus) {
        if(!ready()) return
        val target=mutable.value.overview?.users?.find { it.user.id==userId } ?: return invalid()
        if(target.user.accountStatus==AccountStatus.REVOKED || status !in setOf(AccountStatus.SUSPENDED,AccountStatus.REVOKED)) return invalid()
        prepare(Command.Lifecycle(userId,AccountLifecycleRequest(UUID.randomUUID().toString(),target.user.authRevision,status,"")),target.user.displayName)
    }
    fun prepareRevocation(id:String) {
        if(!ready()) return
        val assignment=mutable.value.assignments.find { it.id==id && it.active } ?: return invalid()
        prepare(Command.Revocation(id,RevokeTutorAssignmentRequest(UUID.randomUUID().toString(),"")),assignment.academicGroupName)
    }
    fun cancelOperation() {
        generation++;actionJob?.cancel();command=null;proof=null
        mutable.value=mutable.value.copy(operation=null,working=false,failure=null)
    }
    fun authorize(password:String,otp:String) {
        if(!ready()) return
        val pending=command ?: return
        val cached=proof?.takeIf { it.expiresAtEpochSeconds>clock.instant().epochSecond }
        if(cached==null && (password.length !in 1..128 || otp.length!=6 || otp.any { it !in '0'..'9' })) return invalid()
        val captured=generation;val capturedOwner=owner
        mutable.value=mutable.value.copy(working=true,failure=null)
        actionJob=viewModelScope.launch {
            val evidence=if(cached!=null) Outcome.Success(cached) else mfa.stepUp(MfaStepUpRequest(password,otp,pending.action,pending.target,pending.operationId,pending.digest))
            if(!current(captured,capturedOwner) || command!==pending) return@launch
            if(evidence is Outcome.Failure) {
                proof=null;mutable.value=mutable.value.copy(working=false,failure=evidence.error)
                return@launch
            }
            val actual=(evidence as Outcome.Success).value
            if(actual.expiresAtEpochSeconds<=clock.instant().epochSecond) {
                proof=null;mutable.value=mutable.value.copy(working=false,failure=AppError.Http(403))
                return@launch
            }
            proof=actual
            val result=when(pending) {
                is Command.Approval -> repository.approve(pending.target,pending.body.copy(proof=actual.proof))
                is Command.Lifecycle -> repository.lifecycle(pending.target,pending.body.copy(proof=actual.proof))
                is Command.Revocation -> repository.revokeAssignment(pending.target,pending.body.copy(proof=actual.proof))
            }
            if(!current(captured,capturedOwner) || command!==pending) return@launch
            when(result) {
                is Outcome.Success -> {
                    command=null;proof=null
                    mutable.value=mutable.value.copy(loading=true,working=false,operation=null,failure=null,completedOperationId=pending.operationId)
                    refresh()
                }
                is Outcome.Failure -> {
                    // Keep evidence only for an uncertain transport/server result of this exact immutable command.
                    if((result.error as? AppError.Http)?.status?.let { it<500 }==true) proof=null
                    mutable.value=mutable.value.copy(working=false,failure=result.error)
                }
            }
        }
    }
}
