package org.companerodeescuela.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.companerodeescuela.core.network.MfaRepository
import org.companerodeescuela.core.common.result.*
import org.companerodeescuela.shared.contracts.*

data class MfaUiState(val loading:Boolean=true,val working:Boolean=false,val status:MfaStatus?=null,val enrollment:MfaEnrollment?=null,val failure:AppError?=null,val ownerKey:String?=null)
/** Credential inputs live only in the request coroutine; no SavedStateHandle or persistent secret. */
@HiltViewModel
class MfaViewModel @Inject constructor(private val repository:MfaRepository):ViewModel() {
    private val mutable=MutableStateFlow(MfaUiState());val state:StateFlow<MfaUiState> = mutable.asStateFlow()
    private var owner:String?=null;private var generation=0L
    private var statusJob:Job?=null;private var operationJob:Job?=null
    init { viewModelScope.launch { repository.observeOwner().collect { next ->
        generation++;owner=next;statusJob?.cancel();operationJob?.cancel()
        mutable.value=MfaUiState(loading=next!=null,failure=if(next==null) AppError.Http(401) else null,ownerKey=next)
        if(next!=null) refresh()
    } } }
    private fun current(captured:Long,capturedOwner:String?)=captured==generation && capturedOwner!=null && capturedOwner==owner
    fun refresh() {
        val captured=generation;val capturedOwner=owner
        if(capturedOwner==null) return
        statusJob?.cancel();mutable.value=mutable.value.copy(loading=true,failure=null)
        statusJob=viewModelScope.launch {
            val result=repository.status()
            if(!current(captured,capturedOwner)) return@launch
            mutable.value=when(result) {
                is Outcome.Success -> mutable.value.copy(loading=false,status=result.value,enrollment=mutable.value.enrollment.takeIf { result.value.available && !result.value.enabled },failure=null)
                is Outcome.Failure -> mutable.value.copy(loading=false,status=null,enrollment=null,failure=result.error)
            }
        }
    }
    fun enroll(password:String) {
        if(operationJob?.isActive==true) return
        val status=mutable.value.status
        if(password.length !in 1..128) { mutable.value=mutable.value.copy(failure=AppError.Http(422));return }
        if(status==null || !status.available || status.enabled) { mutable.value=mutable.value.copy(failure=AppError.Http(if(status?.enabled==true) 409 else 503));return }
        val captured=generation;val capturedOwner=owner
        if(capturedOwner==null) return
        mutable.value=mutable.value.copy(working=true,failure=null,enrollment=null)
        operationJob=viewModelScope.launch {
            val result=repository.enroll(MfaEnrollmentRequest(password))
            if(!current(captured,capturedOwner)) return@launch
            mutable.value=when(result) {
                is Outcome.Success -> mutable.value.copy(working=false,enrollment=result.value,failure=null)
                is Outcome.Failure -> mutable.value.copy(working=false,enrollment=null,failure=result.error)
            }
        }
    }
    fun confirm(otp:String) {
        if(operationJob?.isActive==true) return
        val enrollment=mutable.value.enrollment
        if(enrollment==null || otp.length!=6 || otp.any { it !in '0'..'9' }) { mutable.value=mutable.value.copy(failure=AppError.Http(422));return }
        val captured=generation;val capturedOwner=owner
        if(capturedOwner==null) return
        mutable.value=mutable.value.copy(working=true,failure=null)
        operationJob=viewModelScope.launch {
            val result=repository.confirm(MfaConfirmationRequest(enrollment.enrollmentId,otp))
            if(!current(captured,capturedOwner)) return@launch
            mutable.value=when(result) {
                is Outcome.Success -> mutable.value.copy(working=false,status=result.value,enrollment=null,failure=null)
                is Outcome.Failure -> mutable.value.copy(working=false,failure=result.error)
            }
        }
    }
    fun clearSensitive() {
        generation++;statusJob?.cancel();operationJob?.cancel()
        mutable.value=mutable.value.copy(loading=false,working=false,enrollment=null,failure=null)
    }
}
