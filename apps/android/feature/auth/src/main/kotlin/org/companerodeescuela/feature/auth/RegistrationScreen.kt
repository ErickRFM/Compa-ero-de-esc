package org.companerodeescuela.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.shared.contracts.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegistrationScreen(state: SessionUiState, onRegister: (RegisterRequest) -> Unit, onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier, onReloadInstitutions: () -> Unit = {}) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var accountType by rememberSaveable { mutableStateOf(RegistrationAccountType.STUDENT) }
    var institutionId by rememberSaveable { mutableStateOf<String?>(null) }
    var identityReference by remember { mutableStateOf("") }
    var preferredGroup by rememberSaveable { mutableStateOf("") }
    var institutionMenu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val academicRequest = accountType == RegistrationAccountType.TEACHER || accountType == RegistrationAccountType.TUTOR
    val validInstitution = state.institutions.any { it.id == institutionId }
    val emailInvalid = email.isNotEmpty() && (InstitutionalCredentialValidator.identifierIssue(email) != null || '@' !in email)
    val passwordInvalid = password.isNotEmpty() && (password.length < 8 || password.any(Char::isISOControl))
    val confirmationInvalid = confirmation.isNotEmpty() && confirmation != password
    val canSubmit = !state.submitting && displayName.trim().length in 2..80 && !displayName.any(Char::isISOControl) &&
        email.isNotEmpty() && !emailInvalid && password.length in 8..256 && !passwordInvalid &&
        confirmation == password && (!academicRequest || (validInstitution && identityReference.isNotBlank()))
    fun submit() {
        if (!canSubmit) return
        focus.clearFocus()
        onRegister(RegisterRequest(displayName.trim(), email.trim().lowercase(), password, accountType,
            requestedInstitutionId = institutionId.takeIf { academicRequest && validInstitution },
            identityReference = identityReference.trim().takeIf { academicRequest },
            preferredGroup = preferredGroup.trim().takeIf { accountType == RegistrationAccountType.TUTOR && it.isNotEmpty() }))
    }
    AuthV8Layout(modifier, themeAware = true) {
        Text(stringResource(R.string.auth_create_account), color = authInk(), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.registration_subtitle), color = authMuted(), fontSize = 15.sp, lineHeight = 21.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 20.dp))
        V8FrostedGlassPanel(Modifier.fillMaxWidth(), emphasized = true, themeAware = true) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.registration_type), color = authInk(), fontWeight = FontWeight.SemiBold)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth >= 280.dp * LocalDensity.current.fontScale) 2 else 1
                    FlowRow(Modifier.fillMaxWidth().selectableGroup(), maxItemsInEachRow = columns,
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RegistrationAccountType.entries.forEach { type ->
                            AccountTypeOption(stringResource(type.labelResource), accountType == type,
                                { accountType = type }, !state.submitting, Modifier.weight(1f).testTag("registration_type_"+type.name))
                        }
                    }
                }
                Text(stringResource(if (academicRequest) R.string.registration_staff_notice else R.string.registration_email_notice),
                    color = authMuted(), fontSize = 12.sp, lineHeight = 18.sp)
                Text(stringResource(R.string.registration_data), color = authInk(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                RegistrationField(displayName, { displayName = it.take(80) }, R.string.registration_name, !state.submitting, "registration_name")
                RegistrationField(email, { email = it.filterNot(Char::isWhitespace).take(128) }, R.string.registration_email,
                    !state.submitting, "registration_email", KeyboardType.Email, error = emailInvalid,
                    errorText = stringResource(R.string.auth_identifier_email))
                RegistrationField(password, { password = it.take(256) }, R.string.auth_password, !state.submitting,
                    "registration_password", KeyboardType.Password, error = passwordInvalid,
                    errorText = stringResource(R.string.registration_password_error))
                RegistrationField(confirmation, { confirmation = it.take(256) }, R.string.registration_confirm, !state.submitting,
                    "registration_confirmation", KeyboardType.Password, ImeAction.Done, confirmationInvalid,
                    stringResource(R.string.registration_confirmation_error), ::submit)
                if (academicRequest) {
                    Text(stringResource(R.string.registration_institution), color = authInk(), fontWeight = FontWeight.SemiBold)
                    when {
                        state.institutionsLoading -> {
                            CircularProgressIndicator(Modifier.size(24.dp), color = authAccent())
                            Text(stringResource(R.string.registration_loading), color = authMuted())
                        }
                        state.institutionsFailure != null -> {
                            Text(stringResource(R.string.registration_directory_error), color = MaterialTheme.colorScheme.error)
                            AuthV8SecondaryAction(stringResource(R.string.registration_retry), onReloadInstitutions, !state.submitting)
                        }
                        state.institutions.isEmpty() -> {
                            Text(stringResource(R.string.registration_directory_empty), color = authMuted())
                            AuthV8SecondaryAction(stringResource(R.string.registration_retry), onReloadInstitutions, !state.submitting)
                        }
                        else -> Box(Modifier.fillMaxWidth()) {
                            AuthV8SecondaryAction(state.institutions.firstOrNull { it.id == institutionId }?.displayName
                                ?: stringResource(R.string.registration_select_institution), { institutionMenu = true }, !state.submitting,
                                Modifier.testTag("registration_institution"))
                            DropdownMenu(institutionMenu, { institutionMenu = false }) {
                                state.institutions.forEach { institution ->
                                    DropdownMenuItem(text = { Text(institution.displayName) }, onClick = { institutionId = institution.id; institutionMenu = false })
                                }
                            }
                        }
                    }
                    RegistrationField(identityReference, { identityReference = it.take(160) }, R.string.registration_identity,
                        !state.submitting, "registration_identity")
                    if (accountType == RegistrationAccountType.TUTOR) {
                        RegistrationField(preferredGroup, { preferredGroup = it.take(128) }, R.string.registration_group,
                            !state.submitting, "registration_group")
                        Text(stringResource(R.string.registration_preference_notice), color = authMuted(), fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                if (state.failure != null) Text(stringResource(registrationErrorResource(state.failure)), color = MaterialTheme.colorScheme.error)
                AuthV8PrimaryAction(stringResource(R.string.auth_create_account), ::submit, canSubmit, state.submitting,
                    Modifier.padding(top = 6.dp).testTag("registration_submit"), stringResource(R.string.registration_submitting))
                AuthV8SecondaryAction(stringResource(R.string.registration_back), onBackToLogin, !state.submitting)
            }
        }
    }
}

@Composable
private fun RegistrationField(value: String, changed: (String) -> Unit, label: Int, enabled: Boolean, tag: String,
    keyboard: KeyboardType = KeyboardType.Text, ime: ImeAction = ImeAction.Next, error: Boolean = false,
    errorText: String? = null, onDone: () -> Unit = {}) {
    OutlinedTextField(value, changed, Modifier.fillMaxWidth().testTag(tag), enabled = enabled, singleLine = true,
        shape = RoundedCornerShape(18.dp), colors = institutionalFieldColors(), label = { Text(stringResource(label)) },
        isError = error, supportingText = if (error && errorText != null) ({ Text(errorText) }) else null,
        visualTransformation = if (keyboard == KeyboardType.Password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ime), keyboardActions = KeyboardActions(onDone = { onDone() }))
}

@Composable
private fun AccountTypeOption(label: String, selected: Boolean, click: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.clip(shape).background(if (selected) authAccent().copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
        .border(1.dp, if (selected) authAccent() else MaterialTheme.colorScheme.outline, shape)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = click).heightIn(min = 56.dp).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RadioButton(selected, null, enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = authAccent(), unselectedColor = authMuted()))
        Text(label, Modifier.weight(1f), color = if (enabled) authInk() else authMuted(), fontWeight = FontWeight.SemiBold)
    }
}
private val RegistrationAccountType.labelResource: Int get() = when (this) {
    RegistrationAccountType.STUDENT -> R.string.auth_role_student
    RegistrationAccountType.TEACHER -> R.string.auth_role_teacher
    RegistrationAccountType.TUTOR -> R.string.auth_role_tutor
    RegistrationAccountType.PARTICIPANT -> R.string.registration_participant
}
internal fun registrationErrorResource(error: org.companerodeescuela.core.common.result.AppError?): Int = when (error) {
    is org.companerodeescuela.core.common.result.AppError.Http -> when (error.status) {
        400, 422 -> R.string.registration_invalid
        409 -> R.string.registration_duplicate
        429 -> R.string.auth_error_attempts
        in 500..599 -> R.string.auth_error_unavailable
        else -> R.string.auth_error_generic
    }
    is org.companerodeescuela.core.common.result.AppError.Network -> R.string.auth_error_network
    else -> R.string.auth_error_generic
}
