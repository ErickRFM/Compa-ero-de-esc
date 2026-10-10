package org.companerodeescuela.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.core.navigation.AppExperience

@Composable
fun LoginScreen(
    state: SessionUiState,
    onLogin: (String, String, AppExperience) -> Unit,
    onCreateAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    // Passwords never enter saved instance state; paging keeps this one form mounted.
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val pager = rememberPagerState(pageCount = { loginAccesses.size })
    val access = loginAccesses[pager.currentPage]
    val focus = LocalFocusManager.current
    val usernameIssue = InstitutionalCredentialValidator.identifierIssue(username)
    val passwordIssue = InstitutionalCredentialValidator.passwordIssue(password)
    val canSubmit = !state.submitting && !pager.isScrollInProgress && usernameIssue == null && passwordIssue == null
    val ink = authInk()
    val muted = authMuted()
    val accent = authAccent()
    val errorColor = MaterialTheme.colorScheme.error
    fun submit() {
        if (!canSubmit) return
        focus.clearFocus()
        onLogin(username, password, access.experience)
    }
    AuthV8Layout(modifier, themeAware = true) {
        Text(stringResource(R.string.auth_hero_title), color = ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.auth_hero_accent), color = accent, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        Text(stringResource(R.string.auth_hero_description), color = muted, fontSize = 15.sp, lineHeight = 21.sp,
            modifier = Modifier.padding(top = 13.dp, bottom = 12.dp))
        LoginAccessCarousel(pager, enabled = !state.submitting)
        if (!state.noticeMessage.isNullOrBlank()) {
            V8FrostedGlassPanel(Modifier.fillMaxWidth().padding(top = 12.dp), themeAware = true) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.auth_notice_title), color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (state.noticeReason == SessionNotice.EXPIRED) R.string.auth_notice_expired else R.string.auth_notice_connection),
                        color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        V8FrostedGlassPanel(Modifier.fillMaxWidth(), themeAware = true) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 22.dp)) {
                Text(stringResource(R.string.auth_title), color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.auth_subtitle), color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp, bottom = 14.dp))
                OutlinedTextField(
                    value = username, onValueChange = { username = InstitutionalCredentialValidator.sanitizeIdentifier(it) },
                    modifier = Modifier.fillMaxWidth().testTag("login_username"), enabled = !state.submitting,
                    singleLine = true, shape = RoundedCornerShape(18.dp), label = { Text(stringResource(R.string.auth_identifier)) },
                    isError = username.isNotEmpty() && usernameIssue != null,
                    supportingText = usernameIssue?.takeIf { username.isNotEmpty() }?.let { issue -> { Text(stringResource(issue.stringResource)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    colors = institutionalFieldColors(),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = InstitutionalCredentialValidator.sanitizePassword(it) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("login_password"), enabled = !state.submitting,
                    singleLine = true, shape = RoundedCornerShape(18.dp), label = { Text(stringResource(R.string.auth_password)) },
                    isError = password.isNotEmpty() && passwordIssue != null,
                    supportingText = passwordIssue?.takeIf { password.isNotEmpty() }?.let { issue -> { Text(stringResource(issue.stringResource)) } },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.submitting) {
                            Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                stringResource(if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password), tint = muted)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }), colors = institutionalFieldColors(),
                )
                state.errorMessage?.let {
                    Text(stringResource(loginErrorResource(state.failure)), color = errorColor, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Assertive })
                }
                AuthV8PrimaryAction(stringResource(R.string.auth_submit), ::submit, canSubmit, state.submitting,
                    Modifier.padding(top = 20.dp).testTag("login_submit"), submittingText = stringResource(R.string.auth_submitting))
                if (access.canRegister) {
                    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        HorizontalDivider(Modifier.weight(1f), color = muted.copy(alpha = 0.3f))
                        Text(stringResource(R.string.auth_or), color = muted, fontSize = 13.sp)
                        HorizontalDivider(Modifier.weight(1f), color = muted.copy(alpha = 0.3f))
                    }
                    AuthV8SecondaryAction(stringResource(R.string.auth_create_account), onCreateAccount, !state.submitting,
                        Modifier.testTag("login_registration"))
                } else {
                    Text(stringResource(R.string.auth_managed_access), color = muted, fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 18.dp))
                }
            }
        }
        Text(stringResource(R.string.auth_footer), color = muted, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp))
    }
}

private val CredentialIssue.stringResource: Int get() = when (this) {
    CredentialIssue.IDENTIFIER_REQUIRED -> R.string.auth_identifier_required
    CredentialIssue.IDENTIFIER_SHORT -> R.string.auth_identifier_short
    CredentialIssue.IDENTIFIER_INVALID -> R.string.auth_identifier_invalid
    CredentialIssue.IDENTIFIER_EMAIL -> R.string.auth_identifier_email
    CredentialIssue.PASSWORD_REQUIRED -> R.string.auth_password_required
    CredentialIssue.PASSWORD_INVALID -> R.string.auth_password_invalid
}

private fun loginErrorResource(error: AppError?): Int = when (error) {
    is AppError.Http -> when (error.status) {
        401 -> R.string.auth_error_credentials
        403 -> R.string.auth_error_forbidden
        429 -> R.string.auth_error_attempts
        in 500..599 -> R.string.auth_error_unavailable
        else -> R.string.auth_error_generic
    }
    is AppError.Network -> R.string.auth_error_network
    is AppError.Storage -> R.string.auth_error_storage
    is AppError.Serialization -> R.string.auth_error_response
    else -> R.string.auth_error_generic
}
