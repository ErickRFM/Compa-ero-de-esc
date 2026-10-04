package org.companerodeescuela.core.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        color = CompanionColors.graphite,
        tonalElevation = CompaneroElevation.raised,
        shadowElevation = CompaneroElevation.immersive,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
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
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(CompanionColors.crimson),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                        ) {
                            Icon(
                                imageVector = when (topLevel) {
                                    TopLevelDestination.Home -> Icons.Filled.Home
                                    TopLevelDestination.Schedule -> Icons.Filled.DateRange
                                    TopLevelDestination.Attendance -> Icons.Filled.QrCodeScanner
                                },
                                contentDescription = topLevel.label,
                                modifier = Modifier.size(20.dp),
                                tint = if (selected) {
                                    Color.White
                                } else {
                                    CompanionColors.onDarkSurfaceVariant
                                },
                            )
                            if (selected) {
                                Text(
                                    text = topLevel.label,
                                    modifier = Modifier.padding(start = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
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
}
