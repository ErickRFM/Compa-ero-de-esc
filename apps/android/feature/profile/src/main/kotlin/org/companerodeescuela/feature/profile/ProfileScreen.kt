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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.designsystem.v8.V8BrandHeader
import org.companerodeescuela.core.designsystem.v8.V8CampusBackdrop
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.shared.contracts.UserRole

/**
 * Consistent V8 account surface; navigation and role switching retain their existing callbacks.
 * No hard-coded identity or client-only role privilege changes.
 */
@Composable
fun ProfileScreen(
    displayName: String?,
    roles: Set<UserRole>,
    canSwitchExperience: Boolean = false,
    onSwitchExperience: () -> Unit = {},
    onAppearance: () -> Unit,
    onIntegrations: () -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        V8CampusBackdrop(modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = CompaneroSize.homeContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
        ) {
            V8BrandHeader()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Mi perfil",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = V8RedColors.TextPrimary,
                )
                Text(
                    text = "Tus datos, preferencias y acceso escolar en un solo lugar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = V8RedColors.TextSecondary,
                )
            }

            V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                ) {
                    Surface(
                        modifier = Modifier.size(CompaneroSize.avatar),
                        shape = CircleShape,
                        color = V8RedColors.DeepCrimson,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initials(displayName),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = V8RedColors.TextPrimary,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = displayName?.takeIf(String::isNotBlank)
                                ?: "Cuenta de Compañero",
                            style = MaterialTheme.typography.titleLarge,
                            color = V8RedColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = roleSummary(roles),
                            style = MaterialTheme.typography.bodyMedium,
                            color = V8RedColors.TextSecondary,
                        )
                        Text(
                            text = "Sesión activa",
                            style = MaterialTheme.typography.labelLarge,
                            color = V8RedColors.Success,
                        )
                    }
                }
            }

            ProfileGroup(title = "Cuenta") {
                ProfileRow(
                    title = "Cuenta",
                    detail = "Compañero de Clase",
                    trailingLabel = "Activa",
                )
                GroupDivider()
                ProfileRow(
                    title = "Sistema escolar",
                    detail = "Estado, sincronización y fuentes académicas.",
                    onClick = onIntegrations,
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
                        detail = "Alterna entre tus roles autorizados sin cerrar sesión.",
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
                    detail = "Compañero de Clase · interfaz V8",
                )
            }

            V8RedPrimaryButton(
                text = "Cerrar sesión",
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProfileGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = V8RedColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        V8GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
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
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = CompaneroSpacing.sm)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = V8RedColors.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = V8RedColors.TextSecondary,
            )
        }
        trailingLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = V8RedColors.Success,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = V8RedColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(color = V8RedColors.Outline.copy(alpha = 0.5f))
}

private fun roleSummary(roles: Set<UserRole>): String = roles
    .sortedBy(UserRole::ordinal)
    .joinToString(" · ") { role ->
        when (role) {
            UserRole.STUDENT -> "Estudiante"
            UserRole.TEACHER_PENDING -> "Docente pendiente"
            UserRole.TEACHER -> "Docente"
            UserRole.TUTOR -> "Tutoría"
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
