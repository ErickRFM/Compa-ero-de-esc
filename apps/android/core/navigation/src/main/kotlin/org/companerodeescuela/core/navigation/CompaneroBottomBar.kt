package org.companerodeescuela.core.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import org.companerodeescuela.core.designsystem.theme.CompanionColors
import org.companerodeescuela.core.designsystem.theme.CompaneroElevation
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.motion.CompaneroMotion
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

@Composable
fun CompaneroBottomBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
    destinations: List<TopLevelDestination> = TopLevelDestination.entries,
) {
    val selectedIndex = destinations.indexOfFirst { topLevel ->
        currentDestination
            ?.hierarchy
            ?.any { it.route == topLevel.destination.route } == true
    }.coerceAtLeast(0)
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion

    Surface(
        modifier = modifier.padding(
            horizontal = CompaneroSpacing.sm,
            vertical = CompaneroSpacing.xxs,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        color = V8RedColors.Background,
        tonalElevation = CompaneroElevation.raised,
        shadowElevation = CompaneroElevation.immersive,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .padding(CompaneroSpacing.xxs),
        ) {
            val itemWidth = maxWidth / destinations.size
            val targetOffset = itemWidth * selectedIndex
            val pillOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = if (reducedMotion) {
                    CompaneroMotion.fast()
                } else {
                    CompaneroMotion.snappySpring()
                },
                label = "navPillOffset",
            )

            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .width(itemWidth)
                    .height(68.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(V8RedColors.DeepCrimson.copy(alpha = 0.30f)),
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                destinations.forEachIndexed { index, topLevel ->
                    val selected = index == selectedIndex
                    val scale by animateFloatAsState(
                        targetValue = if (selected && !reducedMotion) 1.02f else 1f,
                        animationSpec = CompaneroMotion.fast(),
                        label = "navItemScale",
                    )

                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(48.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .clickable {
                                navController.navigate(topLevel.destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        ) {
                            val isQr = topLevel == TopLevelDestination.Attendance
                            Box(
                                modifier = Modifier
                                    .size(if (isQr) 45.dp else 37.dp)
                                    .clip(CircleShape)
                                    .background(if (isQr) V8RedColors.DeepCrimson else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = when (topLevel) {
                                        TopLevelDestination.Home,
                                        TopLevelDestination.TeacherHome,
                                        TopLevelDestination.CoordinatorHome,
                                        TopLevelDestination.AdminHome -> Icons.Filled.Home
                                        TopLevelDestination.Schedule -> Icons.Filled.DateRange
                                        TopLevelDestination.Classrooms -> Icons.Filled.School
                                        TopLevelDestination.Channel -> Icons.Filled.Forum
                                        TopLevelDestination.Attendance -> Icons.Filled.QrCodeScanner
                                    },
                                    contentDescription = topLevel.label,
                                    modifier = Modifier.size(if (isQr) 26.dp else 22.dp),
                                    tint = if (isQr) Color.White else if (selected) V8RedColors.Crimson else V8RedColors.TextSecondary,
                                )
                            }
                            Text(
                                text = topLevel.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) V8RedColors.Crimson else V8RedColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
