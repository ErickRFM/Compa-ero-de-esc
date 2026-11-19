package org.companerodeescuela.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8PageTitle
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.feature.home.R
import org.companerodeescuela.shared.contracts.AdminUserSummary
import org.companerodeescuela.shared.contracts.StaffApplicationStatus
import org.companerodeescuela.shared.contracts.StaffRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun superAdminAccountsScreen(
    onOpenInvitations: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.refresh()
        viewModel.refreshInvitations()
    }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelOperation() } }
    var directoryFilter by rememberSaveable(state.ownerKey) { mutableStateOf(AdminDirectoryFilter.ALL) }
    var linkTarget by remember(state.ownerKey) { mutableStateOf<AdminUserSummary?>(null) }
    var externalReference by remember(state.ownerKey, linkTarget?.user?.id) { mutableStateOf("") }

    Column(
        modifier
            .fillMaxSize()
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        V8ScreenHeader {
            V8PageTitle(
                title = stringResource(R.string.super_admin_accounts_title),
                description = stringResource(R.string.super_admin_accounts_description),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    viewModel.refresh()
                    viewModel.refreshInvitations()
                },
                enabled = !state.working,
                modifier = Modifier.testTag("super_admin_accounts_refresh"),
            ) { Text(stringResource(R.string.admin_refresh)) }
            V8RedPrimaryButton(
                text = stringResource(R.string.admin_invite_action),
                onClick = onOpenInvitations,
                enabled = !state.working,
                modifier = Modifier.weight(1f).testTag("super_admin_accounts_invite"),
            )
        }
        if (state.loading || state.invitationsLoading) CircularProgressIndicator()
        state.failure?.let { adminFailure(it) }
        if (state.completedOperationId != null) {
            StatusNotice(
                title = stringResource(R.string.admin_done_title),
                message = stringResource(R.string.admin_done),
                tone = NoticeTone.SUCCESS,
            )
        }
        Text(stringResource(R.string.super_admin_invitations_title), style = MaterialTheme.typography.titleLarge)
        state.invitationsFailure?.let { adminFailure(it) }
        if (!state.invitationsLoading && state.invitations.isEmpty() && state.invitationsFailure == null) {
            Text(
                stringResource(R.string.super_admin_invitations_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.invitations.forEach { invitation ->
            V8GlassCard(Modifier.fillMaxWidth().testTag("invitation_item_${invitation.invitationId}")) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(invitation.recipient, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${invitationProfileLabel(invitation.profile)} · ${invitationStatusLabel(invitation.status)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${invitation.institutionId} · ${formatLocalDateTime(invitation.createdAtEpochSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (invitation.status == "SENT" || invitation.status == "CREATED") {
                        TextButton(
                            onClick = { viewModel.revokeInvitation(invitation.invitationId) },
                            enabled = !state.working,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.super_admin_invitation_revoke)) }
                    }
                }
            }
        }
        state.overview?.let { overview ->
            val pending =
                overview.users.flatMap { user ->
                    user.applications.filter { it.status == StaffApplicationStatus.PENDING }.map { user to it }
                }
            if (pending.isNotEmpty()) {
                Text(stringResource(R.string.admin_requests), style = MaterialTheme.typography.titleLarge)
                pending.forEach { (user, application) ->
                    V8GlassCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(user.user.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    if (application.role == StaffRole.TEACHER) R.string.admin_teacher else R.string.admin_tutor,
                                ),
                            )
                            Text(stringResource(R.string.admin_identity, application.identityReference))
                            if (application.role == StaffRole.TEACHER) {
                                V8RedPrimaryButton(
                                    text = stringResource(R.string.admin_approve),
                                    onClick = { viewModel.prepareApprovalWithValidity(user.user.id, application.id, emptySet(), null) },
                                    enabled = !state.loading && !state.working && user.user.emailVerified,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            adminUserDirectory(
                users = overview.users,
                ownerKey = state.ownerKey,
                filter = directoryFilter,
                onFilterChange = { directoryFilter = it },
                loading = state.loading,
                working = state.working,
                onLifecycle = viewModel::prepareLifecycle,
                onStudentAffiliation = viewModel::prepareStudentAffiliation,
                onInstitutionalLink = { id -> linkTarget = overview.users.find { it.user.id == id } },
            )
        }
    }

    linkTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { linkTarget = null },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.admin_link_siii)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(target.user.displayName)
                    Text(stringResource(R.string.admin_link_reference_help))
                    OutlinedTextField(
                        externalReference,
                        { externalReference = it.take(32) },
                        label = { Text(stringResource(R.string.admin_link_reference)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.prepareInstitutionalLink(target.user.id, externalReference)
                        linkTarget = null
                    },
                    enabled = externalReference.trim().uppercase(Locale.ROOT).matches(Regex("[A-Z0-9][A-Z0-9_-]{1,31}")),
                ) { Text(stringResource(R.string.admin_link_siii)) }
            },
            dismissButton = { TextButton({ linkTarget = null }) { Text(stringResource(R.string.admin_cancel)) } },
        )
    }
    state.operation?.let { operation ->
        protectedAdminDialog(operation, state.working, state.failure, viewModel::cancelOperation, viewModel::authorize)
    }
}

@Composable
fun superAdminAcademicScreen(
    onOpenSchedule: () -> Unit,
    onOpenClassrooms: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenIntegrations: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelOperation() } }

    Column(
        modifier
            .fillMaxSize()
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        V8ScreenHeader {
            V8PageTitle(
                title = stringResource(R.string.super_admin_academic_title),
                description = stringResource(R.string.super_admin_academic_description),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            superAdminModuleTile(
                title = stringResource(R.string.admin_schedule),
                description = stringResource(R.string.admin_shortcut_schedule),
                tag = "super_admin_academic_schedule",
                onClick = onOpenSchedule,
                modifier = Modifier.weight(1f),
            )
            superAdminModuleTile(
                title = stringResource(R.string.admin_classrooms),
                description = stringResource(R.string.admin_shortcut_classrooms),
                tag = "super_admin_academic_classrooms",
                onClick = onOpenClassrooms,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            superAdminModuleTile(
                title = stringResource(R.string.admin_channel),
                description = stringResource(R.string.admin_shortcut_channel),
                tag = "super_admin_academic_channel",
                onClick = onOpenChannel,
                modifier = Modifier.weight(1f),
            )
            superAdminModuleTile(
                title = stringResource(R.string.super_admin_siii_card_title),
                description = stringResource(R.string.super_admin_siii_card_description),
                tag = "super_admin_academic_siii",
                onClick = onOpenIntegrations,
                modifier = Modifier.weight(1f),
            )
        }
        Text(stringResource(R.string.admin_assignments), style = MaterialTheme.typography.titleLarge)
        state.assignmentFailure?.let { adminFailure(it) }
        if (state.assignments.isEmpty() && state.assignmentFailure == null) {
            Text(stringResource(R.string.admin_empty_assignments), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.assignments.forEach { assignment ->
            V8GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        state.overview
                            ?.users
                            ?.find { it.user.id == assignment.tutorUserId }
                            ?.user
                            ?.displayName ?: assignment.tutorUserId,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(assignment.academicGroupName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(if (assignment.active) R.string.admin_approved else R.string.admin_revoked),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (assignment.active) {
                        TextButton(
                            onClick = { viewModel.prepareRevocation(assignment.id) },
                            enabled = !state.loading && !state.working,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.admin_revoke_assignment)) }
                    }
                }
            }
        }
    }
    state.operation?.let { operation ->
        protectedAdminDialog(operation, state.working, state.failure, viewModel::cancelOperation, viewModel::authorize)
    }
}

private enum class AuditFilter { ALL, INVITATIONS, ACCOUNTS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun superAdminHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refreshAudit() }
    var filter by rememberSaveable(state.ownerKey) { mutableStateOf(AuditFilter.ALL) }
    val filtered =
        state.auditEntries.filter { entry ->
            when (filter) {
                AuditFilter.ALL -> true
                AuditFilter.INVITATIONS -> entry.action.startsWith("INVITATION")
                AuditFilter.ACCOUNTS -> entry.action.startsWith("ACCOUNT") || entry.action.startsWith("STAFF")
            }
        }

    Column(
        modifier
            .fillMaxSize()
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        V8ScreenHeader {
            V8PageTitle(
                title = stringResource(R.string.super_admin_history_title),
                description = stringResource(R.string.super_admin_history_description),
            )
        }
        OutlinedButton(
            onClick = { viewModel.refreshAudit() },
            enabled = !state.auditLoading,
            modifier = Modifier.testTag("super_admin_history_refresh"),
        ) { Text(stringResource(R.string.admin_refresh)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == AuditFilter.ALL,
                onClick = { filter = AuditFilter.ALL },
                label = { Text(stringResource(R.string.super_admin_history_all)) },
            )
            FilterChip(
                selected = filter == AuditFilter.INVITATIONS,
                onClick = { filter = AuditFilter.INVITATIONS },
                label = { Text(stringResource(R.string.super_admin_history_invitations)) },
            )
            FilterChip(
                selected = filter == AuditFilter.ACCOUNTS,
                onClick = { filter = AuditFilter.ACCOUNTS },
                label = { Text(stringResource(R.string.super_admin_history_accounts)) },
            )
        }
        if (state.auditLoading) CircularProgressIndicator(Modifier.testTag("super_admin_history_loading"))
        state.auditFailure?.let { adminFailure(it) }
        if (!state.auditLoading && filtered.isEmpty() && state.auditFailure == null) {
            Text(
                stringResource(R.string.super_admin_history_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("super_admin_history_empty"),
            )
        }
        filtered.forEach { entry ->
            V8GlassCard(Modifier.fillMaxWidth().testTag("audit_entry_${entry.id}")) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        auditActionLabel(entry.action),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${auditOutcomeLabel(entry.outcome)} · ${formatLocalDateTime(entry.createdAtEpochSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (entry.outcome == "FAILED") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    Text(
                        "${entry.resourceType} · ${entry.resourceId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun superAdminModuleTile(
    title: String,
    description: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    V8GlassCard(
        modifier =
            modifier
                .heightIn(min = 84.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(tag),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun invitationProfileLabel(profile: String): String =
    when (profile) {
        "INSTITUTION_ADMIN" -> "Administrador"
        "TEACHER" -> "Docente"
        "TUTOR" -> "Tutor"
        "STUDENT" -> "Alumno"
        else -> profile
    }

private fun invitationStatusLabel(status: String): String =
    when (status) {
        "SENT" -> "Enviada"
        "CREATED" -> "Creada"
        "ACCEPTED" -> "Aceptada"
        "REVOKED" -> "Revocada"
        "EXPIRED" -> "Expirada"
        "FAILED" -> "Fallida"
        else -> status
    }

private fun auditActionLabel(action: String): String =
    when (action) {
        "ACCOUNT_STAFF_APPROVAL" -> "Aprobación de personal"
        "ACCOUNT_STUDENT_AFFILIATION" -> "Afiliación escolar"
        "ACCOUNT_LIFECYCLE" -> "Cambio de estado de cuenta"
        "INVITATION_PREPARE" -> "Preparación de invitación"
        "INVITATION_SEND" -> "Envío de invitación"
        "INVITATION_RETRY" -> "Reenvío de invitación"
        "INVITATION_REVOKE" -> "Revocación de invitación"
        "INVITATION_ACCEPT" -> "Invitación aceptada"
        "INSTITUTION_PROVISION" -> "Alta institucional"
        "STAFF_APPLICATION_TEACHER" -> "Solicitud docente"
        "STAFF_APPLICATION_TUTOR" -> "Solicitud tutoría"
        else -> action.replace('_', ' ').lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }
    }

private fun auditOutcomeLabel(outcome: String): String =
    when (outcome) {
        "SUCCEEDED" -> "Confirmado"
        "STARTED" -> "Iniciado"
        "FAILED" -> "Fallido"
        else -> outcome
    }

private fun formatLocalDateTime(epochSeconds: Long): String =
    DateTimeFormatter
        .ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
