package org.companerodeescuela.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.companerodeescuela.core.common.result.*
import org.companerodeescuela.core.network.MfaRepository
import org.companerodeescuela.shared.contracts.*

data class StaffApplicationUiState(val loading:Boolean=true,val working:Boolean=false,val user:UserSummary?=null,
    val applications:List<StaffApplication> = emptyList(),val failure:AppError?=null,val submitted:Boolean=false,val ownerKey:String?=null) {
    val canRequest:Boolean get()=user?.let { it.accountStatus==AccountStatus.ACTIVE && it.emailVerified && !it.institutionId.isNullOrBlank() && UserRole.TEACHER in it.roles && it.roles.none { role -> role in setOf(UserRole.TUTOR,UserRole.ADMIN,UserRole.SUPER_ADMIN) } }==true && applications.none { it.role==StaffRole.TUTOR && it.status==StaffApplicationStatus.PENDING }
}
@HiltViewModel
class StaffApplicationViewModel @Inject constructor(private val repository:AuthRepository,private val ownership:MfaRepository):ViewModel() {
    private val mutable=MutableStateFlow(StaffApplicationUiState())
    val state:StateFlow<StaffApplicationUiState> = mutable.asStateFlow()
    private var owner:String?=null;private var generation=0L
    private var loadJob:Job?=null;private var submitJob:Job?=null
    private var pending:TutorApplicationRequest?=null
    init { viewModelScope.launch { ownership.observeOwner().collect { next ->
        generation++;owner=next;loadJob?.cancel();submitJob?.cancel();pending=null
        mutable.value=StaffApplicationUiState(loading=next!=null,failure=if(next==null) AppError.Http(401) else null,ownerKey=next)
        if(next!=null) refresh()
    } } }
    private fun current(captured:Long,capturedOwner:String?)=captured==generation && capturedOwner!=null && owner==capturedOwner
    fun refresh() {
        if(owner==null) return
        generation++;loadJob?.cancel();submitJob?.cancel();pending=null
        val captured=generation;val capturedOwner=owner
        mutable.value=StaffApplicationUiState(ownerKey=owner)
        loadJob=viewModelScope.launch {
            val profile=repository.currentUser()
            if(!current(captured,capturedOwner)) return@launch
            if(profile is Outcome.Failure) { mutable.value=StaffApplicationUiState(loading=false,ownerKey=capturedOwner,failure=profile.error);return@launch }
            val applications=repository.staffApplications()
            if(!current(captured,capturedOwner)) return@launch
            mutable.value=when(applications) {
                is Outcome.Success -> StaffApplicationUiState(loading=false,ownerKey=capturedOwner,user=(profile as Outcome.Success).value,applications=applications.value)
                is Outcome.Failure -> StaffApplicationUiState(loading=false,ownerKey=capturedOwner,failure=applications.error)
            }
        }
    }
    fun submit(reference:String,preference:String) {
        val snapshot=mutable.value
        if(snapshot.loading || snapshot.working || !snapshot.canRequest) return
        val profile=snapshot.user ?: return
        val institution=profile.institutionId ?: return
        val identity=reference.trim();val group=preference.trim().takeIf { it.isNotEmpty() }
        if(identity.length !in 1..160 || identity.any(Char::isISOControl) || (group!=null && (group.length>120 || group.any(Char::isISOControl)))) {
            mutable.value=snapshot.copy(failure=AppError.Http(422));return
        }
        val previous=pending
        if(previous!=null && (previous.identityReference!=identity || previous.preferredGroup!=group)) {
            mutable.value=snapshot.copy(failure=AppError.Http(409));return
        }
        val request=previous ?: TutorApplicationRequest(UUID.randomUUID().toString(),profile.authRevision,institution,identity,group).also { pending=it }
        val captured=generation;val capturedOwner=owner
        mutable.value=snapshot.copy(working=true,failure=null,submitted=false)
        submitJob=viewModelScope.launch {
            val result=repository.requestTutor(request)
            if(!current(captured,capturedOwner) || pending!==request) return@launch
            mutable.value=when(result) {
                is Outcome.Success -> {
                    pending=null
                    mutable.value.copy(working=false,submitted=true,applications=mutable.value.applications.filterNot { it.id==result.value.id }+result.value,failure=null)
                }
                is Outcome.Failure -> {
                    if((result.error as? AppError.Http)?.status?.let { it<500 }==true) pending=null
                    mutable.value.copy(working=false,failure=result.error)
                }
            }
        }
    }
    fun clearDraft() {
        generation++;submitJob?.cancel();pending=null
        mutable.value=mutable.value.copy(working=false,submitted=false,failure=null)
    }
}
