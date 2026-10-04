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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.shape.CircleShape
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.shared.contracts.UserRole

@Composable
fun ProfileScreen(
    displayName: String?,
    roles: Set<UserRole>,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CompaneroSpacing.lg, vertical = CompaneroSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
    ) {
        Text(
            text = "Tu cuenta",
            style = MaterialTheme.typography.titleLarge,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = CompanionColors.graphite,
        ) {
            Column(
                modifier = Modifier.padding(CompaneroSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
            ) {
                Text(
                    text = "UPTx",
                    style = MaterialTheme.typography.labelLarge,
                    color = CompanionColors.crimsonContainer,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.md),
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
                    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs)) {
                        Text(
                            text = displayName?.takeIf(String::isNotBlank)
                                ?: "Cuenta institucional",
                            style = MaterialTheme.typography.headlineSmall,
                            color = CompanionColors.onDarkSurface,
                        )
                        Text(
                            text = roleSummary(roles),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CompanionColors.onDarkSurfaceVariant,
                        )
                    }
                }
            }
        }

        ProfileRow(
            title = "Institución",
            detail = "Universidad Politécnica de Tlaxcala",
            trailingLabel = "UPTx",
        )
        ProfileRow(title = "Sesión", detail = "Institucional activa")
        ProfileRow(
            title = "Privacidad",
            detail = "La información académica guardada permanece separada por cuenta y sólo se consulta con una sesión válida.",
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
            modifier = Modifier.padding(CompaneroSpacing.md),
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
            onLogout = {},
        )
    }
}
