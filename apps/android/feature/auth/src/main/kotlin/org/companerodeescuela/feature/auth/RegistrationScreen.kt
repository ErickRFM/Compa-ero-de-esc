@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.core.designsystem.v8.V8RedDimensions
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.*

private val PUBLIC_ACCOUNT_TYPES =
    listOf(
        RegistrationAccountType.STUDENT,
        RegistrationAccountType.TEACHER,
        RegistrationAccountType.TUTOR,
        RegistrationAccountType.PARTICIPANT,
    )

@Composable
fun RegistrationScreen(
    state: SessionUiState,
    onRegister: (RegisterRequest) -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    onReloadInstitutions: () -> Unit = {},
    initialInviteMode: Boolean = false,
    draft: AuthDraftViewModel = viewModel(),
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by draft.password
    var confirmation by draft.confirmation
    var inviteMode by rememberSaveable(initialInviteMode) { mutableStateOf(initialInviteMode) }
    var ownerBootstrapMode by rememberSaveable { mutableStateOf(false) }
    var standardAccountType by rememberSaveable { mutableStateOf(RegistrationAccountType.STUDENT) }
    var institutionId by rememberSaveable { mutableStateOf<String?>(null) }
    var identityReference by draft.identityReference
    var preferredGroup by rememberSaveable { mutableStateOf("") }
    var ownerInvitationCode by remember { mutableStateOf("") }
    var invitationKeyInput by remember { mutableStateOf("") }
    var invitationId by remember { mutableStateOf("") }
    var invitationCode by remember { mutableStateOf("") }
    var separateInvitationFields by remember { mutableStateOf(false) }
    var institutionMenu by remember { mutableStateOf(false) }

    val focus = LocalFocusManager.current
    val effectiveAccountType =
        when {
            inviteMode -> RegistrationAccountType.INVITED
            ownerBootstrapMode -> RegistrationAccountType.OWNER_INVITED
            else -> standardAccountType
        }
    val academicRequest =
        effectiveAccountType == RegistrationAccountType.TEACHER ||
            effectiveAccountType == RegistrationAccountType.TUTOR
    val validInstitution = state.institutions.any { it.id == institutionId }
    val emailInvalid =
        email.isNotEmpty() &&
            (InstitutionalCredentialValidator.identifierIssue(email) != null || '@' !in email)
    val passwordInvalid =
        password.isNotEmpty() &&
            (password.length < 8 || password.any(Char::isISOControl))
    val confirmationInvalid = confirmation.isNotEmpty() && confirmation != password
    val validInviteCredentials =
        InvitationCredentialParser.isValidPart(invitationId) &&
            InvitationCredentialParser.isValidPart(invitationCode)
    val canSubmit =
        !state.submitting &&
            displayName.trim().length in 2..80 &&
            !displayName.any(Char::isISOControl) &&
            email.isNotEmpty() &&
            !emailInvalid &&
            password.length in 8..256 &&
            !passwordInvalid &&
            confirmation == password &&
            (!academicRequest || (validInstitution && identityReference.isNotBlank())) &&
            (
                effectiveAccountType != RegistrationAccountType.OWNER_INVITED ||
                    InvitationCredentialParser.isValidPart(ownerInvitationCode)
            ) &&
            (effectiveAccountType != RegistrationAccountType.INVITED || validInviteCredentials)

    LaunchedEffect(inviteMode, academicRequest) {
        if (!inviteMode && academicRequest && state.institutions.isEmpty() && !state.institutionsLoading) {
            onReloadInstitutions()
        }
    }

    fun submit() {
        if (!canSubmit) return
        focus.clearFocus()
        val cleanedReference =
            identityReference
                .trim()
                .takeIf { it.isNotEmpty() && (academicRequest || effectiveAccountType == RegistrationAccountType.INVITED) }
        onRegister(
            RegisterRequest(
                displayName = displayName.trim(),
                email = email.trim().lowercase(),
                password = password,
                accountType = effectiveAccountType,
                requestedInstitutionId = institutionId.takeIf { academicRequest && validInstitution },
                identityReference = cleanedReference,
                preferredGroup = preferredGroup.trim().takeIf { effectiveAccountType == RegistrationAccountType.TUTOR && it.isNotEmpty() },
                ownerInvitationCode = ownerInvitationCode.takeIf { effectiveAccountType == RegistrationAccountType.OWNER_INVITED },
                invitationId = invitationId.trim().takeIf { effectiveAccountType == RegistrationAccountType.INVITED },
                invitationCode = invitationCode.trim().takeIf { effectiveAccountType == RegistrationAccountType.INVITED },
            ),
        )
    }

    AuthV8Layout(modifier, themeAware = true) {
        AuthWelcomeHero(
            title =
                stringResource(
                    if (inviteMode) R.string.registration_invited_title else R.string.auth_create_account,
                ),
        )
        V8FrostedGlassPanel(Modifier.fillMaxWidth(), emphasized = true, themeAware = true) {
            Column(
                Modifier.padding(horizontal = V8RedDimensions.ScreenHorizontal, vertical = CompaneroSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RegistrationModeTab(
                        label = stringResource(R.string.registration_mode_standard),
                        selected = !inviteMode,
                        enabled = !state.submitting,
                        onClick = {
                            inviteMode = false
                        },
                        modifier = Modifier.weight(1f).testTag("registration_mode_standard"),
                    )
                    RegistrationModeTab(
                        label = stringResource(R.string.registration_mode_invited),
                        selected = inviteMode,
                        enabled = !state.submitting,
                        onClick = {
                            inviteMode = true
                            ownerBootstrapMode = false
                        },
                        modifier = Modifier.weight(1f).testTag("registration_mode_invited"),
                    )
                }

                if (inviteMode) {
                    Text(
                        text = stringResource(R.string.registration_invited_notice),
                        color = authMuted(),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    SmartInvitationKeySection(
                        keyInput = invitationKeyInput,
                        onKeyInputChange = { raw ->
                            invitationKeyInput = raw.take(512)
                            InvitationCredentialParser.parse(raw)?.let { parsed ->
                                invitationId = parsed.invitationId
                                invitationCode = parsed.code
                            }
                        },
                        invitationId = invitationId,
                        onInvitationIdChange = { invitationId = it.filterNot(Char::isWhitespace).take(43) },
                        invitationCode = invitationCode,
                        onInvitationCodeChange = { invitationCode = it.filterNot(Char::isWhitespace).take(43) },
                        separateFields = separateInvitationFields,
                        onToggleSeparateFields = { separateInvitationFields = !separateInvitationFields },
                        enabled = !state.submitting,
                        tagPrefix = "registration_invitation",
                    )
                } else if (!ownerBootstrapMode) {
                    Text(
                        stringResource(R.string.registration_type),
                        color = authInk(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columns = if (maxWidth >= 250.dp * LocalDensity.current.fontScale) 2 else 1
                        Column(
                            Modifier.fillMaxWidth().selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PUBLIC_ACCOUNT_TYPES.chunked(columns).forEach { rowTypes ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowTypes.forEach { type ->
                                        AccountTypeOption(
                                            type = type,
                                            label = stringResource(type.labelResource),
                                            selected = standardAccountType == type,
                                            click = { standardAccountType = type },
                                            enabled = !state.submitting,
                                            modifier = Modifier.weight(1f).testTag("registration_type_" + type.name),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(if (academicRequest) R.string.registration_staff_notice else R.string.registration_email_notice),
                        color = authMuted(),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                } else {
                    Text(
                        stringResource(R.string.registration_owner_invited),
                        color = authInk(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Text(
                    stringResource(R.string.registration_data),
                    color = authInk(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                RegistrationField(
                    displayName,
                    { displayName = it.take(80) },
                    R.string.registration_name,
                    !state.submitting,
                    "registration_name",
                )
                RegistrationField(
                    email,
                    { email = it.filterNot(Char::isWhitespace).take(128) },
                    R.string.registration_email,
                    !state.submitting,
                    "registration_email",
                    KeyboardType.Email,
                    error = emailInvalid,
                    errorText = stringResource(R.string.auth_identifier_email),
                )
                RegistrationField(
                    password,
                    { password = it.take(256) },
                    R.string.auth_password,
                    !state.submitting,
                    "registration_password",
                    KeyboardType.Password,
                    error = passwordInvalid,
                    errorText = stringResource(R.string.registration_password_error),
                )
                RegistrationField(
                    confirmation,
                    { confirmation = it.take(256) },
                    R.string.registration_confirm,
                    !state.submitting,
                    "registration_confirmation",
                    KeyboardType.Password,
                    if (inviteMode || academicRequest || ownerBootstrapMode) ImeAction.Next else ImeAction.Done,
                    confirmationInvalid,
                    stringResource(R.string.registration_confirmation_error),
                    ::submit,
                )

                if (inviteMode) {
                    RegistrationField(
                        value = identityReference,
                        changed = { identityReference = it.take(160) },
                        label = R.string.registration_siii_optional,
                        enabled = !state.submitting,
                        tag = "registration_siii_reference",
                        ime = ImeAction.Done,
                        onDone = ::submit,
                        leadingBadge = true,
                    )
                    Text(
                        stringResource(R.string.registration_siii_optional_help),
                        color = authMuted(),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }

                if (effectiveAccountType == RegistrationAccountType.OWNER_INVITED) {
                    RegistrationField(
                        ownerInvitationCode,
                        { ownerInvitationCode = it.trim().take(43) },
                        R.string.registration_owner_invitation_code,
                        !state.submitting,
                        "registration_owner_invitation_code",
                        KeyboardType.Password,
                        ImeAction.Done,
                        onDone = ::submit,
                    )
                }

                if (academicRequest) {
                    Text(stringResource(R.string.registration_institution), color = authInk(), fontWeight = FontWeight.SemiBold)
                    when {
                        state.institutionsLoading -> {
                            CircularProgressIndicator(Modifier.size(24.dp), color = authAccent())
                            Text(stringResource(R.string.registration_loading), color = authMuted())
                        }
                        state.institutionsFailure != null -> {
                            StatusNotice(
                                title = stringResource(R.string.registration_directory_error),
                                message = state.institutionsFailure.userMessage,
                                tone = NoticeTone.ERROR,
                            )
                            AuthV8SecondaryAction(stringResource(R.string.registration_retry), onReloadInstitutions, !state.submitting)
                        }
                        state.institutions.isEmpty() -> {
                            StatusNotice(
                                title = stringResource(R.string.registration_directory_empty),
                                message = stringResource(R.string.registration_retry),
                                tone = NoticeTone.WARNING,
                            )
                            AuthV8SecondaryAction(stringResource(R.string.registration_retry), onReloadInstitutions, !state.submitting)
                        }
                        else ->
                            Box(Modifier.fillMaxWidth()) {
                                AuthV8SecondaryAction(
                                    state.institutions.firstOrNull { it.id == institutionId }?.displayName
                                        ?: stringResource(R.string.registration_select_institution),
                                    { institutionMenu = true },
                                    !state.submitting,
                                    Modifier.testTag("registration_institution"),
                                )
                                DropdownMenu(institutionMenu, { institutionMenu = false }) {
                                    state.institutions.forEach { institution ->
                                        DropdownMenuItem(text = { Text(institution.displayName) }, onClick = {
                                            institutionId = institution.id
                                            institutionMenu = false
                                        })
                                    }
                                }
                            }
                    }
                    RegistrationField(
                        value = identityReference,
                        changed = { identityReference = it.take(160) },
                        label = R.string.registration_identity,
                        enabled = !state.submitting,
                        tag = "registration_identity",
                        leadingBadge = true,
                    )
                    Text(
                        stringResource(R.string.registration_identity_help),
                        color = authMuted(),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    if (effectiveAccountType == RegistrationAccountType.TUTOR) {
                        RegistrationField(
                            preferredGroup,
                            { preferredGroup = it.take(128) },
                            R.string.registration_group,
                            !state.submitting,
                            "registration_group",
                            ime = ImeAction.Done,
                            onDone = ::submit,
                        )
                        Text(
                            stringResource(R.string.registration_preference_notice),
                            color = authMuted(),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                if (state.failure != null) {
                    StatusNotice(
                        title = stringResource(registrationErrorResource(state.failure)),
                        message = state.failure.userMessage.ifBlank { stringResource(R.string.auth_error_generic) },
                        tone = NoticeTone.ERROR,
                    )
                }
                AuthV8PrimaryAction(
                    stringResource(if (inviteMode) R.string.registration_invited_title else R.string.auth_create_account),
                    ::submit,
                    canSubmit,
                    state.submitting,
                    Modifier.padding(top = 6.dp).testTag("registration_submit"),
                    stringResource(R.string.registration_submitting),
                )
                AuthV8SecondaryAction(stringResource(R.string.registration_back), {
                    draft.clearRegistration()
                    onBackToLogin()
                }, !state.submitting)

                if (!inviteMode) {
                    TextButton(
                        onClick = { ownerBootstrapMode = !ownerBootstrapMode },
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth().testTag("registration_type_OWNER_INVITED"),
                    ) {
                        Text(
                            stringResource(
                                if (ownerBootstrapMode) {
                                    R.string.registration_owner_cancel
                                } else {
                                    R.string.registration_owner_toggle
                                },
                            ),
                            color = authMuted(),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SmartInvitationKeySection(
    keyInput: String,
    onKeyInputChange: (String) -> Unit,
    invitationId: String,
    onInvitationIdChange: (String) -> Unit,
    invitationCode: String,
    onInvitationCodeChange: (String) -> Unit,
    separateFields: Boolean,
    onToggleSeparateFields: () -> Unit,
    enabled: Boolean,
    tagPrefix: String,
) {
    val clipboard = LocalClipboardManager.current
    val ready =
        InvitationCredentialParser.isValidPart(invitationId) &&
            InvitationCredentialParser.isValidPart(invitationCode)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!separateFields) {
            OutlinedTextField(
                value = keyInput,
                onValueChange = onKeyInputChange,
                modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_key"),
                enabled = enabled,
                maxLines = 3,
                shape = RoundedCornerShape(V8RedDimensions.FieldCorner),
                colors = institutionalFieldColors(),
                label = { Text(stringResource(R.string.registration_invitation_key)) },
                trailingIcon = {
                    TextButton(
                        onClick = {
                            val clipText = clipboard.getText()?.text.orEmpty()
                            if (clipText.isNotBlank()) {
                                onKeyInputChange(clipText)
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.testTag("${tagPrefix}_paste"),
                    ) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.registration_invitation_paste), fontSize = 12.sp)
                    }
                },
            )
        } else {
            OutlinedTextField(
                value = invitationId,
                onValueChange = onInvitationIdChange,
                modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_id"),
                enabled = enabled,
                singleLine = true,
                shape = RoundedCornerShape(V8RedDimensions.FieldCorner),
                colors = institutionalFieldColors(),
                label = { Text(stringResource(R.string.registration_invitation_id)) },
            )
            OutlinedTextField(
                value = invitationCode,
                onValueChange = onInvitationCodeChange,
                modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_code"),
                enabled = enabled,
                singleLine = true,
                shape = RoundedCornerShape(V8RedDimensions.FieldCorner),
                colors = institutionalFieldColors(),
                label = { Text(stringResource(R.string.registration_invitation_code)) },
            )
        }
        if (ready) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("${tagPrefix}_ready"),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = authAccent(),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.registration_invitation_detected),
                    color = authInk(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        TextButton(
            onClick = onToggleSeparateFields,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.testTag("${tagPrefix}_separate_toggle"),
        ) {
            Text(
                stringResource(
                    if (separateFields) {
                        R.string.registration_invitation_hide_separate
                    } else {
                        R.string.registration_invitation_separate_toggle
                    },
                ),
                color = authAccent(),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun RegistrationModeTab(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(V8RedDimensions.FieldCorner)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(if (selected) authAccent() else MaterialTheme.colorScheme.surface)
                .border(1.dp, if (selected) authAccent() else MaterialTheme.colorScheme.outline, shape)
                .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
                .heightIn(min = 44.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) androidx.compose.ui.graphics.Color.White else authInk(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun RegistrationField(
    value: String,
    changed: (String) -> Unit,
    label: Int,
    enabled: Boolean,
    tag: String,
    keyboard: KeyboardType = KeyboardType.Text,
    ime: ImeAction = ImeAction.Next,
    error: Boolean = false,
    errorText: String? = null,
    onDone: () -> Unit = {},
    leadingBadge: Boolean = false,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val isPassword = keyboard == KeyboardType.Password
    OutlinedTextField(
        value,
        changed,
        Modifier.fillMaxWidth().testTag(tag),
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(V8RedDimensions.FieldCorner),
        colors = institutionalFieldColors(),
        label = { Text(stringResource(label)) },
        leadingIcon = {
            Icon(
                when {
                    isPassword -> Icons.Filled.Lock
                    keyboard == KeyboardType.Email -> Icons.Filled.Email
                    leadingBadge -> Icons.Filled.Badge
                    else -> Icons.Filled.Person
                },
                contentDescription = null,
            )
        },
        trailingIcon =
            if (isPassword) {
                (
                    {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = enabled) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription =
                                    stringResource(
                                        if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                                    ),
                            )
                        }
                    }
                )
            } else {
                null
            },
        isError = error,
        supportingText = if (error && errorText != null) ({ Text(errorText) }) else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ime),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
private fun AccountTypeOption(
    type: RegistrationAccountType,
    label: String,
    selected: Boolean,
    click: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(V8RedDimensions.FieldCorner)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) authAccent().copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) authAccent() else MaterialTheme.colorScheme.outline, shape)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = click)
            .heightIn(min = 52.dp)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            when (type) {
                RegistrationAccountType.STUDENT -> Icons.Filled.School
                RegistrationAccountType.TEACHER -> Icons.Filled.MenuBook
                RegistrationAccountType.TUTOR -> Icons.Filled.Group
                RegistrationAccountType.PARTICIPANT -> Icons.Filled.Person
                RegistrationAccountType.OWNER_INVITED -> Icons.Filled.Lock
                RegistrationAccountType.INVITED -> Icons.Filled.VerifiedUser
            },
            contentDescription = null,
            tint = if (selected) authAccent() else authInk(),
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            Modifier.weight(1f),
            color = if (enabled) authInk() else authMuted(),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) authAccent() else authMuted(),
            modifier = Modifier.size(16.dp),
        )
    }
}

private val RegistrationAccountType.labelResource: Int get() =
    when (this) {
        RegistrationAccountType.STUDENT -> R.string.auth_role_student
        RegistrationAccountType.TEACHER -> R.string.auth_role_teacher
        RegistrationAccountType.TUTOR -> R.string.auth_role_tutor
        RegistrationAccountType.PARTICIPANT -> R.string.registration_participant
        RegistrationAccountType.OWNER_INVITED -> R.string.registration_owner_invited
        RegistrationAccountType.INVITED -> R.string.registration_invited_type
    }

internal fun registrationErrorResource(error: org.companerodeescuela.core.common.result.AppError?): Int =
    when (error) {
        is org.companerodeescuela.core.common.result.AppError.Http ->
            when (error.status) {
                400, 422 -> R.string.registration_invalid
                409 -> R.string.registration_duplicate
                429 -> R.string.auth_error_attempts
                in 500..599 -> R.string.auth_error_unavailable
                else -> R.string.auth_error_generic
            }
        is org.companerodeescuela.core.common.result.AppError.Network -> R.string.auth_error_network
        else -> R.string.auth_error_generic
    }
