@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.core.motion.CompaneroMotionPolicy
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences
import org.companerodeescuela.core.navigation.AppDistribution
import org.companerodeescuela.core.navigation.AppExperience

internal data class LoginAccess(
    val experience: AppExperience,
    val title: Int,
    val description: Int,
    val icon: ImageVector,
) {
    val canRegister: Boolean
        get() = experience == AppExperience.STUDENT || experience == AppExperience.TEACHER
}

internal val loginAccesses =
    listOf(
        LoginAccess(
            AppExperience.STUDENT,
            R.string.auth_role_student,
            R.string.auth_student_description,
            Icons.Filled.School,
        ),
        LoginAccess(
            AppExperience.TEACHER,
            R.string.auth_role_teacher,
            R.string.auth_teacher_description,
            Icons.Filled.MenuBook,
        ),
        LoginAccess(
            AppExperience.TUTOR,
            R.string.auth_role_tutor,
            R.string.auth_tutor_description,
            Icons.Filled.Groups,
        ),
        LoginAccess(
            AppExperience.ADMIN,
            R.string.auth_role_admin,
            R.string.auth_admin_description,
            Icons.Filled.AdminPanelSettings,
        ),
        LoginAccess(
            AppExperience.SUPER_ADMIN,
            R.string.auth_role_super_admin,
            R.string.auth_super_admin_description,
            Icons.Filled.VerifiedUser,
        ),
    )

/**
 * Principal and Coordinator are management distribution experiences.
 */
internal fun loginAccessesFor(distribution: AppDistribution): List<LoginAccess> =
    if (distribution == AppDistribution.MANAGEMENT) {
        listOf(
            LoginAccess(
                AppExperience.PRINCIPAL,
                R.string.auth_role_principal,
                R.string.auth_principal_description,
                Icons.Filled.Business,
            ),
            LoginAccess(
                AppExperience.COORDINATOR,
                R.string.auth_role_coordinator,
                R.string.auth_coordinator_description,
                Icons.Filled.Business,
            ),
        ) + loginAccesses.filter { distribution.allows(it.experience) }
    } else {
        loginAccesses.filter { distribution.allows(it.experience) }
    }

@Composable
internal fun LoginAccessCarousel(
    pager: PagerState,
    enabled: Boolean,
    accesses: List<LoginAccess> = loginAccesses,
    onPageSelected: (AppExperience) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val motion = LocalCompaneroMotionPreferences.current
    val currentAccess = accesses.getOrNull(pager.currentPage) ?: accesses.first()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("auth_access_pager"),
        ) { page ->
            val access = accesses[page]
            val isSelected = page == pager.currentPage

            V8FrostedGlassPanel(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .testTag("auth_access_card_${access.experience.name.lowercase()}"),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .background(
                                            color =
                                                if (isSelected) {
                                                    V8FrostedGlassPanel.AccentCrimson.copy(
                                                        alpha = 0.2f,
                                                    )
                                                } else {
                                                    V8FrostedGlassPanel.BorderMuted
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = access.icon,
                                    contentDescription = null,
                                    tint =
                                        if (isSelected) {
                                            V8FrostedGlassPanel.AccentCrimson
                                        } else {
                                            V8FrostedGlassPanel.TextMuted
                                        },
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(
                                text = stringResource(access.title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = V8FrostedGlassPanel.TextPrimary,
                                modifier = Modifier.semantics { heading() },
                            )
                        }

                        if (access.canRegister) {
                            Box(
                                modifier =
                                    Modifier
                                        .background(
                                            color = V8FrostedGlassPanel.AccentCrimson.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_create_account),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = V8FrostedGlassPanel.AccentCrimson,
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(access.description),
                        fontSize = 13.sp,
                        color = V8FrostedGlassPanel.TextSecondary,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        // Live region announcing current page for accessibility
        val accessPositionText =
            stringResource(
                R.string.auth_access_position,
                pager.currentPage + 1,
                accesses.size,
            )
        val selectedAccessText =
            stringResource(
                R.string.auth_select_access,
                stringResource(currentAccess.title),
            )

        Box(
            modifier =
                Modifier
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "$selectedAccessText, $accessPositionText"
                    },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .selectableGroup()
                        .testTag("auth_access_indicators"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val density = LocalDensity.current
                accesses.forEachIndexed { index, access ->
                    val isSelected = index == pager.currentPage
                    val accessTitle = stringResource(access.title)
                    val selectAccessLabel =
                        stringResource(
                            R.string.auth_select_access,
                            accessTitle,
                        )

                    Box(
                        modifier =
                            Modifier
                                .height(6.dp)
                                .width(if (isSelected) 24.dp else 6.dp)
                                .background(
                                    color =
                                        if (isSelected) {
                                            V8FrostedGlassPanel.AccentCrimson
                                        } else {
                                            V8FrostedGlassPanel.BorderMuted
                                        },
                                    shape = RoundedCornerShape(3.dp),
                                )
                                .selectable(
                                    selected = isSelected,
                                    role = Role.Tab,
                                    onClick = {
                                        if (enabled && index != pager.currentPage) {
                                            scope.launch {
                                                CompaneroMotionPolicy.animatePagerScroll(
                                                    pagerState = pager,
                                                    targetPage = index,
                                                    motion = motion,
                                                    density = density,
                                                )
                                                onPageSelected(accesses[index].experience)
                                            }
                                        }
                                    },
                                )
                                .semantics {
                                    contentDescription = selectAccessLabel
                                },
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = {
                        if (enabled && pager.currentPage > 0) {
                            scope.launch {
                                val prevPage = pager.currentPage - 1
                                pager.animateScrollToPage(
                                    prevPage,
                                    animationSpec = tween(durationMillis = 200),
                                )
                                onPageSelected(accesses[prevPage].experience)
                            }
                        }
                    },
                    enabled = enabled && pager.currentPage > 0,
                    modifier = Modifier.testTag("auth_access_prev_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.auth_previous_access),
                        tint =
                            if (pager.currentPage > 0) {
                                V8FrostedGlassPanel.TextPrimary
                            } else {
                                V8FrostedGlassPanel.TextMuted
                            },
                        modifier = Modifier.size(18.dp),
                    )
                }

                IconButton(
                    onClick = {
                        if (enabled && pager.currentPage < accesses.size - 1) {
                            scope.launch {
                                val nextPage = pager.currentPage + 1
                                pager.animateScrollToPage(
                                    nextPage,
                                    animationSpec = tween(durationMillis = 200),
                                )
                                onPageSelected(accesses[nextPage].experience)
                            }
                        }
                    },
                    enabled = enabled && pager.currentPage < accesses.size - 1,
                    modifier = Modifier.testTag("auth_access_next_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.auth_next_access),
                        tint =
                            if (pager.currentPage < accesses.size - 1) {
                                V8FrostedGlassPanel.TextPrimary
                            } else {
                                V8FrostedGlassPanel.TextMuted
                            },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
