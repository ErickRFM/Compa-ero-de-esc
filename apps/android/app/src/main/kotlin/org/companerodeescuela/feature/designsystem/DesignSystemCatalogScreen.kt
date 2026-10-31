@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.brand.UptlaxBrand
import org.companerodeescuela.core.designsystem.theme.CompaneroAccentPalette
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroAccentPalette
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.ui.component.AcademicClassCard
import org.companerodeescuela.core.ui.component.AcademicTimelineItem
import org.companerodeescuela.core.ui.component.AnimatedCountBadge
import org.companerodeescuela.core.ui.component.ExpressiveSegmentedControl
import org.companerodeescuela.core.ui.component.HeroAcademicCard
import org.companerodeescuela.core.ui.component.NoticeTone
import org.companerodeescuela.core.ui.component.StatusNotice

@Composable
fun DesignSystemCatalogScreen(modifier: Modifier = Modifier) {
    var selectedMode by remember { mutableIntStateOf(0) }
    var badgeCount by remember { mutableIntStateOf(4) }
    var activeCatalogPalette by remember { mutableStateOf(CompaneroAccentPalette.CRIMSON) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CompaneroSpacing.lg, vertical = CompaneroSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs)) {
            UptlaxBrand(
                modifier =
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(CompaneroSize.brandLogoCompactHeight),
                tint = CompanionColors.crimson,
            )
            Text("UPTlax · UI V5", style = MaterialTheme.typography.labelLarge, color = CompanionColors.crimson)
            Text("Sistema visual", style = MaterialTheme.typography.headlineMedium)
        }

        CatalogSection(title = "Colores") {
            val colors =
                listOf(
                    "Crimson" to CompanionColors.crimson,
                    "Crimson container" to CompanionColors.crimsonContainer,
                    "Crimson subtle" to CompanionColors.crimsonSubtle,
                    "Graphite" to CompanionColors.graphite,
                    "Graphite raised" to CompanionColors.graphiteRaised,
                    "Graphite soft" to CompanionColors.graphiteSoft,
                    "Warm background" to CompanionColors.warmBackground,
                    "Warm surface" to CompanionColors.warmSurface,
                    "Warm variant" to CompanionColors.warmSurfaceVariant,
                    "Institutional gold" to CompanionColors.institutionalGold,
                    "Semantic green" to CompanionColors.semanticGreen,
                    "Semantic blue" to CompanionColors.semanticBlue,
                    "Semantic amber" to CompanionColors.semanticAmber,
                    "Semantic red" to CompanionColors.semanticRed,
                )
            colors.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
                ) {
                    pair.forEach { (name, color) ->
                        ColorToken(name, color, Modifier.weight(1f))
                    }
                }
            }
        }

        CatalogSection(title = "Paletas de acento V8") {
            Text(
                text = "Toca una paleta para previsualizar los componentes V8 con ese acento:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                CompaneroAccentPalette.entries.forEach { palette ->
                    val isSelected = palette == activeCatalogPalette
                    Surface(
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clickable { activeCatalogPalette = palette },
                        shape = CircleShape,
                        color = palette.previewColor,
                        tonalElevation = if (isSelected) 6.dp else 0.dp,
                        shadowElevation = if (isSelected) 4.dp else 0.dp,
                    ) {
                        if (isSelected) {
                            Box(contentAlignment = Alignment.Center) {
                                Surface(
                                    modifier = Modifier.size(14.dp),
                                    shape = CircleShape,
                                    color = Color.White,
                                ) {}
                            }
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalCompaneroAccentPalette provides activeCatalogPalette) {
                V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
                        Text(
                            text = "Paleta activa: ${activeCatalogPalette.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = activeCatalogPalette.previewColor,
                        )
                        Text(
                            text = "Modo de alto contraste y gradiente V8 adaptativo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        V8RedPrimaryButton(
                            text = "Botón con acento ${activeCatalogPalette.displayName}",
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        CatalogSection(title = "Tipografía") {
            Text("Título de pantalla", style = MaterialTheme.typography.headlineMedium)
            Text("Título de superficie", style = MaterialTheme.typography.titleLarge)
            Text("Texto académico", style = MaterialTheme.typography.bodyLarge)
            Text("Etiqueta semántica", style = MaterialTheme.typography.labelLarge)
        }

        CatalogSection(title = "Shapes") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                listOf(
                    "Compacto" to MaterialTheme.shapes.extraSmall,
                    "Control" to MaterialTheme.shapes.medium,
                    "Tarjeta" to MaterialTheme.shapes.large,
                    "Hero" to MaterialTheme.shapes.extraLarge,
                ).forEach { (label, shape) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = shape,
                        color = CompanionColors.crimsonContainer,
                    ) {
                        Text(label, modifier = Modifier.padding(CompaneroSpacing.xs))
                    }
                }
            }
        }

        CatalogSection(title = "Controles") {
            Row(horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                Button(onClick = {}) { Text("Primario") }
                FilledTonalButton(onClick = {}) { Text("Tonal") }
                OutlinedButton(onClick = {}) { Text("Outline") }
            }
            ExpressiveSegmentedControl(
                options = listOf("Día", "Semana"),
                selectedIndex = selectedMode,
                onSelected = { selectedMode = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.md)) {
                AnimatedCountBadge(count = badgeCount)
                OutlinedButton(onClick = { badgeCount++ }) { Text("Incrementar") }
            }
        }

        CatalogSection(title = "Académico") {
            HeroAcademicCard(
                subject = "Programación móvil",
                time = "18:00 – 19:40",
                location = "Edificio B · Aula 5",
                teacher = "Docente",
                progress = 0.42f,
                supportingText = "Termina en 36 min",
            )
            AcademicClassCard(
                subject = "Seguridad informática",
                time = "20:00 – 21:40",
                location = "Edificio A · Aula 2",
                teacher = "Docente",
                eyebrow = "Siguiente",
            )
            AcademicTimelineItem(
                time = "18:00",
                title = "Programación móvil",
                subtitle = "Edificio B · Aula 5",
                status = "Ahora",
                highlighted = true,
            )
        }

        CatalogSection(title = "Estados") {
            StatusNotice("Sin conexión", "Mostrando información guardada.", tone = NoticeTone.WARNING)
            StatusNotice("Pendiente", "El servidor aún no confirma este intento.", tone = NoticeTone.INFO)
            StatusNotice("Verificada", "Estado confirmado por el servidor.", tone = NoticeTone.SUCCESS)
            StatusNotice("No disponible", "No se pudo actualizar la información.", tone = NoticeTone.ERROR)
            CircularProgressIndicator(modifier = Modifier.size(CompaneroSize.indicator))
        }

        CatalogSection(title = "Superficie graphite") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = CompanionColors.graphite,
            ) {
                Column(
                    modifier = Modifier.padding(CompaneroSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
                ) {
                    Text("UPTlax", color = CompanionColors.crimsonContainer)
                    Text("Texto sobre superficie oscura", color = CompanionColors.onDarkSurface)
                    Text("Información secundaria", color = CompanionColors.onDarkSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CatalogSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun ColorToken(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs),
    ) {
        Surface(
            modifier = Modifier.size(CompaneroSize.avatar),
            shape = MaterialTheme.shapes.small,
            color = color,
        ) {}
        Text(name, modifier = Modifier.padding(top = CompaneroSpacing.xs))
    }
}
