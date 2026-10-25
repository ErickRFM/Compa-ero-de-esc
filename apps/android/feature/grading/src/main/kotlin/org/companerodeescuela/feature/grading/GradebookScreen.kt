@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.grading

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8MatteBackdrop
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.ui.component.StatusNotice
import org.companerodeescuela.shared.contracts.TeacherClassContext

/**
 * Only classes returned with canManage=true can be selected. Server-side grading
 * permissions still determine whether synchronization is authorized/available.
 */
@Composable
fun GradebookScreen(
    modifier: Modifier = Modifier,
    requestedClassroom: TeacherClassContext? = null,
    viewModel: GradebookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(requestedClassroom) { requestedClassroom?.let { viewModel.requestClassroom(it.classroomId) } }
    var confirmSync by remember(state.classroomId, state.preview, state.categories, state.gradingPeriod) { mutableStateOf(false) }
    var confirmPublish by remember(state.classroomId, state.gradebookRevision, state.gradingPeriod) { mutableStateOf(false) }
    val context = LocalContext.current
    val fileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val fallbackName =
                    runCatching {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    }.getOrNull()?.takeIf(String::isNotBlank) ?: when {
                        mime.contains("spreadsheet") || mime.contains("excel") -> "calificaciones.xlsx"
                        mime.contains("csv") -> "calificaciones.csv"
                        else -> uri.lastPathSegment ?: "calificaciones.xlsx"
                    }
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("No se pudo abrir el archivo.")
                }.onSuccess { bytes ->
                    viewModel.importSpreadsheet(fallbackName, bytes)
                }.onFailure {
                    viewModel.importSpreadsheet("archivo-invalido", ByteArray(0))
                }
            }
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
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
        ) {
            Text(
                "Evaluación",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Organiza las actividades de tus materias y revisa las calificaciones antes de enviarlas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            V8GlassCard(modifier = Modifier.fillMaxWidth(), emphasized = true) {
                Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                    TeacherGradingSectionTitle(number = "01", title = "Materia y periodo")
                    Text("Tus clases asignadas", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (state.loadingClassrooms) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    if (state.classroomLoadError != null) {
                        StatusNotice(title = "Sin datos de clases", message = state.classroomLoadError!!)
                    }
                    if (!state.loadingClassrooms && state.classroomLoadError == null && state.assignedClassrooms.isEmpty()) {
                        StatusNotice(
                            title = "Aún no hay materias para evaluar",
                            message = "Control escolar debe asignarte una clase activa. No necesitas escribir ningún ID.",
                        )
                    }
                    if (state.assignedClassrooms.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                        ) {
                            state.assignedClassrooms.forEach { classroom ->
                                FilterChip(
                                    selected = classroom.id == state.classroomId,
                                    enabled = !state.submitting,
                                    onClick = { viewModel.setClassroomId(classroom.id) },
                                    label = { Text(classroom.name + " · " + classroom.groupName.orEmpty()) },
                                )
                            }
                        }
                        val selected = state.assignedClassrooms.firstOrNull { it.id == state.classroomId }
                        if (selected != null) {
                            Text(
                                "Grupo " + selected.groupName.orEmpty() +
                                    (selected.room?.takeIf(String::isNotBlank)?.let { " · Aula $it" } ?: ""),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.gradingPeriod,
                        onValueChange = viewModel::setGradingPeriod,
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = teacherFieldColors(),
                        label = { Text("Periodo de evaluación") },
                        placeholder = { Text("Parcial 1") },
                    )
                    OutlinedButton(
                        onClick = viewModel::refreshClassrooms,
                        enabled = !state.loadingClassrooms && !state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Actualizar materias") }
                }
            }

            V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                    TeacherGradingSectionTitle(number = "02", title = "Esquema de evaluación")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ponderación acumulada", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatWeight(state.totalWeight) + "% de 100%",
                            color = if (state.schemeComplete) V8RedColors.Success else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (state.totalWeight / 100.0).coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                    )
                    if (state.categories.isEmpty()) {
                        Text(
                            "Agrega proyectos, prácticas, exámenes o cualquier actividad evaluable.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    state.categories.forEachIndexed { index, category ->
                        V8GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding =
                                androidx.compose.foundation.layout
                                    .PaddingValues(12.dp),
                            cornerRadius = 14.dp,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                                Text(
                                    "Actividad " + (index + 1),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    if (maxWidth < 340.dp) {
                                        Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                                            CategoryNameField(
                                                category.name,
                                                { viewModel.updateCategory(index, name = it) },
                                                Modifier.fillMaxWidth(),
                                                enabled = !state.submitting,
                                            )
                                            CategoryWeightField(category.weightPercent, {
                                                viewModel.updateCategory(index, weight = it)
                                            }, Modifier.fillMaxWidth(), enabled = !state.submitting)
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                                            CategoryNameField(
                                                category.name,
                                                { viewModel.updateCategory(index, name = it) },
                                                Modifier.weight(2f),
                                                enabled = !state.submitting,
                                            )
                                            CategoryWeightField(category.weightPercent, {
                                                viewModel.updateCategory(index, weight = it)
                                            }, Modifier.weight(1f), enabled = !state.submitting)
                                        }
                                    }
                                }
                                OutlinedButton(onClick = { viewModel.removeCategory(index) }, enabled = !state.submitting) {
                                    Text("Quitar actividad")
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = viewModel::addCategory, enabled = !state.submitting, modifier = Modifier.fillMaxWidth()) {
                        Text("+ Agregar actividad")
                    }
                    if (state.categories.isNotEmpty() && !state.schemeComplete) {
                        Text(
                            if (state.totalWeight > 100) {
                                "La ponderación supera el 100%."
                            } else {
                                "Falta " + formatWeight(100.0 - state.totalWeight) + "% para completar el esquema."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                    TeacherGradingSectionTitle(number = "03", title = "Importar y revisar")
                    Text(
                        "Usa un Excel o CSV con matrícula y columnas que coincidan con tus actividades.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = {
                            fileLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "text/csv",
                                    "text/comma-separated-values",
                                ),
                            )
                        },
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.preview == null) "Elegir archivo Excel / CSV" else "Cambiar archivo") }
                    state.preview?.let { preview ->
                        Text(
                            state.importedFileName.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            preview.rows.size.toString() + " alumnos · " + preview.columns.size + " columnas",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (preview.warnings.isNotEmpty()) {
                            Text(
                                "${preview.warnings.size} incidencias:\n" + preview.warnings.joinToString("\n"),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            "Columnas detectadas: " + preview.columns.joinToString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        preview.rows.take(20).forEach { student ->
                            Text(
                                student.studentId + " · " +
                                    student.values.entries.joinToString { (column, value) -> "$column: ${value ?: "vacío"}" },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (preview.rows.size >
                            20
                        ) {
                            Text(
                                "Vista previa de 20 de ${preview.rows.size} alumnos.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            state.errorMessage?.let {
                StatusNotice(title = "No se pudo completar", message = it)
            }
            state.successMessage?.let {
                StatusNotice(title = "Resultado de evaluación", message = it)
            }
            OutlinedButton(
                onClick = viewModel::saveDraft,
                enabled =
                    !state.submitting &&
                        !state.gradebookPublished &&
                        state.schemeComplete &&
                        state.preview?.rows?.isNotEmpty() == true &&
                        state.preview?.warnings?.isEmpty() == true &&
                        state.gradingPeriod.isNotBlank() &&
                        state.assignedClassrooms.any { it.id == state.classroomId },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.gradebookRevision ==
                        null
                    ) {
                        "Guardar borrador"
                    } else {
                        "Actualizar borrador · revisión ${state.gradebookRevision}"
                    },
                )
            }
            Button(
                onClick = { confirmPublish = true },
                enabled = !state.submitting && !state.gradebookPublished && state.gradebookRevision != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.gradebookPublished) "Calificaciones publicadas" else "Publicar para estudiantes") }
            if (state.gradebookPublished) {
                Text(
                    "Esta revisión ya está publicada. Para corregirla se requiere una operación de corrección autorizada.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                when {
                    state.capabilityLoading -> "Comprobando si el servidor permite publicar calificaciones…"
                    state.institutionalSyncAvailable ->
                        "El servidor indica que dispone de un proveedor de calificaciones. El envío requiere confirmación institucional."
                    else -> "La publicación institucional no está configurada. Puedes importar y revisar las notas, pero no enviarlas."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!state.capabilityLoading && !state.institutionalSyncAvailable) {
                OutlinedButton(
                    onClick = viewModel::refreshCapability,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Comprobar conexión institucional") }
            }
            Button(
                onClick = { confirmSync = true },
                enabled =
                    !state.submitting &&
                        !state.loadingClassrooms &&
                        !state.capabilityLoading &&
                        state.institutionalSyncAvailable &&
                        state.schemeComplete &&
                        state.preview?.rows?.isNotEmpty() == true &&
                        state.preview?.warnings?.isEmpty() == true &&
                        state.gradingPeriod.isNotBlank() &&
                        state.assignedClassrooms.any { it.id == state.classroomId },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator()
                } else {
                    Text("Enviar a control escolar")
                }
            }
        }
    }
    if (confirmSync) {
        AlertDialog(
            onDismissRequest = { confirmSync = false },
            title = { Text("Confirmar publicación institucional") },
            text = {
                Text(
                    "${state.assignedClassrooms.firstOrNull {
                        it.id == state.classroomId
                    }?.name.orEmpty()} · ${state.gradingPeriod}\n${state.preview?.rows?.size ?: 0} alumnos. El servidor deberá confirmar el resultado; no se considerará publicado antes de esa respuesta.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmSync = false
                    viewModel.sync()
                }) { Text("Confirmar envío") }
            },
            dismissButton = { TextButton(onClick = { confirmSync = false }) { Text("Seguir revisando") } },
        )
    }
    if (confirmPublish) {
        AlertDialog(
            onDismissRequest = { confirmPublish = false },
            title = { Text("Publicar calificaciones") },
            text = {
                Text(
                    "Se publicará la revisión ${state.gradebookRevision} de ${state.preview?.rows?.size ?: 0} estudiantes para ${state.gradingPeriod}. Después, esta revisión no se podrá editar.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmPublish = false
                    viewModel.publishDraft()
                }) { Text("Confirmar publicación") }
            },
            dismissButton = { TextButton(onClick = { confirmPublish = false }) { Text("Seguir revisando") } },
        )
    }
}

@Composable
private fun TeacherGradingSectionTitle(
    number: String,
    title: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
        Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CategoryNameField(
    value: String,
    change: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        enabled = enabled,
        modifier = modifier,
        singleLine = true,
        colors = teacherFieldColors(),
        label = { Text("Actividad") },
        placeholder = { Text("Proyecto") },
    )
}

@Composable
private fun CategoryWeightField(
    value: Double,
    change: (Double) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = if (value == 0.0) "" else formatWeight(value),
        enabled = enabled,
        onValueChange = { raw -> raw.replace(',', '.').toDoubleOrNull()?.let { change(it.coerceIn(0.0, 100.0)) } },
        modifier = modifier,
        singleLine = true,
        colors = teacherFieldColors(),
        label = { Text("Porcentaje") },
        placeholder = { Text("20") },
    )
}

private fun formatWeight(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable
private fun teacherFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    )
