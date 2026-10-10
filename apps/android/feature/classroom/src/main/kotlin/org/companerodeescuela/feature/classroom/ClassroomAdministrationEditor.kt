package org.companerodeescuela.feature.classroom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.feature.admin.AdminOperation
import org.companerodeescuela.feature.admin.ProtectedAdminDialog
import org.companerodeescuela.shared.contracts.*

/** Administrative body of ClassroomScreen; existing teacher/student bodies and enrollment cards are reused. */
@Composable
fun ClassroomAdministrationEditor(onOpenChannel:()->Unit,modifier:Modifier=Modifier,viewModel:ClassroomAdministrationViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelOperation() } }
    ClassroomAdministrationEditor(state,viewModel::refresh,viewModel::prepareGroup,viewModel::prepareMember,viewModel::prepareClass,viewModel::prepareSchedule,
        viewModel::authorize,viewModel::cancelOperation,viewModel::generateInvite,viewModel::revokeInvite,onOpenChannel,modifier)
}
@Composable
fun ClassroomAdministrationEditor(state:ClassroomAdministrationState,onRefresh:()->Unit,
    onGroup:(String,String,String,String,Long,Long)->Unit,onMember:(String,String)->Unit,onClass:(String,String,String,String)->Unit,
    onSchedule:(String,String,String,String)->Unit,onAuthorize:(String,String)->Unit,onCancel:()->Unit,
    onInvite:(String)->Unit,onRevokeInvite:()->Unit,onOpenChannel:()->Unit,modifier:Modifier=Modifier) {
    var groupId by remember(state.ownerKey) { mutableStateOf("") };var groupName by remember(state.ownerKey) { mutableStateOf("") }
    var institution by remember(state.ownerKey) { mutableStateOf("") };var period by remember(state.ownerKey) { mutableStateOf("") }
    var starts by remember(state.ownerKey) { mutableStateOf("") };var ends by remember(state.ownerKey) { mutableStateOf("") }
    var selectedGroup by remember(state.ownerKey) { mutableStateOf("") };var student by remember(state.ownerKey) { mutableStateOf("") }
    var teacher by remember(state.ownerKey) { mutableStateOf("") };var subject by remember(state.ownerKey) { mutableStateOf("") };var room by remember(state.ownerKey) { mutableStateOf("") }
    var selectedClass by remember(state.ownerKey) { mutableStateOf("") };var day by remember(state.ownerKey) { mutableStateOf("") }
    var fromTime by remember(state.ownerKey) { mutableStateOf("") };var untilTime by remember(state.ownerKey) { mutableStateOf("") }
    var invalidDate by remember(state.ownerKey) { mutableStateOf(false) }
    val enabled=!state.loading && !state.working && state.operationId==null && state.actor!=null
    val context=LocalContext.current
    val scopedGroup=state.groups.find { it.id==selectedGroup }
    val knownGroups=state.groups.filter { it.active && it.institutionId!=null && !it.periodId.isNullOrBlank() && it.endsAtEpochSeconds!=null && it.scopeRevision>=0 }
    val institutions=state.institutions.filter { UserRole.SUPER_ADMIN in state.actor?.roles.orEmpty() || it.id==state.actor?.institutionId }
    Column(modifier.fillMaxSize().widthIn(max=CompaneroSize.homeContentMaxWidth).verticalScroll(rememberScrollState())
        .padding(horizontal=CompaneroSpacing.page,vertical=CompaneroSpacing.sm),verticalArrangement=Arrangement.spacedBy(CompaneroSpacing.section)) {
        V8ScreenHeader { Text(stringResource(R.string.academic_admin_title),style=MaterialTheme.typography.headlineSmall) }
        Text(stringResource(R.string.academic_admin_description),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.loading) CircularProgressIndicator(Modifier.testTag("academic_admin_loading"))
        state.failure?.let { AcademicFailure(it) }
        if(state.completed) Text(stringResource(R.string.academic_admin_done),modifier=Modifier.testTag("academic_admin_done"))
        Button(onRefresh,enabled=!state.working) { Text(stringResource(R.string.academic_admin_refresh)) }
        if(state.actor!=null) {
            AcademicCard(R.string.academic_admin_group_create) {
                AcademicField(groupId,{ groupId=it.take(128) },R.string.academic_admin_group_id,"academic_group_id",enabled)
                AcademicField(groupName,{ groupName=it.take(120) },R.string.academic_admin_group_name,"academic_group_name",enabled)
                AcademicPicker(R.string.academic_admin_institution,institutions.map { it.id to it.displayName },institution,{ institution=it },"academic_institution",enabled)
                AcademicField(period,{ period=it.take(128) },R.string.academic_admin_period,"academic_period",enabled)
                AcademicField(starts,{ starts=it.take(10);invalidDate=false },R.string.academic_admin_start,"academic_start",enabled)
                AcademicField(ends,{ ends=it.take(10);invalidDate=false },R.string.academic_admin_end,"academic_end",enabled)
                if(invalidDate) Text(stringResource(R.string.academic_admin_invalid),color=MaterialTheme.colorScheme.error)
                Button({
                    val dates=runCatching { LocalDate.parse(starts).atStartOfDay(ZoneOffset.UTC).toEpochSecond() to LocalDate.parse(ends).atStartOfDay(ZoneOffset.UTC).toEpochSecond() }.getOrNull()
                    if(dates==null) invalidDate=true else onGroup(groupId,groupName,institution,period,dates.first,dates.second)
                },enabled=enabled && groupId.isNotBlank() && groupName.isNotBlank() && institution.isNotBlank() && period.isNotBlank() && starts.length==10 && ends.length==10,modifier=Modifier.testTag("academic_group_prepare")) { Text(stringResource(R.string.academic_admin_prepare)) }
            }
            AcademicCard(R.string.academic_admin_member) {
                AcademicPicker(R.string.academic_admin_group,knownGroups.map { it.id to (it.name+" · "+it.periodId) },selectedGroup,{ selectedGroup=it;student="";teacher="" },"academic_group",enabled)
                AcademicPicker(R.string.academic_admin_student,state.users.filter { it.institutionId==scopedGroup?.institutionId && UserRole.STUDENT in it.roles }.map { it.id to it.displayName },student,{ student=it },"academic_student",enabled)
                Button({ onMember(selectedGroup,student) },enabled=enabled && selectedGroup.isNotBlank() && student.isNotBlank(),modifier=Modifier.testTag("academic_member_prepare")) { Text(stringResource(R.string.academic_admin_prepare)) }
            }
            AcademicCard(R.string.academic_admin_class_create) {
                AcademicPicker(R.string.academic_admin_group,knownGroups.map { it.id to it.name },selectedGroup,{ selectedGroup=it;teacher="";student="" },"academic_class_group",enabled)
                AcademicPicker(R.string.academic_admin_teacher,state.users.filter { it.institutionId==scopedGroup?.institutionId && UserRole.TEACHER in it.roles }.map { it.id to it.displayName },teacher,{ teacher=it },"academic_teacher",enabled)
                AcademicField(subject,{ subject=it.take(120) },R.string.academic_admin_subject,"academic_subject",enabled)
                AcademicField(room,{ room=it.take(120) },R.string.academic_admin_room,"academic_room",enabled)
                Button({ onClass(selectedGroup,teacher,subject,room) },enabled=enabled && selectedGroup.isNotBlank() && teacher.isNotBlank() && subject.isNotBlank(),modifier=Modifier.testTag("academic_class_prepare")) { Text(stringResource(R.string.academic_admin_prepare)) }
            }
            AcademicCard(R.string.academic_admin_schedule) {
                AcademicPicker(R.string.academic_admin_class,state.classrooms.filter { it.status==ClassroomStatus.ACTIVE }.map { it.id to (it.name+" · "+it.groupName.orEmpty()) },selectedClass,{ selectedClass=it },"academic_schedule_class",enabled)
                AcademicPicker(R.string.academic_admin_day,DayOfWeek.entries.map { it.name to it.getDisplayName(TextStyle.FULL,LocalConfiguration.current.locales[0]) },day,{ day=it },"academic_schedule_day",enabled)
                AcademicField(fromTime,{ fromTime=it.take(5) },R.string.academic_admin_time_start,"academic_time_start",enabled)
                AcademicField(untilTime,{ untilTime=it.take(5) },R.string.academic_admin_time_end,"academic_time_end",enabled)
                Button({ onSchedule(selectedClass,day,fromTime,untilTime) },enabled=enabled && selectedClass.isNotBlank() && day.isNotBlank() && fromTime.length==5 && untilTime.length==5,modifier=Modifier.testTag("academic_schedule_prepare")) { Text(stringResource(R.string.academic_admin_prepare)) }
            }
            Text(stringResource(R.string.academic_admin_groups),style=MaterialTheme.typography.titleLarge)
            state.groups.forEach { Text(it.name+" · "+it.periodId.orEmpty()) }
            state.activeInvite?.let { invite -> InviteCard(invite,{ copyInvite(context,invite) },{ shareInvite(context,invite) },onRevokeInvite,!state.working) }
            Text(stringResource(R.string.academic_admin_classes),style=MaterialTheme.typography.titleLarge)
            if(state.classrooms.isEmpty()) Text(stringResource(R.string.academic_admin_empty_classes))
            state.classrooms.forEach { assigned -> ClassroomCard(assigned,enabled,{ onInvite(assigned.id) }) }
        }
        TextButton(onOpenChannel) { Text(stringResource(R.string.academic_admin_channel)) }
    }
    state.operationId?.let { operation ->
        val resource=state.operationResource.orEmpty()
        ProtectedAdminDialog(AdminOperation(operation,resource,StepUpAction.MANAGE_ACADEMICS,state.groups.find { it.id==resource }?.name ?: resource),state.working,state.failure,onCancel,onAuthorize)
    }
}
@Composable private fun AcademicCard(title:Int,content:@Composable ColumnScope.()->Unit) {
    V8GlassCard(Modifier.fillMaxWidth()) { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(stringResource(title),style=MaterialTheme.typography.titleMedium);content() } }
}
@Composable private fun AcademicField(value:String,change:(String)->Unit,label:Int,tag:String,enabled:Boolean) {
    OutlinedTextField(value,change,modifier=Modifier.fillMaxWidth().testTag(tag),singleLine=true,enabled=enabled,label={ Text(stringResource(label)) })
}
@Composable private fun AcademicPicker(label:Int,options:List<Pair<String,String>>,selected:String,choose:(String)->Unit,tag:String,enabled:Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded=true },modifier=Modifier.fillMaxWidth().testTag(tag),enabled=enabled && options.isNotEmpty()) { Text(options.find { it.first==selected }?.second ?: stringResource(label)) }
        DropdownMenu(expanded,{ expanded=false },modifier=Modifier.heightIn(max=300.dp)) {
            options.forEach { (id,name) -> DropdownMenuItem(text={ Text(name) },onClick={ choose(id);expanded=false },modifier=Modifier.testTag(tag+"_"+id)) }
        }
    }
    if(options.isEmpty()) Text(stringResource(R.string.academic_admin_empty_choices),style=MaterialTheme.typography.bodySmall)
}
@Composable private fun AcademicFailure(error:AppError) {
    val status=(error as? AppError.Http)?.status
    Text(stringResource(when(status) { 401,403 -> R.string.academic_admin_denied;409 -> R.string.academic_admin_conflict;422 -> R.string.academic_admin_invalid;else -> R.string.academic_admin_unavailable }),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("academic_admin_error"))
}
