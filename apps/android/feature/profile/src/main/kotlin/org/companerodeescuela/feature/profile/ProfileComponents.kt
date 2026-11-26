@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast
import org.companerodeescuela.core.designsystem.theme.companeroAccentSurfaceColors
import org.companerodeescuela.core.designsystem.v8.v8PressFeedback

@Composable
internal fun ProfileIdentityCard(
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
internal fun ProfileRow(
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
internal fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (LocalDensity.current.fontScale >= 1.5f) 0.dp else 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
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
