package org.companerodeescuela.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel

@Composable
fun MfaScreen(onBack:()->Unit,modifier:Modifier=Modifier,viewModel:MfaViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    DisposableEffect(viewModel) { onDispose { viewModel.clearSensitive() } }
    MfaScreen(state,viewModel::enroll,viewModel::confirm,viewModel::refresh,onBack,modifier)
}
@Composable
fun MfaScreen(state:MfaUiState,onEnroll:(String)->Unit,onConfirm:(String)->Unit,onRefresh:()->Unit,onBack:()->Unit,modifier:Modifier=Modifier) {
    var password by remember(state.ownerKey) { mutableStateOf("") }
    var otp by remember(state.ownerKey,state.enrollment?.enrollmentId) { mutableStateOf("") }
    val context=LocalView.current.context
    // Enrollment material and credential entry never enter screenshots or saved state.
    DisposableEffect(context) {
        val window=context.activity()?.window
        val previouslySecure=((window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE)!=0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if(!previouslySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    AuthV8Layout(modifier,themeAware=true) {
        Text(stringResource(R.string.mfa_title),style=MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.mfa_description),modifier=Modifier.padding(vertical=12.dp),color=authMuted())
        V8FrostedGlassPanel(Modifier.fillMaxWidth(),themeAware=true) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(state.loading) CircularProgressIndicator(Modifier.testTag("mfa_loading"))
                state.failure?.let { SecurityFailure(it) }
                when {
                    state.status?.enabled==true -> Text(stringResource(R.string.mfa_enabled),modifier=Modifier.testTag("mfa_enabled"))
                    state.status?.available==false -> Text(stringResource(R.string.mfa_unavailable))
                    state.enrollment!=null -> {
                        Text(stringResource(R.string.mfa_manual))
                        Text(state.enrollment.secret,modifier=Modifier.testTag("mfa_setup_secret"),style=MaterialTheme.typography.bodyLarge)
                        SecurityMemoryField(otp,{ otp=it.filter { char -> char in '0'..'9' }.take(6) },R.string.mfa_otp,"mfa_otp",!state.working,otp=true)
                        AuthV8PrimaryAction(stringResource(R.string.mfa_confirm),{ val code=otp;otp="";onConfirm(code) },otp.length==6 && !state.working,state.working,Modifier.testTag("mfa_confirm"),stringResource(R.string.mfa_working))
                    }
                    state.status?.available==true -> {
                        Text(stringResource(R.string.mfa_disabled))
                        SecurityMemoryField(password,{ password=it.take(128) },R.string.mfa_password,"mfa_password",!state.working,password=true)
                        AuthV8PrimaryAction(stringResource(R.string.mfa_start),{ val credential=password;password="";onEnroll(credential) },password.isNotEmpty() && !state.working,state.working,Modifier.testTag("mfa_enroll"),stringResource(R.string.mfa_working))
                    }
                }
                TextButton(onRefresh,enabled=!state.working) { Text(stringResource(R.string.security_refresh)) }
            }
        }
        TextButton(onBack,modifier=Modifier.fillMaxWidth().testTag("security_back")) { Text(stringResource(R.string.security_back)) }
    }
}
private tailrec fun Context.activity():Activity?=when(this) { is Activity -> this;is ContextWrapper -> baseContext.activity();else -> null }
