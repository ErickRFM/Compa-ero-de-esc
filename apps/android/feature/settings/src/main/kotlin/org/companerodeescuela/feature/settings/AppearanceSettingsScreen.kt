package org.companerodeescuela.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroAccentPalette
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader

@Composable
fun AppearanceSettingsScreen(
    settings: AppearanceSettings,
    onThemeMode: (AppThemeMode) -> Unit,
    onAccentPalette: (CompaneroAccentPalette) -> Unit = {},
    onDynamicColor: (Boolean) -> Unit = {},
    onCampusBackdrop: (Boolean) -> Unit = {},
    onLanguage: (AppLanguage) -> Unit = {},
    onTextScale: (Float) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
    onHighContrast: (Boolean) -> Unit,
    onOpenDebugCatalog: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .widthIn(max = CompaneroSize.homeContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        V8ScreenHeader {
            Text(
                text = "Apariencia",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            text = "Ajusta el producto sin perder la identidad visual de UPTlax.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsGroup(title = "Tema e idioma") {
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
                title = "Idioma",
                supporting = "Selecciona el idioma preferido de la aplicación.",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    listOf(
                        AppLanguage.SYSTEM to "Sistema",
                        AppLanguage.SPANISH to "Español",
                        AppLanguage.ENGLISH to "English",
                    ).forEach { (lang, label) ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { onLanguage(lang) },
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

        SettingsGroup(title = "Colores y personalización") {
            SettingsContentRow(
                title = "Color de acento",
                supporting = "Personaliza los botones, resaltados y elementos visuales clave.",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    CompaneroAccentPalette.entries.forEach { palette ->
                        FilterChip(
                            selected = !settings.dynamicColor && settings.accentPalette == palette,
                            onClick = {
                                onDynamicColor(false)
                                onAccentPalette(palette)
                            },
                            leadingIcon = {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(palette.previewColor),
                                )
                            },
                            label = { Text(palette.displayName) },
                        )
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GroupDivider()

                ToggleRow(
                    title = "Color dinámico (Material You)",
                    supporting = "Armoniza los colores de la app con el fondo de pantalla de tu dispositivo.",
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColor,
                )
            }

            GroupDivider()

            ToggleRow(
                title = "Fotografía de campus en el inicio",
                supporting = "Muestra el fondo fotográfico institucional de la universidad en la vista principal.",
                checked = settings.campusBackdropEnabled,
                onCheckedChange = onCampusBackdrop,
            )
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
        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
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
        modifier =
            Modifier
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
