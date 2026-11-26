@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast
import org.companerodeescuela.core.designsystem.v8.V8MatteBackdrop
import org.companerodeescuela.core.designsystem.v8.V8PreferenceGroup
import org.companerodeescuela.core.navigation.AppDistribution
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.core.navigation.ExperienceAccess
import org.companerodeescuela.core.navigation.ExperiencePresentation
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
    val access = ExperienceAccess.of(experience, roles, distribution)
    val administrativeExperience = access.administrativeProfileShortcuts
    val canRequestTutor = onTutorRequest != null && access.canRequestTutorRole(roles)
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
                activeLabel = "Perfil activo · ${ExperiencePresentation.experienceName(experience, roles)}",
                managementExperience = managementExperience,
            )

            V8PreferenceGroup(
                title = "Cuenta",
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                titleWeight = FontWeight.SemiBold,
            ) {
                ProfileRow(
                    title = "Sistema escolar",
                    icon = Icons.Outlined.School,
                    detail = "Estado de sincronización institucional",
                    onClick = onIntegrations,
                )
                GroupDivider()
                ProfileRow(
                    title = "Roles de la cuenta",
                    detail = ExperiencePresentation.roleSummary(roles),
                    icon = Icons.Outlined.Badge,
                )
                GroupDivider()
                ProfileRow(
                    title = if (access.teacherPending) "Validación pendiente" else "Qué puedes hacer",
                    detail = ExperiencePresentation.scopeSummary(access),
                    icon = Icons.Outlined.Info,
                )
            }

            if (administrativeExperience &&
                (onAdminDashboard != null || onAdminSchedule != null || onAdminClassrooms != null)
            ) {
                V8PreferenceGroup(
                    title = "Administración",
                    titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleWeight = FontWeight.SemiBold,
                ) {
                    onAdminDashboard?.let { action ->
                        ProfileRow(
                            title = "Panel administrativo",
                            icon = Icons.Outlined.Dashboard,
                            detail = "Solicitudes, directorio y métricas de cuentas.",
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
                            detail = "Asignación de docentes, alumnos y canales de clase.",
                            onClick = action,
                        )
                    }
                }
            }

            if (onSecurity != null || onInvitation != null || onInvitationIssue != null || canRequestTutor) {
                V8PreferenceGroup(
                    title = stringResource(R.string.profile_access),
                    titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleWeight = FontWeight.SemiBold,
                ) {
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
                        if (onSecurity != null || onInvitation != null || onInvitationIssue != null) GroupDivider()
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
                V8PreferenceGroup(
                    title = "Modo de uso",
                    titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleWeight = FontWeight.SemiBold,
                ) {
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

            V8PreferenceGroup(
                title = "Aplicación",
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                titleWeight = FontWeight.SemiBold,
            ) {
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
