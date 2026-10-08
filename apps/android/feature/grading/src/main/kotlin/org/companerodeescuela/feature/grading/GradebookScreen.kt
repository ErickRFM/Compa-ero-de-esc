package org.companerodeescuela.feature.grading

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8CampusBackdrop
import org.companerodeescuela.core.designsystem.v8.V8BrandHeader
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8RedColors

@Composable
fun GradebookScreen(
    modifier: Modifier = Modifier,
    viewModel: GradebookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val fallbackName = when {
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
        V8CampusBackdrop(modifier = Modifier.matchParentSize())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
    ) {
        V8BrandHeader()
        Text("Evaluación y calificaciones", style = MaterialTheme.typography.headlineLarge, color = V8RedColors.TextPrimary)
        Text(
            "Configura el 100%, importa Excel/CSV y revisa antes de sincronizar con el sistema escolar.",
            color = V8RedColors.TextSecondary,
        )

        OutlinedTextField(
            value = state.classroomId,
            onValueChange = viewModel::setClassroomId,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = teacherFieldColors(),
            label = { Text("ID de clase asignada") },
        )
        OutlinedTextField(
            value = state.gradingPeriod,
            onValueChange = viewModel::setGradingPeriod,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = teacherFieldColors(),
            label = { Text("Periodo") },
            placeholder = { Text("Parcial 1") },
        )

        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text("Esquema de evaluación", style = MaterialTheme.typography.titleMedium, color = V8RedColors.TextPrimary)
                Text(
                    "Total: ${formatWeight(state.totalWeight)}%",
                    color = if (state.schemeComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                state.categories.forEachIndexed { index, category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                    ) {
                        OutlinedTextField(
                            value = category.name,
                            onValueChange = { viewModel.updateCategory(index, name = it) },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
            colors = teacherFieldColors(),
                            label = { Text("Actividad") },
                            placeholder = { Text("Proyecto") },
                        )
                        OutlinedTextField(
                            value = if (category.weightPercent == 0.0) "" else formatWeight(category.weightPercent),
                            onValueChange = { raw ->
                                raw.replace(',', '.').toDoubleOrNull()?.let {
                                    viewModel.updateCategory(index, weight = it.coerceIn(0.0, 100.0))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
            colors = teacherFieldColors(),
                            label = { Text("%") },
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.removeCategory(index) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Quitar") }
                }
                OutlinedButton(
                    onClick = viewModel::addCategory,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Agregar proyecto / examen / tarea / otro") }
            }
        }

        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text("Importar calificaciones", style = MaterialTheme.typography.titleMedium, color = V8RedColors.TextPrimary)
                Text(
                    "El archivo debe incluir Matrícula/ID y columnas con los mismos nombres de tus actividades. Se admite .xlsx y .csv.",
                    color = V8RedColors.TextSecondary,
                )
                Button(
                    onClick = {
                        fileLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "text/csv",
                                "text/comma-separated-values",
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Seleccionar Excel / CSV") }

                state.preview?.let { preview ->
                    Text("${preview.rows.size} alumnos · ${preview.columns.size} columnas")
                    Text("Columnas: " + preview.columns.joinToString())
                    if (preview.warnings.isNotEmpty()) {
                        Text(
                            preview.warnings.take(3).joinToString("\n"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        state.successMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        Button(
            onClick = viewModel::sync,
            enabled = !state.submitting && state.schemeComplete && state.preview != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.submitting) CircularProgressIndicator()
            else Text("Revisar y sincronizar calificaciones")
        }
    }
    }

}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private fun teacherFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = V8RedColors.TextPrimary,
    unfocusedTextColor = V8RedColors.TextPrimary,
    focusedLabelColor = V8RedColors.Crimson,
    unfocusedLabelColor = V8RedColors.TextSecondary,
    focusedBorderColor = V8RedColors.Crimson,
    unfocusedBorderColor = V8RedColors.Outline,
    cursorColor = V8RedColors.Crimson,
    focusedContainerColor = V8RedColors.Surface,
    unfocusedContainerColor = V8RedColors.Surface,
)
