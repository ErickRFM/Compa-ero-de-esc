package org.companerodeescuela.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing

@Composable
fun AppearanceSettingsScreen(
    settings: AppearanceSettings,
    onThemeMode: (AppThemeMode) -> Unit,
    onTextScale: (Float) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
    onHighContrast: (Boolean) -> Unit,
    onOpenDebugCatalog: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.lg, vertical = CompaneroSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xl),
    ) {
        SettingsSection(
            title = "Tema",
            supporting = "El color crimson de UPTlax se conserva como identidad.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                listOf(
                    AppThemeMode.SYSTEM to "Sistema",
                    AppThemeMode.LIGHT to "Claro",
                    AppThemeMode.DARK to "Oscuro",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = { Text(label) },
                    )
                }
            }
        }

        SettingsSection(
            title = "Tamaño de texto",
            supporting = "Se combina con el tamaño de fuente configurado en Android.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
            ) {
                listOf(
                    0.9f to "Compacto",
                    1f to "Normal",
                    1.2f to "Grande",
                ).forEach { (scale, label) ->
                    FilterChip(
                        selected = kotlin.math.abs(settings.textScale - scale) < 0.01f,
                        onClick = { onTextScale(scale) },
                        label = { Text(label) },
                    )
                }
            }
        }

        ToggleRow(
            title = "Reducir animaciones",
            supporting = "Simplifica desplazamientos y transiciones expresivas.",
            checked = settings.reducedMotion,
            onCheckedChange = onReducedMotion,
        )

        ToggleRow(
            title = "Mayor contraste",
            supporting = "Refuerza bordes y separación entre superficies.",
            checked = settings.highContrast,
            onCheckedChange = onHighContrast,
        )

        onOpenDebugCatalog?.let { openCatalog ->
            SettingsSection(
                title = "Herramientas de desarrollo",
                supporting = "Disponible únicamente en builds DEBUG.",
            ) {
                OutlinedButton(
                    onClick = openCatalog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Abrir catálogo del sistema visual")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    supporting: String,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
