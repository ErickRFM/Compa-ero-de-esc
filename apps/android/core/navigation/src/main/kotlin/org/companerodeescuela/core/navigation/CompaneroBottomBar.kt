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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import org.companerodeescuela.core.designsystem.theme.CompaneroElevation
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing
import org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled
import org.companerodeescuela.core.designsystem.v8.V8RedColors
import org.companerodeescuela.core.designsystem.v8.v8GlassSurface
import org.companerodeescuela.core.motion.CompaneroMotion
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

/**
 * Compact V9 bottom navigation bar featuring icons-only tabs, central highlighted QR
 * action, TalkBack semantic accessibility, and dark matte glass surface.
 */
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
    }
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    val glass = LocalV8GlassEnabled.current

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(
                horizontal = CompaneroSpacing.sm,
                vertical = CompaneroSpacing.xxs,
            )
            .then(if (glass) Modifier.v8GlassSurface(cornerRadius = 28.dp) else Modifier),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (glass) Color.Transparent else V8RedColors.Background,
        tonalElevation = if (glass) 0.dp else CompaneroElevation.raised,
        shadowElevation = if (glass) 0.dp else CompaneroElevation.immersive,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = CompaneroSpacing.xxs, vertical = 6.dp),
        ) {
            val itemWidth = maxWidth / destinations.size.coerceAtLeast(1)

            if (selectedIndex >= 0) {
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
                        .height(52.dp)
                        .then(
                            if (glass) {
                                Modifier.v8GlassSurface(
                                    cornerRadius = 20.dp,
                                    emphasized = true,
                                    elevation = 0.dp,
                                )
                            } else {
                                Modifier
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .background(V8RedColors.DeepCrimson.copy(alpha = 0.30f))
                            },
                        ),
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                destinations.forEachIndexed { index, topLevel ->
                    val isSelected = index == selectedIndex
                    val isQr = topLevel == TopLevelDestination.Attendance
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected && !reducedMotion) 1.05f else 1f,
                        animationSpec = CompaneroMotion.fast(),
                        label = "navItemScale",
                    )

                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(52.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .semantics(mergeDescendants = true) {
                                role = Role.Tab
                                selected = isSelected
                                contentDescription = topLevel.label
                            }
                            .clickable {
                                navController.navigateToTopLevel(topLevel.destination)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isQr) 48.dp else 40.dp)
                                .clip(CircleShape)
                                .background(if (isQr) V8RedColors.DeepCrimson else Color.Transparent)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = when (topLevel) {
                                    TopLevelDestination.Home,
                                    TopLevelDestination.TeacherHome,
                                    TopLevelDestination.TutorHome,
                                    TopLevelDestination.CoordinatorHome,
                                    TopLevelDestination.AdminHome -> Icons.Filled.Home
                                    TopLevelDestination.Schedule -> Icons.Filled.DateRange
                                    TopLevelDestination.Classrooms,
                                    TopLevelDestination.TutorGroups -> Icons.Filled.School
                                    TopLevelDestination.Channel,
                                    TopLevelDestination.TutorRequests -> Icons.Filled.Forum
                                    TopLevelDestination.Attendance -> Icons.Filled.QrCodeScanner
                                    TopLevelDestination.Profile -> Icons.Filled.PersonOutline
                                },
                                contentDescription = null,
                                modifier = Modifier.size(if (isQr) 24.dp else 22.dp),
                                tint = when {
                                    isQr -> Color.White
                                    isSelected -> V8RedColors.Crimson
                                    else -> V8RedColors.TextSecondary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
