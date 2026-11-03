@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import org.companerodeescuela.core.motion.CompaneroMotion
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

/**
 * Compact V25 floating bottom navigation bar in dark charcoal featuring
 * an active white pill container with dark icon and text.
 */
@Composable
fun CompaneroBottomBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
    destinations: List<TopLevelDestination> = TopLevelDestination.entries,
) {
    val selectedIndex =
        destinations.indexOfFirst { topLevel ->
            currentDestination
                ?.hierarchy
                ?.any { it.route == topLevel.destination.route } == true
        }
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    val fontScale = LocalDensity.current.fontScale
    val largeText = fontScale >= 1.5f
    val itemHeight = if (largeText) (44f + 28f * fontScale).dp else 56.dp

    Surface(
        modifier =
            modifier
                .navigationBarsPadding()
                .padding(
                    horizontal = 20.dp,
                    vertical = 6.dp,
                ),
        shape = CircleShape,
        color = Color(0xFF191A1C),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            val itemWidth = maxWidth / destinations.size.coerceAtLeast(1)

            if (selectedIndex >= 0) {
                val targetOffset = itemWidth * selectedIndex
                val pillOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec =
                        if (reducedMotion) {
                            snap()
                        } else {
                            CompaneroMotion.snappySpring()
                        },
                    label = "navPillOffset",
                )

                Box(
                    modifier =
                        Modifier
                            .offset(x = pillOffset)
                            .width(itemWidth)
                            .height(itemHeight - 8.dp)
                            .testTag("navigation_selection")
                            .clip(CircleShape)
                            .background(Color.White),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEachIndexed { index, topLevel ->
                    val isSelected = index == selectedIndex
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected && !reducedMotion) 1.05f else 1f,
                        animationSpec = if (reducedMotion) snap() else CompaneroMotion.fast(),
                        label = "navItemScale",
                    )

                    Box(
                        modifier =
                            Modifier
                                .width(itemWidth)
                                .height(itemHeight - 8.dp)
                                .clip(CircleShape)
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    selected = isSelected
                                    contentDescription = topLevel.label
                                }.clickable(onClickLabel = "Abrir ${topLevel.label}") {
                                    navController.navigateToTopLevel(topLevel.destination)
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector =
                                    when (topLevel) {
                                        TopLevelDestination.Home,
                                        TopLevelDestination.TeacherHome,
                                        TopLevelDestination.TutorHome,
                                        TopLevelDestination.CoordinatorHome,
                                        TopLevelDestination.AdminHome,
                                        -> Icons.Filled.Home
                                        TopLevelDestination.Schedule -> Icons.Filled.DateRange
                                        TopLevelDestination.Classrooms,
                                        TopLevelDestination.TutorGroups,
                                        -> Icons.Filled.School
                                        TopLevelDestination.Grading -> Icons.Filled.Assessment
                                        TopLevelDestination.Channel,
                                        TopLevelDestination.TutorRequests,
                                        -> Icons.Filled.Forum
                                        TopLevelDestination.Attendance -> Icons.Filled.QrCodeScanner
                                        TopLevelDestination.Profile -> Icons.Filled.PersonOutline
                                    },
                                contentDescription = null,
                                modifier =
                                    Modifier.size(20.dp).graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                tint =
                                    when {
                                        isSelected -> Color(0xFF191A1C)
                                        else -> Color(0xFF9E9E9E)
                                    },
                            )
                            // Keep the capsule usable for profiles with many destinations.
                            if (isSelected && (destinations.size <= 4 || itemWidth >= 85.dp)) {
                                Text(
                                    text =
                                        when (topLevel) {
                                            TopLevelDestination.AdminHome -> "Admin"
                                            TopLevelDestination.CoordinatorHome -> "Gestión"
                                            TopLevelDestination.Grading -> "Notas"
                                            TopLevelDestination.Attendance -> "Pase"
                                            else -> topLevel.label
                                        },
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color(0xFF191A1C),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
