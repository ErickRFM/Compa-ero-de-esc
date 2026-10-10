package org.companerodeescuela.feature.auth

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.companerodeescuela.shared.contracts.AccountStatus

/** Activity memory only. Sensitive drafts never enter SavedState, preferences or disk. */
class AuthDraftViewModel : ViewModel() {
    val password = mutableStateOf("")
    val confirmation = mutableStateOf("")
    val identityReference = mutableStateOf("")
    val verificationCode = mutableStateOf("")
    private var verificationAccountId: String? = null

    fun clearRegistration() {
        password.value = ""; confirmation.value = ""; identityReference.value = ""
    }
    fun bindVerificationAccount(userId: String?, status: AccountStatus) {
        if (verificationAccountId != userId || status != AccountStatus.PENDING_VERIFICATION) verificationCode.value = ""
        verificationAccountId = userId
    }
    fun clearAll() {
        clearRegistration(); verificationCode.value = ""; verificationAccountId = null
    }
    override fun onCleared() { clearAll() }
}
