@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroAccentPalette
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8MatteBackdrop
import org.companerodeescuela.core.designsystem.v8.V8PageTitle
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.v8.V8ScreenHeader
import org.companerodeescuela.core.navigation.AppDistribution
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.core.navigation.RoleExperienceResolver
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
    notificationsStatus: String? = null,
    onTutorRequest: (() -> Unit)? = null,
    /** Callbacks navigate only; backend permissions still control protected operations. */
    managementExperience: Boolean = false,
    activeExperience: AppExperience? = null,
    onAdminDashboard: (() -> Unit)? = null,
    onAdminSchedule: (() -> Unit)? = null,
    onAdminClassrooms: (() -> Unit)? = null,
    /** Version string shown in "Acerca de Compañero", e.g. "1.4.2". Pass from app module via BuildConfig.VERSION_NAME. */
    appVersion: String? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val distribution = if (managementExperience) AppDistribution.MANAGEMENT else AppDistribution.ACADEMIC
    val availableExperiences = RoleExperienceResolver.available(roles, distribution)
    val experience =
        if (activeExperience == null) {
            RoleExperienceResolver.resolve(roles, distribution = distribution).experience
        } else {
            activeExperience.takeIf(availableExperiences::contains) ?: AppExperience.UNSUPPORTED
        }
    val administrativeExperience =
        managementExperience &&
            (experience == AppExperience.ADMIN || experience == AppExperience.SUPER_ADMIN)
    val canRequestTutor =
        experience == AppExperience.TEACHER &&
            onTutorRequest != null &&
            UserRole.TEACHER in roles &&
            UserRole.TUTOR !in roles
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
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = CompaneroSize.settingsContentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CompaneroSpacing.page, vertical = CompaneroSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.section),
        ) {
            V8ScreenHeader {
                V8PageTitle(
                    title = if (managementExperience) "Perfil de Gestión" else "Mi perfil",
                    description = "Tu cuenta, acceso y preferencias en un solo lugar.",
                )
            }

            V8GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
                ) {
                    val avatarBg = LocalCompaneroAccentPalette.current.darkContainer
                    Surface(
                        modifier = Modifier.size(CompaneroSize.avatar),
                        shape = CircleShape,
                        color = avatarBg,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initials(displayName),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White,
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
                            text = "Perfil activo · ${experienceLabel(experience, roles)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
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
                    title = "Sistema escolar",
                    detail = "Vinculación y sincronización con SIII.",
                    onClick = onIntegrations,
                )
                GroupDivider()
                ProfileRow(title = "Roles de la cuenta", detail = roleSummary(roles))
            }

            if (administrativeExperience &&
                (onAdminDashboard != null || onAdminSchedule != null || onAdminClassrooms != null)
            ) {
                ProfileGroup(title = "Administración") {
                    onAdminDashboard?.let { action ->
                        ProfileRow(
                            title = "Panel administrativo",
                            detail = "Solicitudes, cuentas y herramientas de gestión.",
                            onClick = action,
                        )
                    }
                    onAdminSchedule?.let { action ->
                        if (onAdminDashboard != null) GroupDivider()
                        ProfileRow(
                            title = "Horarios y grupos",
                            detail = "Edición interna con MFA; publicación oficial desde SIII.",
                            onClick = action,
                        )
                    }
                    onAdminClassrooms?.let { action ->
                        if (onAdminDashboard != null || onAdminSchedule != null) GroupDivider()
                        ProfileRow(
                            title = "Clases y canales",
                            detail = "Accede a los espacios autorizados de tu institución.",
                            onClick = action,
                        )
                    }
                }
            }

            if (onSecurity != null || canRequestTutor) {
                ProfileGroup(title = stringResource(R.string.profile_access)) {
                    onSecurity?.let { action ->
                        ProfileRow(
                            title = stringResource(R.string.profile_security),
                            detail = stringResource(R.string.profile_security_description),
                            onClick = action,
                        )
                    }
                    if (canRequestTutor) {
                        if (onSecurity != null) GroupDivider()
                        ProfileRow(
                            title = stringResource(R.string.profile_tutor_request),
                            detail = stringResource(R.string.profile_tutor_description),
                            onClick = onTutorRequest,
                        )
                    }
                }
            }

            if (canSwitchExperience && availableExperiences.size > 1) {
                ProfileGroup(title = "Modo de uso") {
                    ProfileRow(
                        title = "Cambiar perfil activo",
                        detail = "Alterna entre tus roles autorizados sin cerrar sesión.",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSwitchExperience()
                        },
                    )
                }
            }

            ProfileGroup(title = "Aplicación") {
                ProfileRow(
                    title = "Tema y accesibilidad",
                    detail = "Tema, texto, contraste y movimiento.",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAppearance()
                    },
                )
                onNotifications?.let { action ->
                    GroupDivider()
                    ProfileRow(
                        title = "Notificaciones",
                        detail = notificationsStatus ?: "Activar avisos académicos en este dispositivo.",
                        onClick = action,
                    )
                }
                GroupDivider()
                ProfileRow(
                    title = "Acerca de Compañero",
                    detail =
                        if (appVersion != null) {
                            "v$appVersion · " + if (managementExperience) "Compañero Gestión" else "Compañero de Clase"
                        } else if (managementExperience) {
                            "Compañero Gestión"
                        } else {
                            "Compañero de Clase"
                        },
                )
            }

            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cerrar sesión") }
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
            modifier = Modifier.semantics { heading() },
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
    onClick: (() -> Unit)? = null,
) {
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
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
                UserRole.PRINCIPAL -> "Dirección"
                UserRole.COORDINATOR -> "Coordinación"
                UserRole.ADMIN -> "Administración"
                UserRole.SUPER_ADMIN -> "Administración general"
            }
        }.ifBlank { "Rol no disponible" }

private fun experienceLabel(
    experience: AppExperience,
    roles: Set<UserRole>,
): String =
    when (experience) {
        AppExperience.STUDENT -> "Estudiante"
        AppExperience.TEACHER -> if (UserRole.TEACHER in roles) "Docente" else "Docente pendiente"
        AppExperience.TUTOR -> "Tutoría"
        AppExperience.PRINCIPAL -> "Dirección"
        AppExperience.COORDINATOR -> "Coordinación"
        AppExperience.ADMIN -> "Administración"
        AppExperience.SUPER_ADMIN -> "Administración general"
        AppExperience.GUEST -> if (UserRole.WORKSHOP_PARTICIPANT in roles) "Participante" else "Invitado"
        AppExperience.UNSUPPORTED -> "No disponible"
    }

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
