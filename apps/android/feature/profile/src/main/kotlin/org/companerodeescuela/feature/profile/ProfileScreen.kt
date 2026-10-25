@file:Suppress("ktlint:standard:function-naming")

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8MatteBackdrop
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
    onSecurity: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
    onTutorRequest: (() -> Unit)? = null,
    onRepresentatives: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (!org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled.current &&
            MaterialTheme.colorScheme.background.luminance() < 0.5f &&
            !LocalCompaneroHighContrast.current
        ) {
            V8MatteBackdrop(modifier = Modifier.matchParentSize())
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = CompaneroSize.homeContentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Mi perfil",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Tus datos, preferencias y acceso escolar en un solo lugar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text =
                                displayName?.takeIf(String::isNotBlank)
                                    ?: "Cuenta de Compañero",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = roleSummary(roles),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Sesión activa",
                            style = MaterialTheme.typography.labelLarge,
                            color = profileSuccessColor(),
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

            if (onSecurity != null ||
                (onTutorRequest != null && UserRole.TEACHER in roles && UserRole.TUTOR !in roles) ||
                (onRepresentatives != null && UserRole.STUDENT in roles)
            ) {
                ProfileGroup(title = stringResource(R.string.profile_access)) {
                    if (onRepresentatives != null && UserRole.STUDENT in roles) {
                        ProfileRow(
                            title = "Representantes de grupo",
                            detail = "Acepta o rechaza tus nombramientos de grupo.",
                            onClick = onRepresentatives,
                        )
                        if (onSecurity != null || (onTutorRequest != null && UserRole.TEACHER in roles)) GroupDivider()
                    }
                    onSecurity?.let { action ->
                        ProfileRow(
                            title = stringResource(R.string.profile_security),
                            detail = stringResource(R.string.profile_security_description),
                            onClick = action,
                        )
                    }
                    if (onTutorRequest != null && UserRole.TEACHER in roles && UserRole.TUTOR !in roles) {
                        GroupDivider()
                        ProfileRow(
                            title = stringResource(R.string.profile_tutor_request),
                            detail = stringResource(R.string.profile_tutor_description),
                            onClick = onTutorRequest,
                        )
                    }
                }
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
                onNotifications?.let { action ->
                    GroupDivider()
                    ProfileRow(
                        title = "Notificaciones",
                        detail = "Activar avisos académicos en este dispositivo.",
                        onClick = action,
                    )
                }
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
            color = MaterialTheme.colorScheme.onSurface,
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
    val rowModifier =
        Modifier
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
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
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
                color = profileSuccessColor(),
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

@Composable
private fun profileSuccessColor(): androidx.compose.ui.graphics.Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        CompanionColors.semanticGreen
    } else {
        V8RedColors.Success
    }

private fun roleSummary(roles: Set<UserRole>): String =
    roles
        .sortedBy(UserRole::ordinal)
        .joinToString(" · ") { role ->
            when (role) {
                UserRole.STUDENT -> "Estudiante"
                UserRole.WORKSHOP_PARTICIPANT -> "Participante"
                UserRole.GUEST -> "Invitado"
                UserRole.TEACHER_PENDING -> "Docente pendiente"
                UserRole.TEACHER -> "Docente"
                UserRole.TUTOR -> "Tutoría"
                UserRole.COORDINATOR -> "Coordinación"
                UserRole.ADMIN -> "Administración"
                UserRole.SUPER_ADMIN -> "Administración general"
            }
        }.ifBlank { "Rol no disponible" }

private fun initials(displayName: String?): String =
    displayName
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
