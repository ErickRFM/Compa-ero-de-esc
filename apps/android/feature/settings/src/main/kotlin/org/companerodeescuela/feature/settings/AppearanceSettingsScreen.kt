@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroAccentPalette
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard

@Composable
@OptIn(ExperimentalLayoutApi::class)
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
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = CompaneroSize.settingsContentMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
            ) {
                LiveAppearancePreviewCard()

                SettingsGroup(title = "Visualización") {
                    SettingsContentRow(
                        title = "Tema",
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
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
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
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
                        supporting = "Se combina con el tamaño de Android.",
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
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

                SettingsGroup(title = "Color y fondo") {
                    SettingsContentRow(
                        title = "Color de acento",
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
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
                            title = "Color del dispositivo",
                            supporting = "Usar los colores de tu fondo de pantalla.",
                            checked = settings.dynamicColor,
                            onCheckedChange = onDynamicColor,
                        )
                    }

                    GroupDivider()

                    ToggleRow(
                        title = "Fondo de campus",
                        supporting = "Fotografía institucional en Inicio.",
                        checked = settings.campusBackdropEnabled,
                        onCheckedChange = onCampusBackdrop,
                    )
                }

                SettingsGroup(title = "Accesibilidad") {
                    ToggleRow(
                        title = "Reducir animaciones",
                        supporting = "Sin desplazamientos ni efectos de escala.",
                        checked = settings.reducedMotion,
                        onCheckedChange = onReducedMotion,
                    )

                    GroupDivider()

                    ToggleRow(
                        title = "Mayor contraste",
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
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier
                    .semantics {
                        heading()
                    },
        )
        V8GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = CompaneroSpacing.md, vertical = CompaneroSpacing.xxs),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsContentRow(
    title: String,
    supporting: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    supporting: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = CompaneroSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun LiveAppearancePreviewCard() {
    V8GlassCard(
        modifier = Modifier.fillMaxWidth().testTag("appearance_preview"),
        emphasized = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
            Text(
                text = "Vista previa",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Cálculo Diferencial",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "08:00 – 10:00 · Edificio B, Aula 102",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
