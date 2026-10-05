package org.companerodeescuela.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.ui.component.CompaneroGroupedList

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
            .widthIn(max = CompaneroSize.homeContentMaxWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        Text(
            text = "Apariencia",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Ajusta el producto sin perder la identidad visual de UPTlax.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsGroup(title = "Tema y texto") {
            SettingsContentRow(
                title = "Tema",
                supporting = "Crimson institucional en claro, oscuro o según el sistema.",
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

            GroupDivider()

            SettingsContentRow(
                title = "Tamaño de texto",
                supporting = "Se combina con la escala configurada en Android.",
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
        }

        SettingsGroup(title = "Accesibilidad") {
            ToggleRow(
                title = "Reducir animaciones",
                supporting = "Elimina desplazamientos innecesarios y conserva transiciones breves.",
                checked = settings.reducedMotion,
                onCheckedChange = onReducedMotion,
            )

            GroupDivider()

            ToggleRow(
                title = "Mayor contraste",
                supporting = "Refuerza bordes y separación entre superficies.",
                checked = settings.highContrast,
                onCheckedChange = onHighContrast,
            )
        }

        onOpenDebugCatalog?.let { openCatalog ->
            SettingsGroup(title = "Desarrollo") {
                SettingsContentRow(
                    title = "Sistema visual",
                    supporting = "Catálogo disponible únicamente en builds DEBUG.",
                ) {
                    OutlinedButton(
                        onClick = openCatalog,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Abrir catálogo")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        CompaneroGroupedList(content = content)
    }
}

@Composable
private fun SettingsContentRow(
    title: String,
    supporting: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(CompaneroSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
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

@Composable
private fun GroupDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
