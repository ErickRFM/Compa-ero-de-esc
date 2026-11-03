@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.home

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.V8GlassCard
import org.companerodeescuela.core.designsystem.v8.V8RedDimensions
import org.companerodeescuela.core.designsystem.v8.v8PressFeedback

/** The four existing student shortcuts; presentation never changes their destination. */
@Composable
fun HomeShortcutGrid(
    onOpenSchedule: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items =
        listOf(
            HomeShortcut("Horario", "Organiza tu jornada", Icons.Filled.DateRange, onOpenSchedule),
            HomeShortcut("Asistencia", "Pases y registros", Icons.Filled.CheckCircle, onOpenAttendance),
            HomeShortcut("Avisos", "Tu canal de clase", Icons.Filled.Campaign, onOpenChannel),
            HomeShortcut("Tareas", "Entregas y pendientes", Icons.AutoMirrored.Filled.Assignment, onOpenTasks),
        )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns =
            when {
                LocalDensity.current.fontScale >= 1.6f -> 1
                maxWidth >= 600.dp -> 4
                else -> 2
            }
        Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
            items.chunked(columns).forEach { group ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                    group.forEach { item ->
                        val interaction = remember { MutableInteractionSource() }
                        V8GlassCard(
                            modifier =
                                Modifier
                                    .weight(
                                        1f,
                                    ).fillMaxHeight()
                                    .v8PressFeedback(
                                        interaction,
                                    ).clip(RoundedCornerShape(V8RedDimensions.CardCorner))
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = LocalIndication.current,
                                        role = Role.Button,
                                        onClickLabel = "Abrir ${item.title}",
                                        onClick = item.onClick,
                                    ),
                            contentPadding = PaddingValues(CompaneroSpacing.md),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.sm)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                            Icon(
                                                item.icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs)) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class HomeShortcut(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)
