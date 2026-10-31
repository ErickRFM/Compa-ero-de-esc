@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.classroom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
fun ClassroomScreen(
    roles: Set<UserRole>,
    modifier: Modifier = Modifier,
    teacherExperience: Boolean = false,
    teacherUserId: String? = null,
    onOpenAttendance: (ClassroomSummary) -> Unit = {},
    onOpenChannel: (ClassroomSummary?) -> Unit = {},
    onOpenGrading: (ClassroomSummary) -> Unit = {},
    onOpenStudentGrades: () -> Unit = {},
    viewModel: ClassroomViewModel? = null,
) {
    if (!teacherExperience && roles.any { it == UserRole.ADMIN || it == UserRole.SUPER_ADMIN }) {
        ClassroomAdministrationEditor(onOpenChannel = { onOpenChannel(null) }, modifier = modifier)
        return
    }
    val model = viewModel ?: hiltViewModel<ClassroomViewModel>()
    val state by model.state.collectAsStateWithLifecycle()
    if (teacherExperience && UserRole.TEACHER in roles) {
        TeacherAssignedClassrooms(
            state = state,
            teacherUserId = teacherUserId,
            onRefresh = model::refresh,
            onOpenAttendance = onOpenAttendance,
            onOpenChannel = onOpenChannel,
            onOpenGrading = onOpenGrading,
            modifier = modifier,
        )
        return
    }
    val context = LocalContext.current
    val isStudent = UserRole.STUDENT in roles
    val pendingTeacher = UserRole.TEACHER_PENDING in roles

    var joinCode by remember { mutableStateOf("") }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .widthIn(max = CompaneroSize.homeContentMaxWidth)
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
                            text = "Calificaciones",
                            onClick = onOpenStudentGrades,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedButton(onClick = { onOpenChannel(null) }, modifier = Modifier.weight(1f)) {
                        Text("Canal de clase")
                    }
                }
            }
        }
        InstitutionalSections(state)
        Text(
            text = "Espacios colaborativos de Compañero. No sustituyen tus inscripciones oficiales de SIII.",
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
                        text = "Unirme a una clase",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text =
                            "Usa un código de incorporación emitido por control escolar cuando tu grupo todavía no se haya sincronizado automáticamente.",
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
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Código de incorporación") },
                        placeholder = { Text("ABCD-2345") },
                    )
                    Button(
                        onClick = { model.join(joinCode) },
                        enabled = !state.submitting && joinCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Vincular clase")
                    }
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
            )
            if (index != state.classrooms.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

/**
 * Teacher experience is read-only with respect to academic assignments:
 * control escolar creates groups; teachers operate only assigned active classes.
 */
@Composable
private fun TeacherAssignedClassrooms(
    state: ClassroomUiState,
    teacherUserId: String?,
    onRefresh: () -> Unit,
    onOpenAttendance: (ClassroomSummary) -> Unit,
    onOpenChannel: (ClassroomSummary?) -> Unit,
    onOpenGrading: (ClassroomSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val assigned =
        state.classrooms.filter {
            it.canManage && it.status == ClassroomStatus.ACTIVE && it.teacherId == teacherUserId
        }
    Box(modifier = modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current) {
            V8MatteBackdrop(modifier = Modifier.matchParentSize())
        }
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
                text = "Espacios colaborativos de Compañero. Sus permisos y herramientas son independientes del catálogo institucional.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.loading) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            state.errorMessage?.let { StatusNotice(title = "Datos sin actualizar", message = it) }
            if (!state.loading && state.errorMessage == null && assigned.isEmpty()) {
                StatusNotice(
                    title = "No tienes espacios colaborativos asignados",
                    message = "Las asignaciones oficiales están en el catálogo institucional; estos espacios tienen permisos propios.",
                )
            }
            if (assigned.isNotEmpty()) {
                V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                        Text(
                            text = "${assigned.size} espacios colaborativos activos",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text =
                                assigned
                                    .mapNotNull { it.groupName }
                                    .distinct()
                                    .size
                                    .toString() +
                                    " grupos vinculados · permisos de docente",
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
                                onClick = { onOpenAttendance(classroom) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                            ) {
                                OutlinedButton(onClick = { onOpenChannel(classroom) }, modifier = Modifier.weight(1f)) {
                                    Text("Canal")
                                }
                                OutlinedButton(onClick = { onOpenGrading(classroom) }, modifier = Modifier.weight(1f)) {
                                    Text("Evaluar")
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.loading && !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Actualizar clases")
            }
        }
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
            if (classroom.canManageEnrollment) {
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
