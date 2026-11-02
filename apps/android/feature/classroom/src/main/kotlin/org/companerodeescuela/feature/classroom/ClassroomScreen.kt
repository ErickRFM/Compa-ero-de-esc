@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.classroom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8IconTile
import org.companerodeescuela.core.designsystem.v8.V8MatteBackdrop
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.ClassInvite
import org.companerodeescuela.shared.contracts.ClassroomStatus
import org.companerodeescuela.shared.contracts.ClassroomSummary
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomScreen(
    roles: Set<UserRole>,
    modifier: Modifier = Modifier,
    studentExperience: Boolean = false,
    teacherExperience: Boolean = false,
    administrativeExperience: Boolean = false,
    teacherUserId: String? = null,
    onOpenAttendance: (ClassroomSummary) -> Unit = {},
    onOpenChannel: (ClassroomSummary?) -> Unit = {},
    onOpenGrading: (ClassroomSummary) -> Unit = {},
    onOpenStudentGrades: () -> Unit = {},
    viewModel: ClassroomViewModel? = null,
) {
    if (administrativeExperience) {
        if (roles.any { it == UserRole.ADMIN || it == UserRole.SUPER_ADMIN }) {
            ClassroomAdministrationEditor(onOpenChannel = { onOpenChannel(null) }, modifier = modifier)
        } else {
            Text("Acceso administrativo no autorizado")
        }
        return
    }
    val model = viewModel ?: hiltViewModel<ClassroomViewModel>()
    val state by model.state.collectAsStateWithLifecycle()
    if (teacherExperience) {
        if (UserRole.TEACHER in roles) {
            TeacherAssignedClassrooms(
                state = state,
                teacherUserId = teacherUserId,
                onRefresh = model::refresh,
                onOpenAttendance = onOpenAttendance,
                onOpenChannel = onOpenChannel,
                onOpenGrading = onOpenGrading,
                onGenerateInvite = model::generateInvite,
                onRevokeInvite = model::revokeInvite,
                onAddMembers = model::addMembers,
                onLoadMembers = model::loadMembers,
                modifier = modifier,
            )
        } else {
            Text("Tu acceso docente está pendiente de aprobación institucional.")
        }
        return
    }
    if (!studentExperience || UserRole.STUDENT !in roles) {
        Text("Esta vista requiere la experiencia de estudiante.")
        return
    }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isStudent = true
    val pendingTeacher = false

    var joinCode by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                model.refresh()
            },
            modifier = Modifier.fillMaxSize().align(Alignment.TopCenter),
        ) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = CompaneroSize.homeContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = CompaneroSpacing.page,
                            vertical = CompaneroSpacing.sm,
                        ),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
            ) {
                V8ScreenHeader {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Mis clases",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = model::refresh, enabled = !state.loading) {
                            Text(if (state.loading) "Actualizando…" else "Actualizar clases")
                        }
                    }
                }
                V8GlassCard(modifier = Modifier.fillMaxWidth(), emphasized = isStudent) {
                    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                        Text(
                            text = if (isStudent) "Accesos académicos" else "Comunicación del grupo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                if (isStudent) {
                                    "Consulta tus calificaciones publicadas o revisa los avisos de tus docentes."
                                } else {
                                    "Accede al canal de avisos y recursos de tus grupos."
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                        ) {
                            if (isStudent) {
                                V8RedPrimaryButton(
                                    text = "Notas",
                                    onClick = onOpenStudentGrades,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            OutlinedButton(onClick = { onOpenChannel(null) }, modifier = Modifier.weight(1f)) {
                                Text("Canal")
                            }
                        }
                    }
                }
                InstitutionalSections(state)
                Text(
                    text = "Los espacios de Compañero no sustituyen tu inscripción oficial.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.loading && state.classrooms.isEmpty()) {
                    CircularProgressIndicator()
                }

                state.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.successMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (isStudent && state.groups.isEmpty() && state.institutionalSource != "SIII") {
                    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                        ) {
                            Text(
                                text = "Unirme con código",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text =
                                    "¿Control escolar te dio un código? Escríbelo para solicitar acceso a tu espacio de clase.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = { raw ->
                                    joinCode =
                                        raw
                                            .uppercase()
                                            .filter { it.isLetterOrDigit() || it == '-' }
                                            .take(9)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("classroom_join_code"),
                                singleLine = true,
                                label = { Text("Código de clase") },
                                placeholder = { Text("ABCD-2345") },
                            )
                            V8RedPrimaryButton(
                                text = if (state.submitting) "Vinculando…" else "Unirme a la clase",
                                onClick = { model.join(joinCode) },
                                enabled = !state.submitting && joinCode.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().testTag("classroom_join_action"),
                            )
                        }
                    }
                }

                if (isStudent && state.groups.isNotEmpty()) {
                    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                        ) {
                            Text(
                                text = "Grupos de Compañero",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = state.groups.joinToString(" · ") { it.name },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text =
                                    "Estos grupos colaborativos conservan permisos propios; " +
                                        "tus inscripciones oficiales aparecen en el catálogo institucional.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (pendingTeacher) {
                    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                        ) {
                            Text(
                                text = "Cuenta docente pendiente",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text =
                                    "Cuando control escolar verifique tu perfil y te asigne grupos, tus clases y horarios aparecerán aquí automáticamente.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.activeInvite?.let { invite ->
                    InviteCard(
                        invite = invite,
                        onCopy = { copyInvite(context, invite) },
                        onShare = { shareInvite(context, invite) },
                        onRevoke = model::revokeInvite,
                        enabled = !state.submitting,
                    )
                }

                Text(
                    text = "Tus espacios colaborativos",
                    style = MaterialTheme.typography.titleMedium,
                )

                if (!state.loading && state.classrooms.isEmpty()) {
                    Text(
                        text =
                            when {
                                pendingTeacher -> "No tienes espacios colaborativos. Tu perfil docente sigue pendiente de verificación."
                                isStudent -> "No tienes espacios colaborativos vinculados a tu grupo."
                                else -> "Todavía no hay espacios colaborativos asignados."
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.classrooms.forEachIndexed { index, classroom ->
                    ClassroomCard(
                        classroom = classroom,
                        enabled = !state.submitting,
                        onGenerateInvite = { model.generateInvite(classroom.id) },
                        showInviteActions = false,
                    )
                    if (index != state.classrooms.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * Teacher experience is read-only with respect to academic assignments:
 * control escolar creates groups; teachers operate only assigned active classes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherAssignedClassrooms(
    state: ClassroomUiState,
    teacherUserId: String?,
    onRefresh: () -> Unit,
    onOpenAttendance: (ClassroomSummary) -> Unit,
    onOpenChannel: (ClassroomSummary?) -> Unit,
    onOpenGrading: (ClassroomSummary) -> Unit,
    onGenerateInvite: (String) -> Unit,
    onRevokeInvite: () -> Unit,
    onAddMembers: (String, List<String>) -> Unit = { _, _ -> },
    onLoadMembers: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importReader = remember(context) { NativeRosterImportReader(context) }
    var selectedImportClass by remember { mutableStateOf<String?>(null) }
    var selectedRosterClass by remember { mutableStateOf<String?>(null) }
    var importCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var manualIdentifier by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var importLoading by remember { mutableStateOf(false) }
    var pickerClassId by remember { mutableStateOf<String?>(null) }
    val documentPicker =
        if (LocalActivityResultRegistryOwner.current != null) {
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val expectedClassId = pickerClassId
                if (uri != null && expectedClassId != null && selectedImportClass == expectedClassId) {
                    importLoading = true
                    importCandidates = emptyList()
                    importError = null
                    scope.launch {
                        val result = runCatching { importReader.read(uri) }
                        if (selectedImportClass != expectedClassId) return@launch
                        importLoading = false
                        result.onSuccess { importCandidates = it }
                        result.onFailure { importError = it.message ?: "No se pudo importar la lista" }
                    }
                }
            }
        } else {
            null
        }
    val assigned =
        state.classrooms.filter {
            it.canManage && it.status == ClassroomStatus.ACTIVE && it.teacherId == teacherUserId
        }
    val haptic = LocalHapticFeedback.current
    Box(modifier = modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current) {
            V8MatteBackdrop(modifier = Modifier.matchParentSize())
        }
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRefresh()
            },
            modifier = Modifier.fillMaxSize().align(Alignment.TopCenter),
        ) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = CompaneroSize.homeContentMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
            ) {
                V8ScreenHeader {
                    Text(
                        text = "Mis clases",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                InstitutionalSections(state)
                Text(
                    text =
                        "Espacios colaborativos de Compañero. " +
                            "Sus permisos y herramientas son independientes del catálogo institucional.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.loading) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                state.errorMessage?.let { StatusNotice(title = "Datos sin actualizar", message = it) }
                state.successMessage?.let { StatusNotice(title = "Listo", message = it) }
                state.activeInvite?.let { invite ->
                    if (assigned.any { it.id == invite.classroomId }) {
                        InviteCard(
                            invite = invite,
                            onCopy = { copyInvite(context, invite) },
                            onShare = { shareInvite(context, invite) },
                            onRevoke = onRevokeInvite,
                            enabled = !state.submitting,
                        )
                    }
                }
                if (!state.loading && state.errorMessage == null && assigned.isEmpty()) {
                    StatusNotice(
                        title = "No tienes espacios colaborativos asignados",
                        message = "Las asignaciones oficiales están en el catálogo institucional; estos espacios tienen permisos propios.",
                    )
                }
                if (assigned.isNotEmpty()) {
                    val groupCount = assigned.mapNotNull { it.groupName }.distinct().size
                    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                            Text(
                                text = "${assigned.size} espacios colaborativos activos",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "$groupCount grupos vinculados · permisos de docente",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    assigned.forEach { classroom ->
                        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    V8IconTile(Icons.Filled.MenuBook)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = classroom.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text =
                                                listOfNotNull(
                                                    classroom.groupName?.takeIf(String::isNotBlank),
                                                    classroom.room?.takeIf(String::isNotBlank)?.let { "Aula $it" },
                                                ).joinToString(" · ").ifBlank { "Clase asignada" },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                V8RedPrimaryButton(
                                    text = "Pase de lista",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onOpenAttendance(classroom)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onOpenChannel(classroom)
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Canal")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onOpenGrading(classroom)
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Evaluar")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onGenerateInvite(classroom.id) },
                                    enabled = !state.submitting && classroom.canManage,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Generar código para compartir")
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectedRosterClass = classroom.id
                                        onLoadMembers(classroom.id)
                                    },
                                    enabled = !state.submitting,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Ver integrantes de la clase") }
                                state.members[classroom.id]?.let { members ->
                                    Text("${members.size} alumnos con cuenta vinculada", style = MaterialTheme.typography.titleSmall)
                                    if (members.isEmpty()) {
                                        Text("Aún no hay alumnos vinculados a este Canal.")
                                    }
                                    members.take(6).forEach { student ->
                                        val timestamp = Instant.ofEpochSecond(student.joinedAtEpochSeconds)
                                        val date = timestamp.atZone(ZoneId.systemDefault())
                                        val joinedAt = DateTimeFormatter.ofPattern("d MMM yyyy").format(date)
                                        Text(
                                            student.displayName + " · " + joinedAt,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (members.size > 6) {
                                        Text(
                                            "+${members.size - 6} integrantes más en la lista de la clase",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectedImportClass = classroom.id
                                        importCandidates = emptyList()
                                        manualIdentifier = ""
                                        importError = null
                                        importLoading = false
                                        pickerClassId = null
                                    },
                                    enabled = !state.submitting,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Añadir alumnos · lista o manual") }
                                if (selectedImportClass == classroom.id) {
                                    Text(
                                        "Se vinculan cuentas registradas en Compañero, no matrículas oficiales de SIII.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    OutlinedTextField(
                                        value = manualIdentifier,
                                        onValueChange = { manualIdentifier = it.take(128) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Correo o ID de alumno") },
                                        singleLine = true,
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            onAddMembers(classroom.id, listOf(manualIdentifier.trim()))
                                        },
                                        enabled = !state.submitting && manualIdentifier.trim().isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Vincular alumno registrado") }
                                    OutlinedButton(
                                        onClick = {
                                            pickerClassId = classroom.id
                                            documentPicker?.launch(
                                                arrayOf(
                                                    "application/pdf",
                                                    "text/csv",
                                                    "text/plain",
                                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                ),
                                            )
                                        },
                                        enabled = !state.submitting && !importLoading && documentPicker != null,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Elegir lista PDF / CSV / TXT / XLSX") }
                                    if (importLoading) {
                                        CircularProgressIndicator()
                                        Text("Leyendo lista de alumnos…", style = MaterialTheme.typography.bodySmall)
                                    }
                                    importError?.let {
                                        StatusNotice(
                                            title = "No se pudo leer la lista",
                                            message = it,
                                        )
                                    }
                                    if (importCandidates.isNotEmpty()) {
                                        Text(
                                            "${importCandidates.size} identificadores detectados. Revisa antes de confirmar:",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            importCandidates.take(10).joinToString("\n") +
                                                if (importCandidates.size > 10) "\n…" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        V8RedPrimaryButton(
                                            text = "Confirmar incorporación a Compañero",
                                            onClick = { onAddMembers(classroom.id, importCandidates) },
                                            enabled = !state.submitting && !importLoading,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRefresh()
                    },
                    enabled = !state.loading && !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Actualizar clases")
                }
            }
        }
    }
    selectedRosterClass?.let { classroomId ->
        val selected = assigned.firstOrNull { it.id == classroomId }
        val members = state.members[classroomId]
        AlertDialog(
            onDismissRequest = { selectedRosterClass = null },
            title = { Text("Lista de ${selected?.name ?: "alumnos"}") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                ) {
                    when {
                        members == null -> CircularProgressIndicator()
                        members.isEmpty() -> Text("No hay cuentas vinculadas a esta clase.")
                        else -> {
                            Text(
                                "${members.size} cuentas verificadas · lista interna de Compañero",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            members.forEach { student ->
                                Text(student.displayName, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRosterClass = null }) { Text("Cerrar") }
            },
        )
    }
}

@Composable
internal fun InstitutionalSections(state: ClassroomUiState) {
    if (state.institutionalLoading) CircularProgressIndicator()
    state.institutionalErrorMessage?.let {
        StatusNotice(title = "Datos institucionales sin actualizar", message = it)
    }
    if (state.institutionalSource != "SIII") return
    Column(Modifier.fillMaxWidth().testTag("institutional_sections"), verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
        Text("Clases institucionales · SIII", style = MaterialTheme.typography.titleMedium)
        if (state.institutionalFromCache) {
            Text(
                "Última sincronización guardada. Requiere conexión para confirmar vigencia.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.institutionalSections.isEmpty()) Text("No hay inscripciones o asignaciones institucionales vigentes.")
        state.institutionalSections.forEach { section ->
            V8GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("institutional_section_${section.id}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        section.subjectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${section.subjectCode} · Grupo ${section.groupName}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        section.teacherNames.joinToString(" · ").ifBlank { "Docente pendiente de asignación" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Fuente institucional · versión ${section.sourceVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ClassroomCard(
    classroom: ClassroomSummary,
    enabled: Boolean,
    onGenerateInvite: () -> Unit,
    showInviteActions: Boolean = true,
) {
    V8GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
        ) {
            Text(
                text = classroom.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    buildString {
                        classroom.groupName?.let {
                            append(it)
                            append(" · ")
                        }
                        append(classroom.teacherDisplayName)
                        classroom.room?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            classroom.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showInviteActions && classroom.canManageEnrollment) {
                V8RedPrimaryButton(
                    text = stringResource(R.string.academic_invite_generate),
                    onClick = onGenerateInvite,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun InviteCard(
    invite: ClassInvite,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRevoke: () -> Unit,
    enabled: Boolean,
) {
    V8GlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.academic_invite_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = invite.code,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.academic_invite_expiry, expiryLabel(invite.expiresAtEpochSeconds)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onCopy,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.academic_invite_copy))
                }
                Button(
                    onClick = onShare,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.academic_invite_share))
                }
            }
            OutlinedButton(
                onClick = onRevoke,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.academic_invite_revoke))
            }
        }
    }
}

internal fun copyInvite(
    context: Context,
    invite: ClassInvite,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "Código de incorporación",
            invite.code,
        ),
    )
}

internal fun shareInvite(
    context: Context,
    invite: ClassInvite,
) {
    val text =
        "Control escolar te invita a vincular una clase en Compañero con el código " +
            invite.code +
            ". El código es temporal."
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Compartir clave"))
}

private fun expiryLabel(epochSeconds: Long): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return Instant
        .ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
