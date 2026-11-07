@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast
import org.companerodeescuela.core.designsystem.theme.companeroAccentSurfaceColors
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8MatteBackdrop
import org.companerodeescuela.core.designsystem.v8.v8PressFeedback
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
    onInvitation: (() -> Unit)? = null,
    onInvitationIssue: (() -> Unit)? = null,
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
            ProfileIdentityCard(
                displayName = displayName,
                activeLabel = "Perfil activo · ${experienceLabel(experience, roles)}",
                managementExperience = managementExperience,
            )

            ProfileGroup(title = "Cuenta") {
                ProfileRow(
                    title = "Sistema escolar",
                    icon = Icons.Outlined.School,
                    detail = "Estado de sincronización institucional",
                    onClick = onIntegrations,
                )
                GroupDivider()
                ProfileRow(title = "Roles de la cuenta", detail = roleSummary(roles), icon = Icons.Outlined.Badge)
            }

            if (administrativeExperience &&
                (onAdminDashboard != null || onAdminSchedule != null || onAdminClassrooms != null)
            ) {
                ProfileGroup(title = "Administración") {
                    onAdminDashboard?.let { action ->
                        ProfileRow(
                            title = "Panel administrativo",
                            icon = Icons.Outlined.Dashboard,
                            detail = "",
                            onClick = action,
                        )
                    }
                    onAdminSchedule?.let { action ->
                        if (onAdminDashboard != null) GroupDivider()
                        ProfileRow(
                            title = "Horarios y grupos",
                            icon = Icons.Outlined.DateRange,
                            detail = "Edición interna con MFA de grupos y horarios.",
                            onClick = action,
                        )
                    }
                    onAdminClassrooms?.let { action ->
                        if (onAdminDashboard != null || onAdminSchedule != null) GroupDivider()
                        ProfileRow(
                            title = "Clases y canales",
                            icon = Icons.Outlined.School,
                            detail = "",
                            onClick = action,
                        )
                    }
                }
            }

            if (onSecurity != null || onInvitation != null || onInvitationIssue != null || canRequestTutor) {
                ProfileGroup(title = stringResource(R.string.profile_access)) {
                    onSecurity?.let { action ->
                        ProfileRow(
                            title = stringResource(R.string.profile_security),
                            icon = Icons.Outlined.Shield,
                            detail = stringResource(R.string.profile_security_description),
                            onClick = action,
                        )
                    }
                    onInvitation?.let { action ->
                        if (onSecurity != null) GroupDivider()
                        ProfileRow(
                            title = "Aceptar invitación",
                            icon = Icons.Outlined.PersonAdd,
                            detail = "Introduce el código recibido en tu correo",
                            onClick = action,
                        )
                    }
                    onInvitationIssue?.let { action ->
                        if (onSecurity != null || onInvitation != null) GroupDivider()
                        ProfileRow(
                            title = "Enviar invitación",
                            icon = Icons.Outlined.PersonAdd,
                            detail = "Requiere Súper Admin y autenticación en dos pasos",
                            onClick = action,
                        )
                    }
                    if (canRequestTutor) {
                        if (onSecurity != null) GroupDivider()
                        ProfileRow(
                            title = stringResource(R.string.profile_tutor_request),
                            icon = Icons.Outlined.PersonAdd,
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
                        icon = Icons.Outlined.SwitchAccount,
                        detail = "",
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
                    icon = Icons.Outlined.Palette,
                    detail = "",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAppearance()
                    },
                )
                onNotifications?.let { action ->
                    GroupDivider()
                    ProfileRow(
                        title = "Notificaciones",
                        icon = Icons.Outlined.Notifications,
                        detail = notificationsStatus ?: "Activar avisos académicos en este dispositivo.",
                        onClick = action,
                    )
                }
                GroupDivider()
                ProfileRow(
                    title = "Acerca de Compañero",
                    icon = Icons.Outlined.Info,
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
private fun ProfileIdentityCard(
    displayName: String?,
    activeLabel: String,
    managementExperience: Boolean,
) {
    val highContrast = LocalCompaneroHighContrast.current
    val accent = companeroAccentSurfaceColors()
    val ink = accent.foreground
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val name = displayName?.takeIf(String::isNotBlank) ?: "Cuenta de Compañero"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.background,
        shape = RoundedCornerShape(24.dp),
        border = if (highContrast) BorderStroke(2.dp, ink) else null,
    ) {
        Column(Modifier.padding(CompaneroSpacing.md), verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
            if (managementExperience) {
                Text("Perfil de Gestión", color = ink, style = MaterialTheme.typography.labelMedium)
            }
            if (largeText) {
                ProfileAvatar(displayName, ink, highContrast)
                Text(
                    name,
                    color = ink,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier =
                        Modifier
                            .semantics {
                                heading()
                            },
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.md)) {
                    ProfileAvatar(displayName, ink, highContrast)
                    Text(
                        name,
                        color = ink,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier =
                            Modifier
                                .weight(
                                    1f,
                                ).semantics {
                                    heading()
                                },
                    )
                }
            }
            Surface(
                color = if (highContrast) Color.Transparent else ink.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, ink.copy(alpha = if (highContrast) 1f else 0.25f)),
            ) {
                Text(
                    activeLabel,
                    color = ink,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = CompaneroSpacing.sm, vertical = CompaneroSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
    displayName: String?,
    ink: Color,
    highContrast: Boolean,
) {
    val avatarDescription =
        displayName?.trim()?.takeIf(String::isNotBlank) ?: "Cuenta de Compañero"
    Surface(
        modifier =
            Modifier
                .size(if (LocalDensity.current.fontScale >= 1.5f) 64.dp else 48.dp)
                .clearAndSetSemantics {
                    contentDescription = avatarDescription
                },
        shape = CircleShape,
        color = if (highContrast) ink else ink.copy(alpha = 0.16f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initials(displayName),
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
                fontWeight = FontWeight.Bold,
                color = if (highContrast) MaterialTheme.colorScheme.surface else ink,
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
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
private fun ProfileRow(
    title: String,
    detail: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (onClick != null) {
                    Modifier.v8PressFeedback(interactionSource).clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ).padding(vertical = CompaneroSpacing.sm)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm),
    ) {
        if (LocalDensity.current.fontScale < 1.5f) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
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
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    HorizontalDivider(
        modifier = Modifier.padding(start = if (LocalDensity.current.fontScale >= 1.5f) 0.dp else 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
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
