package org.companerodeescuela.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8InlineLoading
import org.companerodeescuela.core.designsystem.v8.V8PageTitle
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.feature.home.R
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.AdminUserSummary
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.StaffApplication
import org.companerodeescuela.shared.contracts.StaffApplicationStatus
import org.companerodeescuela.shared.contracts.StaffRole
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun adminHomeScreen(
    onOpenSchedule: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenClassrooms: () -> Unit,
    onOpenSecurity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
    isSuperAdmin: Boolean = false,
    onOpenInvitations: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelOperation() } }
    adminHomeScreenContent(
        state,
        viewModel::refresh,
        viewModel::prepareApprovalWithValidity,
        viewModel::prepareLifecycle,
        viewModel::prepareRevocation,
        viewModel::authorize,
        viewModel::cancelOperation,
        onOpenSchedule,
        onOpenChannel,
        onOpenClassrooms,
        onOpenSecurity,
        modifier,
        onStudentAffiliation = viewModel::prepareStudentAffiliation,
        onInstitutionalLink = viewModel::prepareInstitutionalLink,
        isSuperAdmin = isSuperAdmin,
        onOpenInvitations = onOpenInvitations,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun adminHomeScreenContent(
    state: AdminUiState,
    onRefresh: () -> Unit,
    onApprove: (String, String, Set<String>, Int?) -> Unit,
    onLifecycle: (String, AccountStatus) -> Unit,
    onRevokeAssignment: (String) -> Unit,
    onAuthorize: (String, String) -> Unit,
    onCancel: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenClassrooms: () -> Unit,
    onOpenSecurity: () -> Unit,
    modifier: Modifier = Modifier,
    onStudentAffiliation: (String, String) -> Unit = { _, _ -> },
    onInstitutionalLink: (String, String) -> Unit = { _, _ -> },
    isSuperAdmin: Boolean = false,
    onOpenInvitations: (() -> Unit)? = null,
) {
    var linkTarget by remember(state.ownerKey) { mutableStateOf<AdminUserSummary?>(null) }
    var externalReference by remember(state.ownerKey, linkTarget?.user?.id) { mutableStateOf("") }
    var tutorSelection by remember(state.ownerKey) { mutableStateOf<Pair<AdminUserSummary, StaffApplication>?>(null) }
    var directoryFilter by rememberSaveable(state.ownerKey) { mutableStateOf(AdminDirectoryFilter.ALL) }
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
                title = stringResource(if (isSuperAdmin) R.string.super_admin_title else R.string.admin_title),
                description = stringResource(if (isSuperAdmin) R.string.super_admin_description else R.string.admin_description),
            )
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = !state.working,
            modifier = Modifier.testTag("admin_refresh"),
        ) { Text(stringResource(R.string.admin_refresh)) }
        if (state.loading) V8InlineLoading("Cargando panel administrativo…", Modifier.testTag("admin_loading"))
        state.failure?.let { adminFailure(it) }
        if (state.completedOperationId != null) {
            StatusNotice(
                title = stringResource(R.string.admin_done_title),
                message = stringResource(R.string.admin_done),
                tone = NoticeTone.SUCCESS,
                modifier = Modifier.testTag("admin_done"),
            )
        }
        if (isSuperAdmin && onOpenInvitations != null) {
            V8GlassCard(Modifier.fillMaxWidth(), emphasized = true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.admin_invite_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.admin_invite_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    V8RedPrimaryButton(
                        text = stringResource(R.string.admin_invite_action),
                        onClick = onOpenInvitations,
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth().testTag("admin_invite_action"),
                    )
                }
            }
        }
        Text(stringResource(R.string.admin_tools), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            adminShortcutTile(
                title = stringResource(R.string.admin_schedule),
                tag = "admin_schedule",
                description = stringResource(R.string.admin_shortcut_schedule),
                onClick = onOpenSchedule,
                modifier = Modifier.weight(1f),
            )
            adminShortcutTile(
                title = stringResource(R.string.admin_classrooms),
                tag = "admin_classrooms",
                description = stringResource(R.string.admin_shortcut_classrooms),
                onClick = onOpenClassrooms,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            adminShortcutTile(
                title = stringResource(R.string.admin_channel),
                tag = "admin_channel",
                description = stringResource(R.string.admin_shortcut_channel),
                onClick = onOpenChannel,
                modifier = Modifier.weight(1f),
            )
            adminShortcutTile(
                title = stringResource(R.string.admin_security),
                tag = "admin_security",
                description = stringResource(R.string.admin_shortcut_security),
                onClick = onOpenSecurity,
                modifier = Modifier.weight(1f),
            )
        }
        state.overview?.let { overview ->
            V8GlassCard(Modifier.fillMaxWidth(), emphasized = true) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("admin_metrics")) {
                    Text(
                        stringResource(R.string.admin_metrics_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = directoryFilter == AdminDirectoryFilter.ALL,
                            onClick = { directoryFilter = AdminDirectoryFilter.ALL },
                            label = { Text("${stringResource(R.string.admin_kpi_users)}: ${overview.metrics.totalUsers}") },
                        )
                        FilterChip(
                            selected = directoryFilter == AdminDirectoryFilter.PENDING,
                            onClick = { directoryFilter = AdminDirectoryFilter.PENDING },
                            label = { Text("${stringResource(R.string.admin_kpi_pending)}: ${overview.metrics.pendingStaff}") },
                        )
                        FilterChip(
                            selected = directoryFilter == AdminDirectoryFilter.SUSPENDED,
                            onClick = { directoryFilter = AdminDirectoryFilter.SUSPENDED },
                            label = { Text("${stringResource(R.string.admin_kpi_suspended)}: ${overview.metrics.suspended}") },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            stringResource(R.string.admin_metrics_teachers, overview.metrics.activeTeachers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.admin_metrics_tutors, overview.metrics.activeTutors),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(stringResource(R.string.admin_requests), style = MaterialTheme.typography.titleLarge)
            val pending =
                overview.users.flatMap { user ->
                    user.applications.filter { it.status == StaffApplicationStatus.PENDING }.map {
                        user to
                            it
                    }
                }
            if (pending.isEmpty()) Text(stringResource(R.string.admin_empty_requests))
            pending.forEach { (user, application) ->
                V8GlassCard(Modifier.fillMaxWidth().testTag("admin_request_" + application.id)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(user.user.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (application.role == StaffRole.TEACHER) R.string.admin_teacher else R.string.admin_tutor))
                        Text(stringResource(R.string.admin_identity, application.identityReference))
                        application.preferredGroup?.let { Text(stringResource(R.string.admin_preference, it)) }
                        V8RedPrimaryButton(
                            text = stringResource(R.string.admin_approve),
                            onClick = {
                                if (application.role ==
                                    StaffRole.TUTOR
                                ) {
                                    tutorSelection = user to application
                                } else {
                                    onApprove(user.user.id, application.id, emptySet(), null)
                                }
                            },
                            enabled = !state.loading && !state.working && user.user.emailVerified,
                            modifier =
                                Modifier.fillMaxWidth().testTag(
                                    "admin_approve_" + application.id,
                                ),
                        )
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
                onLifecycle = onLifecycle,
                onStudentAffiliation = onStudentAffiliation,
                onInstitutionalLink = { id -> linkTarget = overview.users.find { it.user.id == id } },
            )
            Text(stringResource(R.string.admin_assignments), style = MaterialTheme.typography.titleLarge)
            state.assignmentFailure?.let { adminFailure(it) }
            if (state.assignments.isEmpty() && state.assignmentFailure == null) Text(stringResource(R.string.admin_empty_assignments))
            state.assignments.forEach { assignment ->
                V8GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            overview.users
                                .find { it.user.id == assignment.tutorUserId }
                                ?.user
                                ?.displayName ?: assignment.tutorUserId,
                        )
                        Text(assignment.academicGroupName, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (assignment.active) R.string.admin_approved else R.string.admin_revoked))
                        if (assignment.active) {
                            TextButton({
                                onRevokeAssignment(assignment.id)
                            }, enabled = !state.loading && !state.working) { Text(stringResource(R.string.admin_revoke_assignment)) }
                        }
                    }
                }
            }
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
                    OutlinedTextField(externalReference, {
                        externalReference = it.take(32)
                    }, label = { Text(stringResource(R.string.admin_link_reference)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onInstitutionalLink(target.user.id, externalReference)
                    linkTarget = null
                }, enabled = externalReference.trim().uppercase(java.util.Locale.ROOT).matches(Regex("[A-Z0-9][A-Z0-9_-]{1,31}"))) {
                    Text(stringResource(R.string.admin_link_siii))
                }
            },
            dismissButton = { TextButton({ linkTarget = null }) { Text(stringResource(R.string.admin_cancel)) } },
        )
    }
    tutorSelection?.let { (user, application) ->
        tutorApprovalDialog(
            state.groups.filter { it.institutionId == application.requestedInstitutionId },
            state.groupFailure,
            { tutorSelection = null },
            { ids, days ->
                tutorSelection = null
                onApprove(user.user.id, application.id, ids, days)
            },
        )
    }
    state.operation?.let { operation ->
        protectedAdminDialog(operation, state.working, state.failure, onCancel, onAuthorize)
    }
}

/** Compact responsive management shortcuts; no duplicate account or schedule authority. */
@Composable
private fun adminShortcutTile(
    title: String,
    description: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    V8GlassCard(
        modifier =
            modifier
                .heightIn(min = 76.dp)
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

/**
 * Bounded, searchable in-memory directory. The server owns authorization and institutional scope;
 * this component only filters the already-authorized users returned to the current administrator.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun adminUserDirectory(
    users: List<AdminUserSummary>,
    ownerKey: String?,
    filter: AdminDirectoryFilter,
    onFilterChange: (AdminDirectoryFilter) -> Unit,
    loading: Boolean,
    working: Boolean,
    onLifecycle: (String, AccountStatus) -> Unit,
    onStudentAffiliation: (String, String) -> Unit,
    onInstitutionalLink: (String) -> Unit,
) {
    var query by rememberSaveable(ownerKey) { mutableStateOf("") }
    var requestedPage by rememberSaveable(ownerKey) { mutableIntStateOf(0) }
    val filtered = filterAdminDirectoryUsers(users, query, filter)
    val pageCount = ((filtered.size + ADMIN_DIRECTORY_PAGE_SIZE - 1) / ADMIN_DIRECTORY_PAGE_SIZE).coerceAtLeast(1)
    val page = requestedPage.coerceIn(0, pageCount - 1)
    val visible = adminDirectoryPage(filtered, page, ADMIN_DIRECTORY_PAGE_SIZE)

    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
        Text(stringResource(R.string.admin_users), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it.take(120)
                requestedPage = 0
            },
            label = { Text(stringResource(R.string.admin_directory_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("admin_directory_search"),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
        ) {
            listOf(
                AdminDirectoryFilter.ALL to R.string.admin_directory_filter_all,
                AdminDirectoryFilter.ACTIVE to R.string.admin_directory_filter_active,
                AdminDirectoryFilter.PENDING to R.string.admin_directory_filter_pending,
                AdminDirectoryFilter.SUSPENDED to R.string.admin_directory_filter_suspended,
                AdminDirectoryFilter.REVOKED to R.string.admin_directory_filter_revoked,
            ).forEach { (option, label) ->
                FilterChip(
                    selected = filter == option,
                    onClick = {
                        onFilterChange(option)
                        requestedPage = 0
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Text(
            text = stringResource(R.string.admin_directory_count, filtered.size, page + 1, pageCount),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (users.isEmpty()) {
            Text(stringResource(R.string.admin_empty_users))
        } else if (filtered.isEmpty()) {
            Text(stringResource(R.string.admin_directory_no_results))
        }
        visible.forEach { user ->
            V8GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(user.user.displayName, style = MaterialTheme.typography.titleMedium)
                    user.user.email?.let { email ->
                        Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val roleLabels =
                        user.user.roles
                            .mapNotNull { roleResource(it) }
                            .map { stringResource(it) }
                    Text(
                        text =
                            buildString {
                                append(stringResource(statusResource(user.user.accountStatus)))
                                if (roleLabels.isNotEmpty()) {
                                    append(" · ")
                                    append(roleLabels.joinToString(", "))
                                }
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (user.user.accountStatus == AccountStatus.REVOKED ||
                                user.user.accountStatus == AccountStatus.SUSPENDED
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    val pendingInstitution = user.requestedInstitutionId
                    if (user.user.emailVerified &&
                        user.user.accountStatus == AccountStatus.ACTIVE &&
                        user.user.roles == setOf(UserRole.STUDENT) &&
                        user.user.institutionId == null &&
                        pendingInstitution != null &&
                        user.user.registrationAccountType == RegistrationAccountType.STUDENT
                    ) {
                        TextButton(onClick = {
                            onStudentAffiliation(user.user.id, pendingInstitution)
                        }, enabled = !loading && !working, modifier = Modifier.testTag("admin_student_affiliation")) {
                            Text(stringResource(R.string.admin_student_affiliation))
                        }
                    }
                    if (user.user.emailVerified &&
                        user.user.accountStatus == AccountStatus.ACTIVE &&
                        user.user.institutionId != null &&
                        user.user.roles.any { it == UserRole.STUDENT || it == UserRole.TEACHER } &&
                        user.user.roles.none { it == UserRole.ADMIN || it == UserRole.SUPER_ADMIN }
                    ) {
                        TextButton(onClick = {
                            onInstitutionalLink(user.user.id)
                        }, enabled = !loading && !working, modifier = Modifier.testTag("admin_institutional_link")) {
                            Text(stringResource(R.string.admin_link_siii))
                        }
                    }
                    if (user.user.accountStatus != AccountStatus.REVOKED) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (user.user.accountStatus == AccountStatus.SUSPENDED) {
                                TextButton(
                                    onClick = { onLifecycle(user.user.id, AccountStatus.ACTIVE) },
                                    enabled = !loading && !working,
                                    modifier = Modifier.testTag("admin_reactivate_${user.user.id}"),
                                ) { Text(stringResource(R.string.admin_reactivate)) }
                            } else {
                                TextButton(
                                    onClick = { onLifecycle(user.user.id, AccountStatus.SUSPENDED) },
                                    enabled = !loading && !working,
                                ) { Text(stringResource(R.string.admin_suspend)) }
                            }
                            TextButton(
                                onClick = { onLifecycle(user.user.id, AccountStatus.REVOKED) },
                                enabled = !loading && !working,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringResource(R.string.admin_revoke)) }
                        }
                    }
                }
            }
        }
        if (pageCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { requestedPage = page - 1 },
                    enabled = page > 0,
                ) { Text(stringResource(R.string.admin_directory_previous)) }
                Text("${page + 1} / $pageCount", style = MaterialTheme.typography.labelLarge)
                TextButton(
                    onClick = { requestedPage = page + 1 },
                    enabled = page < pageCount - 1,
                ) { Text(stringResource(R.string.admin_directory_next)) }
            }
        }
    }
}

@Composable internal fun tutorApprovalDialog(
    groups: List<AcademicGroupSummary>,
    failure: AppError?,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>, Int?) -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var validityDays by remember { mutableStateOf("") }
    val validDuration = validityDays.isEmpty() || validityDays.toIntOrNull()?.let { it in 1..36500 } == true
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.admin_groups)) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.admin_groups_description))
            OutlinedTextField(
                validityDays,
                { validityDays = it.filter { char -> char in '0'..'9' }.take(5) },
                singleLine = true,
                label = {
                    Text(stringResource(R.string.admin_validity_days))
                },
                supportingText = { Text(stringResource(R.string.admin_validity_description)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = !validDuration,
                modifier = Modifier.fillMaxWidth().testTag("admin_validity_days"),
            )
            failure?.let { adminFailure(it) }
            if (groups.isEmpty() && failure == null) Text(stringResource(R.string.admin_groups_empty))
            groups.forEach { group ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        group.id in selected,
                        { checked -> selected = if (checked) selected + group.id else selected - group.id },
                        enabled =
                            group.id in selected || selected.size < 20,
                        modifier = Modifier.testTag("admin_group_" + group.id),
                    )
                    Column {
                        Text(group.name)
                        Text(
                            stringResource(
                                R.string.admin_period,
                                group.periodId.orEmpty(),
                                date(group.startsAtEpochSeconds),
                                date(group.endsAtEpochSeconds),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton({
            onConfirm(selected, validityDays.toIntOrNull())
        }, enabled = selected.isNotEmpty() && validDuration, modifier = Modifier.testTag("admin_groups_confirm")) {
            Text(stringResource(R.string.admin_approve))
        }
    }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.admin_cancel)) } })
}

@Composable fun protectedAdminDialog(
    operation: AdminOperation,
    working: Boolean,
    failure: AppError?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var password by remember(operation.operationId) { mutableStateOf("") }
    var passwordVisible by remember(operation.operationId) { mutableStateOf(false) }
    var otp by remember(operation.operationId) { mutableStateOf("") }
    LaunchedEffect(password) {
        if (password.isEmpty()) passwordVisible = false
    }
    val destructive = operation.kind == AdminOperationKind.REVOKE_ACCOUNT
    AlertDialog(properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn), onDismissRequest = {
        if (!working) onDismiss()
    }, title = { Text(stringResource(R.string.admin_confirm_title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(operationDescriptionResource(operation.kind), operation.targetName),
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                password,
                { password = it.take(128) },
                label = { Text(stringResource(R.string.admin_password)) },
                singleLine = true,
                enabled = !working,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !working,
                        modifier = Modifier.testTag("admin_password_toggle_visibility"),
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription =
                                stringResource(
                                    if (passwordVisible) R.string.admin_hide_password else R.string.admin_show_password,
                                ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("admin_password"),
            )
            OutlinedTextField(
                otp,
                {
                    otp =
                        it
                            .filter { char ->
                                char in '0'..'9'
                            }.take(6)
                },
                label = { Text(stringResource(R.string.admin_otp)) },
                singleLine = true,
                enabled = !working,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().testTag("admin_otp"),
            )
            if (working) CircularProgressIndicator()
            failure?.let { adminFailure(it) }
        }
    }, confirmButton = {
        TextButton(
            {
                val credential = password
                val code = otp
                password = ""
                otp = ""
                onConfirm(credential, code)
            },
            enabled = !working,
            colors =
                if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            modifier = Modifier.testTag("admin_confirm"),
        ) { Text(stringResource(R.string.admin_confirm)) }
    }, dismissButton = { TextButton(onDismiss, enabled = !working) { Text(stringResource(R.string.admin_cancel)) } })
}

@Composable internal fun adminFailure(error: AppError) {
    val resource =
        when (error) {
            is AppError.Network -> R.string.admin_error_network
            is AppError.Http ->
                when (error.status) {
                    401 -> R.string.admin_error_session
                    403 -> R.string.admin_error_denied
                    409 -> R.string.admin_error_conflict
                    400, 422 -> R.string.admin_error_validation
                    429 -> R.string.admin_error_rate
                    in
                    500..599,
                    -> R.string.admin_error_unavailable
                    else -> R.string.admin_error_generic
                }
            else -> R.string.admin_error_generic
        }
    val typedMessage =
        when ((error as? AppError.Http)?.apiCode) {
            "MFA_ENROLLMENT_REQUIRED" -> "Debes activar y confirmar MFA en tu cuenta antes de autorizar esta operación."
            "MFA_REAUTHENTICATION_FAILED" -> "La contraseña de confirmación es incorrecta."
            "MFA_CODE_INVALID" -> "El código MFA es incorrecto o expiró. Genera uno nuevo."
            "MFA_CODE_REUSED" -> "Ese código MFA ya se utilizó. Espera al siguiente código."
            "INVITATION_INSTITUTION_UNAVAILABLE" -> "La institución indicada no está habilitada."
            else -> null
        }
    val baseMessage = typedMessage ?: error.userMessage
    val requestId = (error as? AppError.Http)?.requestId
    StatusNotice(
        title = stringResource(resource),
        message = if (requestId != null) "$baseMessage\nReferencia: $requestId" else baseMessage,
        tone = NoticeTone.ERROR,
        modifier = Modifier.testTag("admin_error"),
    )
}

private fun operationDescriptionResource(kind: AdminOperationKind): Int =
    when (kind) {
        AdminOperationKind.APPROVE_STAFF -> R.string.admin_confirm_approve_staff
        AdminOperationKind.AFFILIATE_STUDENT -> R.string.admin_confirm_affiliate_student
        AdminOperationKind.LINK_SIII -> R.string.admin_confirm_link_siii
        AdminOperationKind.SUSPEND_ACCOUNT -> R.string.admin_confirm_suspend
        AdminOperationKind.REVOKE_ACCOUNT -> R.string.admin_confirm_revoke
        AdminOperationKind.REACTIVATE_ACCOUNT -> R.string.admin_confirm_reactivate
        AdminOperationKind.REVOKE_TUTOR_ASSIGNMENT -> R.string.admin_confirm_revoke_assignment
        AdminOperationKind.RETRY_INVITATION -> R.string.admin_confirm_retry_invitation
        AdminOperationKind.AUTHORIZE_INVITATION -> R.string.admin_confirm_authorize_invitation
        AdminOperationKind.OTHER -> R.string.admin_confirm_description
    }

private fun roleResource(role: UserRole): Int? =
    when (role) {
        UserRole.SUPER_ADMIN -> R.string.admin_role_super_admin
        UserRole.ADMIN -> R.string.admin_role_admin
        UserRole.COORDINATOR -> R.string.admin_role_coordinator
        UserRole.PRINCIPAL -> R.string.admin_role_principal
        UserRole.TEACHER -> R.string.admin_role_teacher
        UserRole.TEACHER_PENDING -> R.string.admin_role_teacher_pending
        UserRole.TUTOR -> R.string.admin_role_tutor
        UserRole.STUDENT -> R.string.admin_role_student
        else -> null
    }

private fun statusResource(status: AccountStatus) =
    when (status) {
        AccountStatus.ACTIVE -> R.string.admin_approved
        AccountStatus.PENDING_APPROVAL -> R.string.admin_pending_approval
        AccountStatus.PENDING_VERIFICATION -> R.string.admin_pending_verification
        AccountStatus.SUSPENDED -> R.string.admin_suspended
        AccountStatus.REVOKED -> R.string.admin_revoked
    }

private fun date(value: Long?): String =
    value
        ?.let {
            DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(it))
        }.orEmpty()
