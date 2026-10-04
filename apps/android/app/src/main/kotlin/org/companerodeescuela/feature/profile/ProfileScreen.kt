package org.companerodeescuela.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.CompaneroElevation
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.shared.contracts.UserRole

@Composable
fun ProfileScreen(
    displayName: String?,
    roles: Set<UserRole>,
    onAppearance: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = CompanionColors.graphite,
            tonalElevation = CompaneroElevation.raised,
            shadowElevation = CompaneroElevation.raised,
        ) {
            Row(
                modifier = Modifier.padding(CompaneroSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Surface(
                    modifier = Modifier.size(CompaneroSize.avatar),
                    shape = CircleShape,
                    color = CompanionColors.crimson,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initials(displayName),
                            style = MaterialTheme.typography.titleMedium,
                            color = CompanionColors.onDarkSurface,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
                ) {
                    Text(
                        text = displayName?.takeIf(String::isNotBlank)
                            ?: "Cuenta institucional",
                        style = MaterialTheme.typography.titleMedium,
                        color = CompanionColors.onDarkSurface,
                    )
                    Text(
                        text = roleSummary(roles) + " · UPTlax activa",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CompanionColors.onDarkSurfaceVariant,
                    )
                }
            }
        }

        Text("Cuenta", style = MaterialTheme.typography.titleMedium)
        ProfileRow(
            title = "Institución",
            detail = "Universidad Politécnica de Tlaxcala",
            trailingLabel = "UPTlax",
        )
        ProfileRow(title = "Sesión", detail = "Institucional activa")
        ProfileRow(
            title = "Privacidad",
            detail = "Datos académicos separados por cuenta.",
        )

        Text("Aplicación", style = MaterialTheme.typography.titleMedium)
        ProfileActionRow(
            title = "Tema y accesibilidad",
            detail = "Tema, texto, contraste y movimiento.",
            onClick = onAppearance,
        )
        ProfileRow(
            title = "Acerca de Compañero",
            detail = "Compañero de Clase · UPTlax · UI V5.2",
        )

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cerrar sesión")
        }
    }
}

@Composable
private fun ProfileActionRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(CompaneroSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClick) {
                Text("Abrir")
            }
        }
    }
}

@Composable
private fun ProfileRow(
    title: String,
    detail: String,
    trailingLabel: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(CompaneroSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailingLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = CompanionColors.crimson,
                )
            }
        }
    }
}

private fun roleSummary(roles: Set<UserRole>): String = roles
    .sortedBy(UserRole::ordinal)
    .joinToString(" · ") { role ->
        when (role) {
            UserRole.STUDENT -> "Estudiante"
            UserRole.TEACHER -> "Docente"
            UserRole.COORDINATOR -> "Coordinación"
            UserRole.ADMIN -> "Administración"
            UserRole.SUPER_ADMIN -> "Administración general"
        }
    }
    .ifBlank { "Rol no disponible" }

private fun initials(displayName: String?): String = displayName
    ?.trim()
    ?.split(Regex("\\s+"))
    ?.filter(String::isNotBlank)
    ?.take(2)
    ?.joinToString("") { it.first().uppercase() }
    ?.ifBlank { "CC" }
    ?: "CC"

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    CompaneroTheme {
        ProfileScreen(
            displayName = "Ana López",
            roles = setOf(UserRole.STUDENT),
            onAppearance = {},
            onLogout = {},
        )
    }
}
