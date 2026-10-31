@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.core.navigation.AppDistribution
import org.companerodeescuela.core.navigation.AppExperience

@Composable
fun LoginScreen(
    state: SessionUiState,
    onLogin: (String, String, AppExperience) -> Unit,
    onCreateAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
    distribution: AppDistribution = AppDistribution.LEGACY,
) {
    var username by rememberSaveable { mutableStateOf("") }
    // Passwords never enter saved instance state; paging keeps this one form mounted.
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val accesses = remember(distribution) { loginAccessesFor(distribution) }
    require(accesses.isNotEmpty()) { "No login cards configured for this app" }
    val pager = rememberPagerState(pageCount = { accesses.size })
    val access = accesses[pager.currentPage.coerceIn(0, accesses.lastIndex)]
    val focus = LocalFocusManager.current
    val usernameIssue = InstitutionalCredentialValidator.identifierIssue(username)
    val passwordIssue = InstitutionalCredentialValidator.passwordIssue(password)
    val canSubmit =
        !state.submitting &&
            !pager.isScrollInProgress &&
            usernameIssue == null &&
            passwordIssue == null
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_hero_title),
                color = ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.auth_hero_accent),
                color = accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        LoginAccessCarousel(
            pager = pager,
            enabled = !state.submitting,
            accesses = accesses,
        )

        if (!state.noticeMessage.isNullOrBlank()) {
            V8FrostedGlassPanel(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                themeAware = true,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text =
                            stringResource(
                                if (state.noticeReason == SessionNotice.EXPIRED) {
                                    R.string.auth_notice_title
                                } else {
                                    R.string.auth_notice_connection_title
                                },
                            ),
                        color = ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text =
                            stringResource(
                                if (state.noticeReason == SessionNotice.EXPIRED) {
                                    R.string.auth_notice_expired
                                } else {
                                    R.string.auth_notice_connection
                                },
                            ),
                        color = muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier =
                            Modifier
                                .padding(top = 2.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        V8FrostedGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            themeAware = true,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                Text(
                    text = stringResource(R.string.auth_title),
                    color = ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = InstitutionalCredentialValidator.sanitizeIdentifier(it) },
                    modifier = Modifier.fillMaxWidth().testTag("login_username"),
                    enabled = !state.submitting,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text(stringResource(R.string.auth_identifier)) },
                    isError = username.isNotEmpty() && usernameIssue != null,
                    supportingText =
                        usernameIssue
                            ?.takeIf { username.isNotEmpty() }
                            ?.let { issue -> { Text(stringResource(issue.stringResource)) } },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    colors = institutionalFieldColors(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = InstitutionalCredentialValidator.sanitizePassword(it) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("login_password"),
                    enabled = !state.submitting,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text(stringResource(R.string.auth_password)) },
                    isError = password.isNotEmpty() && passwordIssue != null,
                    supportingText =
                        passwordIssue
                            ?.takeIf { password.isNotEmpty() }
                            ?.let { issue -> { Text(stringResource(issue.stringResource)) } },
                    visualTransformation =
                        if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            enabled = !state.submitting,
                        ) {
                            Icon(
                                imageVector =
                                    if (passwordVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                contentDescription =
                                    stringResource(
                                        if (passwordVisible) {
                                            R.string.auth_hide_password
                                        } else {
                                            R.string.auth_show_password
                                        },
                                    ),
                                tint = muted,
                            )
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = institutionalFieldColors(),
                )

                val displayError = state.errorMessage ?: state.failure?.let { stringResource(loginErrorResource(it)) }
                displayError?.let { msg ->
                    Text(
                        text = msg,
                        color = errorColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                    (state.failure as? AppError.Http)?.requestId?.let { requestId ->
                        Text(
                            text = stringResource(R.string.auth_error_reference, requestId),
                            color = muted.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .testTag("login_error_reference"),
                        )
                    }
                }

                AuthV8PrimaryAction(
                    text = stringResource(R.string.auth_submit),
                    onClick = ::submit,
                    enabled = canSubmit,
                    submitting = state.submitting,
                    modifier = Modifier.padding(top = 16.dp).testTag("login_submit"),
                    submittingText = stringResource(R.string.auth_submitting),
                )

                if (access.canRegister) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = muted.copy(alpha = 0.2f),
                        )
                        Text(
                            text = stringResource(R.string.auth_or),
                            color = muted,
                            fontSize = 12.sp,
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = muted.copy(alpha = 0.2f),
                        )
                    }

                    AuthV8SecondaryAction(
                        text = stringResource(R.string.auth_create_account),
                        onClick = onCreateAccount,
                        enabled = !state.submitting,
                        modifier = Modifier.testTag("login_registration"),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.auth_managed_access),
                        color = muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.auth_footer),
            color = muted.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
        )
    }
}

private val CredentialIssue.stringResource: Int
    get() =
        when (this) {
            CredentialIssue.IDENTIFIER_REQUIRED -> R.string.auth_identifier_required
            CredentialIssue.IDENTIFIER_SHORT -> R.string.auth_identifier_short
            CredentialIssue.IDENTIFIER_INVALID -> R.string.auth_identifier_invalid
            CredentialIssue.IDENTIFIER_EMAIL -> R.string.auth_identifier_email
            CredentialIssue.PASSWORD_REQUIRED -> R.string.auth_password_required
            CredentialIssue.PASSWORD_INVALID -> R.string.auth_password_invalid
        }

internal fun loginErrorResource(error: AppError?): Int =
    when (error) {
        is AppError.Http ->
            when (error.status) {
                400, 422 -> R.string.auth_error_request
                401 -> R.string.auth_error_credentials
                403 -> R.string.auth_error_forbidden
                404 -> R.string.auth_error_endpoint
                408, 504 -> R.string.auth_error_timeout
                409 -> R.string.auth_error_conflict
                429 -> R.string.auth_error_attempts
                in 500..599 -> R.string.auth_error_unavailable
                else -> R.string.auth_error_generic
            }
        is AppError.Network -> R.string.auth_error_network
        is AppError.Storage -> R.string.auth_error_storage
        is AppError.Serialization -> R.string.auth_error_response
        else -> R.string.auth_error_generic
    }
