package org.companerodeescuela.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.ui.component.CompaneroGroupedList
import org.companerodeescuela.core.ui.component.CompaneroHeroSurface
import org.companerodeescuela.shared.contracts.UserRole

@Composable
fun ProfileScreen(
    displayName: String?,
    roles: Set<UserRole>,
    canSwitchExperience: Boolean = false,
    onSwitchExperience: () -> Unit = {},
    onAppearance: () -> Unit,
    onLogout: () -> Unit,
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
        CompaneroHeroSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = CompanionColors.graphite,
        ) {
            Row(
                modifier = Modifier.padding(CompaneroSpacing.md),
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
                        style = MaterialTheme.typography.titleLarge,
                        color = CompanionColors.onDarkSurface,
                    )
                    Text(
                        text = roleSummary(roles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CompanionColors.onDarkSurfaceVariant,
                    )
                    Text(
                        text = "UPTlax · Sesión institucional activa",
                        style = MaterialTheme.typography.labelLarge,
                        color = CompanionColors.crimsonContainer,
                    )
                }
            }
        }

        ProfileGroup(title = "Cuenta") {
            ProfileRow(
                title = "Institución",
                detail = "Universidad Politécnica de Tlaxcala",
                trailingLabel = "UPTlax",
            )
            GroupDivider()
            ProfileRow(
                title = "Sesión",
                detail = "Institucional activa",
            )
            GroupDivider()
            ProfileRow(
                title = "Privacidad",
                detail = "Datos académicos separados por cuenta.",
            )
        }

        if (canSwitchExperience) {
            ProfileGroup(title = "Modo de uso") {
                ProfileRow(
                    title = "Cambiar perfil activo",
                    detail = "Alterna entre tus experiencias autorizadas sin cerrar sesión.",
                    onClick = onSwitchExperience,
                )
            }
        }

        ProfileGroup(title = "Aplicación") {
            ProfileRow(
                title = "Tema y accesibilidad",
                detail = "Tema, texto, contraste y movimiento.",
                onClick = onAppearance,
            )
            GroupDivider()
            ProfileRow(
                title = "Acerca de Compañero",
                detail = "Compañero de Clase · UPTlax · UI V5.3 / V6",
            )
        }

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cerrar sesión")
        }
    }
}

@Composable
private fun ProfileGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        CompaneroGroupedList(content = content)
    }
}

@Composable
private fun ProfileRow(
    title: String,
    detail: String,
    trailingLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        )
        .padding(horizontal = CompaneroSpacing.sm, vertical = CompaneroSpacing.sm)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
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
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
