package org.companerodeescuela.feature.tutoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.ui.component.CompaneroSurface
import org.companerodeescuela.core.ui.component.CompaneroSurfaceRole
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.RepresentativePosition

@Composable
fun TutorGroupsScreen(
    modifier: Modifier = Modifier,
    viewModel: TutorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    key(state.scopeGeneration) {
        TutorGroupsContent(state, viewModel, modifier)
    }
}

@Composable
private fun TutorGroupsContent(state: TutorUiState, viewModel: TutorViewModel, modifier: Modifier) {
    var groupId by remember { mutableStateOf<String?>(null) }
    var studentId by remember { mutableStateOf<String?>(null) }
    var caseSummary by remember { mutableStateOf("") }
    var editingCase by remember { mutableStateOf<String?>(null) }
    var noteText by remember { mutableStateOf("") }
    var publishNote by remember { mutableStateOf(false) }

    LaunchedEffect(state.groups) {
        if (state.groups.none { it.id == groupId }) groupId = state.groups.firstOrNull()?.id
    }
    LaunchedEffect(groupId) {
        studentId = null
        editingCase = null
        groupId?.let(viewModel::loadRoster)
    }
    LaunchedEffect(groupId, studentId) {
        caseSummary = ""
        editingCase = null
        noteText = ""
        publishNote = false
    }

    Column(
        modifier = modifier.fillMaxSize()
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        Text("Mi grupo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Alumnos asignados, nombramiento de representantes y seguimiento académico.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loading) CircularProgressIndicator()
        state.error?.let { StatusNotice(title = "No pudimos actualizar", message = it) }
        state.notice?.let { StatusNotice(title = "Tutorías", message = it) }
        if (!state.loading && state.groups.isEmpty()) {
            StatusNotice(title = "Sin asignación", message = "Control escolar debe asignarte un grupo.")
        }
        state.groups.forEach { group ->
            OutlinedButton(
                onClick = { groupId = group.id },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text((if (group.id == groupId) "● " else "") + group.name)
            }
        }

        // Representatives Summary Section for the active group
        val overview = state.representativesOverview
        if (groupId != null && overview != null) {
            Text("Representantes del grupo", style = MaterialTheme.typography.titleMedium)
            CompaneroSurface(modifier = Modifier.fillMaxWidth(), role = CompaneroSurfaceRole.CARD) {
                Column(
                    modifier = Modifier.padding(CompaneroSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    val activeGroup = groupId!!
                    Text(
                        "Jefe de grupo: " + (overview.chief?.studentDisplayName ?: overview.chief?.studentUserId ?: "Sin asignar"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    overview.chief?.let { chief ->
                        OutlinedButton(
                            onClick = { viewModel.revokeRepresentative(activeGroup, chief.id) },
                            enabled = !state.submitting,
                        ) {
                            Text("Revocar Jefe")
                        }
                    }

                    Text(
                        "Subjefe de grupo: " + (overview.deputy?.studentDisplayName ?: overview.deputy?.studentUserId ?: "Sin asignar"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    overview.deputy?.let { deputy ->
                        OutlinedButton(
                            onClick = { viewModel.revokeRepresentative(activeGroup, deputy.id) },
                            enabled = !state.submitting,
                        ) {
                            Text("Revocar Subjefe")
                        }
                    }

                    if (overview.pendingInvitations.isNotEmpty()) {
                        Text(
                            "Invitaciones pendientes: " + overview.pendingInvitations.size,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (state.rosterLoading) CircularProgressIndicator()
        if (groupId != null && !state.rosterLoading && state.roster.isEmpty()) {
            StatusNotice(title = "Sin alumnos registrados", message = "No hay miembros disponibles para este grupo.")
        }
        if (state.rosterGroupId == groupId) {
            state.roster.forEach { student ->
                CompaneroSurface(modifier = Modifier.fillMaxWidth(), role = CompaneroSurfaceRole.CARD) {
                    Column(
                        modifier = Modifier.padding(CompaneroSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                    ) {
                        Text(
                            student.displayName ?: "Cuenta " + student.userId,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (!student.verifiedPlatformStudent) {
                            Text(
                                "Identidad no sincronizada con Compañero.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                            OutlinedButton(onClick = { studentId = student.userId; editingCase = null }) {
                                Text("Ver seguimiento")
                            }
                            val activeGroup = groupId ?: return@Row
                            OutlinedButton(
                                onClick = {
                                    viewModel.appointRepresentative(
                                        activeGroup,
                                        student.userId,
                                        RepresentativePosition.CHIEF,
                                    )
                                },
                                enabled = !state.submitting && student.verifiedPlatformStudent,
                            ) {
                                Text("Nombrar Jefe")
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.appointRepresentative(
                                        activeGroup,
                                        student.userId,
                                        RepresentativePosition.DEPUTY,
                                    )
                                },
                                enabled = !state.submitting && student.verifiedPlatformStudent,
                            ) {
                                Text("Nombrar Subjefe")
                            }
                        }
                    }
                }
            }
        }

        val selected = state.roster.firstOrNull { it.userId == studentId }
        if (selected != null) {
            Text(
                "Seguimiento de " + (selected.displayName ?: selected.userId),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = caseSummary,
                onValueChange = { caseSummary = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Motivo de acompañamiento") },
                minLines = 2,
            )
            Button(
                onClick = {
                    val activeGroup = groupId ?: return@Button
                    viewModel.createCase(activeGroup, selected.userId, caseSummary, state.scopeGeneration)
                    caseSummary = ""
                },
                enabled = !state.submitting && caseSummary.trim().length in 8..500,
            ) { Text("Crear seguimiento") }

            state.cases.filter { it.studentId == selected.userId && it.academicGroupId == groupId }
                .forEach { case ->
                    CompaneroSurface(modifier = Modifier.fillMaxWidth(), role = CompaneroSurfaceRole.CARD) {
                        Column(
                            modifier = Modifier.padding(CompaneroSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                        ) {
                            Text(case.summary, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Estado: " + case.status.name.lowercase().replace('_', ' '),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            case.notes.forEach { note ->
                                Text(note.body, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (note.visibility.name == "STUDENT_VISIBLE") {
                                        "Compartida con estudiante"
                                    } else {
                                        "Nota interna"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (editingCase == case.id) {
                                OutlinedTextField(
                                    value = noteText, onValueChange = { noteText = it.take(2000) },
                                    label = { Text("Observación o acuerdo") },
                                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                                    Switch(checked = publishNote, onCheckedChange = { publishNote = it })
                                    Text("Visible al estudiante", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                    enabled = !state.submitting && noteText.trim().length in 5..2000,
                                    onClick = {
                                        viewModel.addNote(case.id, noteText, publishNote, state.scopeGeneration)
                                        noteText = ""
                                        editingCase = null
                                    },
                                ) { Text("Guardar observación") }
                            } else {
                                OutlinedButton(onClick = { editingCase = case.id; noteText = ""; publishNote = false }) {
                                    Text("Añadir observación")
                                }
                            }
                        }
                    }
                }
        }
        OutlinedButton(onClick = viewModel::refresh, enabled = !state.loading && !state.submitting) {
            Text("Actualizar")
        }
    }
}
