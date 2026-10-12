package org.companerodeescuela.feature.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.feature.home.R
import org.companerodeescuela.shared.contracts.*

@Composable
fun AdminHomeScreen(onOpenSchedule:()->Unit,onOpenChannel:()->Unit,onOpenClassrooms:()->Unit,
    onOpenSecurity:()->Unit,modifier:Modifier=Modifier,viewModel:AdminViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelOperation() } }
    AdminHomeScreen(state,viewModel::refresh,viewModel::prepareApprovalWithValidity,viewModel::prepareLifecycle,
        viewModel::prepareRevocation,viewModel::authorize,viewModel::cancelOperation,onOpenSchedule,onOpenChannel,onOpenClassrooms,onOpenSecurity,modifier)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminHomeScreen(state:AdminUiState,onRefresh:()->Unit,onApprove:(String,String,Set<String>,Int?)->Unit,
    onLifecycle:(String,AccountStatus)->Unit,onRevokeAssignment:(String)->Unit,onAuthorize:(String,String)->Unit,
    onCancel:()->Unit,onOpenSchedule:()->Unit,onOpenChannel:()->Unit,onOpenClassrooms:()->Unit,onOpenSecurity:()->Unit,modifier:Modifier=Modifier) {
    var tutorSelection by remember(state.ownerKey) { mutableStateOf<Pair<AdminUserSummary,StaffApplication>?>(null) }
    Column(modifier.fillMaxSize().widthIn(max=CompaneroSize.homeContentMaxWidth).verticalScroll(rememberScrollState())
        .padding(horizontal=CompaneroSpacing.page,vertical=CompaneroSpacing.sm),verticalArrangement=Arrangement.spacedBy(CompaneroSpacing.section)) {
        V8ScreenHeader { Text(stringResource(R.string.admin_title),style=MaterialTheme.typography.headlineSmall) }
        Text(stringResource(R.string.admin_description),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.loading) CircularProgressIndicator(Modifier.testTag("admin_loading"))
        state.failure?.let { AdminFailure(it) }
        if(state.completedOperationId!=null) Text(stringResource(R.string.admin_done),modifier=Modifier.testTag("admin_done"))
        Button(onRefresh,enabled=!state.working,modifier=Modifier.testTag("admin_refresh")) { Text(stringResource(R.string.admin_refresh)) }
        state.overview?.let { overview ->
            V8GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.testTag("admin_metrics")) {
                    Text(stringResource(R.string.admin_metrics_total,overview.metrics.totalUsers))
                    Text(stringResource(R.string.admin_metrics_pending,overview.metrics.pendingStaff))
                    Text(stringResource(R.string.admin_metrics_teachers,overview.metrics.activeTeachers))
                    Text(stringResource(R.string.admin_metrics_tutors,overview.metrics.activeTutors))
                    Text(stringResource(R.string.admin_metrics_suspended,overview.metrics.suspended))
                }
            }
            Text(stringResource(R.string.admin_requests),style=MaterialTheme.typography.titleLarge)
            val pending=overview.users.flatMap { user -> user.applications.filter { it.status==StaffApplicationStatus.PENDING }.map { user to it } }
            if(pending.isEmpty()) Text(stringResource(R.string.admin_empty_requests))
            pending.forEach { (user,application) ->
                V8GlassCard(Modifier.fillMaxWidth().testTag("admin_request_"+application.id)) {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(user.user.displayName,style=MaterialTheme.typography.titleMedium)
                        Text(stringResource(if(application.role==StaffRole.TEACHER) R.string.admin_teacher else R.string.admin_tutor))
                        Text(stringResource(R.string.admin_identity,application.identityReference))
                        application.preferredGroup?.let { Text(stringResource(R.string.admin_preference,it)) }
                        Button({ if(application.role==StaffRole.TUTOR) tutorSelection=user to application else onApprove(user.user.id,application.id,emptySet(),null) },
                            enabled=!state.loading && !state.working && user.user.emailVerified,modifier=Modifier.fillMaxWidth().testTag("admin_approve_"+application.id)) { Text(stringResource(R.string.admin_approve)) }
                    }
                }
            }
            AdminUserDirectory(
                users = overview.users,
                ownerKey = state.ownerKey,
                loading = state.loading,
                working = state.working,
                onLifecycle = onLifecycle,
            )
            Text(stringResource(R.string.admin_assignments),style=MaterialTheme.typography.titleLarge)
            state.assignmentFailure?.let { AdminFailure(it) }
            if(state.assignments.isEmpty() && state.assignmentFailure==null) Text(stringResource(R.string.admin_empty_assignments))
            state.assignments.forEach { assignment ->
                V8GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(overview.users.find { it.user.id==assignment.tutorUserId }?.user?.displayName ?: assignment.tutorUserId)
                        Text(assignment.academicGroupName,style=MaterialTheme.typography.titleMedium)
                        Text(stringResource(if(assignment.active) R.string.admin_approved else R.string.admin_revoked))
                        if(assignment.active) TextButton({ onRevokeAssignment(assignment.id) },enabled=!state.loading && !state.working) { Text(stringResource(R.string.admin_revoke_assignment)) }
                    }
                }
            }
        }
        Text(stringResource(R.string.admin_tools),style=MaterialTheme.typography.titleLarge)
        V8GlassCard(Modifier.fillMaxWidth()) {
            Column {
                TextButton(onOpenClassrooms) { Text(stringResource(R.string.admin_classrooms)) }
                TextButton(onOpenChannel) { Text(stringResource(R.string.admin_channel)) }
                TextButton(onOpenSchedule) { Text(stringResource(R.string.admin_schedule)) }
                TextButton(onOpenSecurity,modifier=Modifier.testTag("admin_security")) { Text(stringResource(R.string.admin_security)) }
            }
        }
    }
    tutorSelection?.let { (user,application) ->
        TutorApprovalDialog(state.groups.filter { it.institutionId==application.requestedInstitutionId },state.groupFailure,
            { tutorSelection=null },{ ids,days -> tutorSelection=null;onApprove(user.user.id,application.id,ids,days) })
    }
    state.operation?.let { operation ->
        ProtectedAdminDialog(operation,state.working,state.failure,onCancel,onAuthorize)
    }
}

/**
 * Bounded, searchable in-memory directory. The server owns authorization and institutional scope;
 * this component only filters the already-authorized users returned to the current administrator.
 */
@Composable
private fun AdminUserDirectory(
    users: List<AdminUserSummary>,
    ownerKey: String?,
    loading: Boolean,
    working: Boolean,
    onLifecycle: (String, AccountStatus) -> Unit,
) {
    var query by rememberSaveable(ownerKey) { mutableStateOf("") }
    var filter by rememberSaveable(ownerKey) { mutableStateOf(AdminDirectoryFilter.ALL) }
    var requestedPage by rememberSaveable(ownerKey) { mutableIntStateOf(0) }
    val filtered = filterAdminDirectoryUsers(users, query, filter)
    val pageCount = ((filtered.size + ADMIN_DIRECTORY_PAGE_SIZE - 1) / ADMIN_DIRECTORY_PAGE_SIZE).coerceAtLeast(1)
    val page = requestedPage.coerceIn(0, pageCount - 1)
    val visible = adminDirectoryPage(filtered, page, ADMIN_DIRECTORY_PAGE_SIZE)

    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
        Text(stringResource(R.string.admin_users), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(120); requestedPage = 0 },
            label = { Text("Buscar por nombre o correo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("admin_directory_search"),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
        ) {
            listOf(
                AdminDirectoryFilter.ALL to "Todos",
                AdminDirectoryFilter.ACTIVE to "Activos",
                AdminDirectoryFilter.PENDING to "Pendientes",
                AdminDirectoryFilter.SUSPENDED to "Suspendidos",
                AdminDirectoryFilter.REVOKED to "Revocados",
            ).forEach { (option, label) ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option; requestedPage = 0 },
                    label = { Text(label) },
                )
            }
        }
        Text(
            text = "${filtered.size} usuarios encontrados · Página ${page + 1} de $pageCount",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (users.isEmpty()) Text(stringResource(R.string.admin_empty_users))
        else if (filtered.isEmpty()) Text("No hay usuarios que coincidan con la búsqueda y el filtro.")
        visible.forEach { user ->
            V8GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(user.user.displayName, style = MaterialTheme.typography.titleMedium)
                    user.user.email?.let { email ->
                        Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(statusResource(user.user.accountStatus)))
                    if (user.user.accountStatus != AccountStatus.REVOKED) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { onLifecycle(user.user.id, AccountStatus.SUSPENDED) },
                                enabled = !loading && !working && user.user.accountStatus != AccountStatus.SUSPENDED,
                            ) { Text(stringResource(R.string.admin_suspend)) }
                            TextButton(
                                onClick = { onLifecycle(user.user.id, AccountStatus.REVOKED) },
                                enabled = !loading && !working,
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
                TextButton(onClick = { requestedPage = page - 1 }, enabled = page > 0) { Text("Anterior") }
                Text("${page + 1} / $pageCount", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { requestedPage = page + 1 }, enabled = page < pageCount - 1) { Text("Siguiente") }
            }
        }
    }
}

@Composable private fun TutorApprovalDialog(groups:List<AcademicGroupSummary>,failure:AppError?,onDismiss:()->Unit,onConfirm:(Set<String>,Int?)->Unit) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var validityDays by remember { mutableStateOf("") }
    val validDuration=validityDays.isEmpty() || validityDays.toIntOrNull()?.let { it in 1..36500 }==true
    AlertDialog(onDismissRequest=onDismiss,title={ Text(stringResource(R.string.admin_groups)) },text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.admin_groups_description))
            OutlinedTextField(validityDays,{ validityDays=it.filter { char -> char in '0'..'9' }.take(5) },singleLine=true,
                label={ Text(stringResource(R.string.admin_validity_days)) },supportingText={ Text(stringResource(R.string.admin_validity_description)) },
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=!validDuration,modifier=Modifier.fillMaxWidth().testTag("admin_validity_days"))
            failure?.let { AdminFailure(it) }
            if(groups.isEmpty() && failure==null) Text(stringResource(R.string.admin_groups_empty))
            groups.forEach { group ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(group.id in selected,{ checked -> selected=if(checked) selected+group.id else selected-group.id },enabled=group.id in selected || selected.size<20,modifier=Modifier.testTag("admin_group_"+group.id))
                    Column {
                        Text(group.name)
                        Text(stringResource(R.string.admin_period,group.periodId.orEmpty(),date(group.startsAtEpochSeconds),date(group.endsAtEpochSeconds)),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    },confirmButton={ TextButton({ onConfirm(selected,validityDays.toIntOrNull()) },enabled=selected.isNotEmpty() && validDuration,modifier=Modifier.testTag("admin_groups_confirm")) { Text(stringResource(R.string.admin_approve)) } },dismissButton={ TextButton(onDismiss) { Text(stringResource(R.string.admin_cancel)) } })
}
@Composable fun ProtectedAdminDialog(operation:AdminOperation,working:Boolean,failure:AppError?,onDismiss:()->Unit,onConfirm:(String,String)->Unit) {
    var password by remember(operation.operationId) { mutableStateOf("") }
    var otp by remember(operation.operationId) { mutableStateOf("") }
    AlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest={ if(!working) onDismiss() },title={ Text(stringResource(R.string.admin_confirm_title)) },text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.admin_confirm_description,operation.targetName))
            OutlinedTextField(password,{ password=it.take(128) },label={ Text(stringResource(R.string.admin_password)) },singleLine=true,enabled=!working,
                visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth().testTag("admin_password"))
            OutlinedTextField(otp,{ otp=it.filter { char -> char in '0'..'9' }.take(6) },label={ Text(stringResource(R.string.admin_otp)) },singleLine=true,enabled=!working,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth().testTag("admin_otp"))
            if(working) CircularProgressIndicator()
            failure?.let { AdminFailure(it) }
        }
    },confirmButton={ TextButton({ val credential=password;val code=otp;password="";otp="";onConfirm(credential,code) },enabled=!working,
        modifier=Modifier.testTag("admin_confirm")) { Text(stringResource(R.string.admin_confirm)) } },dismissButton={ TextButton(onDismiss,enabled=!working) { Text(stringResource(R.string.admin_cancel)) } })
}
@Composable private fun AdminFailure(error:AppError) {
    val resource=when(error) {
        is AppError.Network -> R.string.admin_error_network
        is AppError.Http -> when(error.status) { 401 -> R.string.admin_error_session;403 -> R.string.admin_error_denied;409 -> R.string.admin_error_conflict;422 -> R.string.admin_error_validation;429 -> R.string.admin_error_rate;in 500..599 -> R.string.admin_error_unavailable;else -> R.string.admin_error_generic }
        else -> R.string.admin_error_generic
    }
    Text(stringResource(resource),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("admin_error"))
}
private fun statusResource(status:AccountStatus)=when(status) { AccountStatus.ACTIVE -> R.string.admin_approved;AccountStatus.PENDING_APPROVAL -> R.string.admin_pending_approval;AccountStatus.PENDING_VERIFICATION -> R.string.admin_pending_verification;AccountStatus.SUSPENDED -> R.string.admin_suspended;AccountStatus.REVOKED -> R.string.admin_revoked }
private fun date(value:Long?):String=value?.let { DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(it)) }.orEmpty()
