@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.feature.auth

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.core.motion.CompaneroMotionPolicy
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences
import org.companerodeescuela.core.motion.MotionRole
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
            AppExperience.GUEST,
            R.string.auth_role_guest,
            R.string.auth_guest_description,
            Icons.Filled.PersonOutline,
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
 * Direction and coordination belong to Management; preserve the legacy carousel.
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
) {
    val scope = rememberCoroutineScope()
    val reduced = LocalCompaneroMotionPreferences.current.reducedMotion
    val policy = CompaneroMotionPolicy.resolve(MotionRole.STATE_CHANGE, reduced)
    val ink = authInk()
    val muted = authMuted()
    val accent = authAccent()

    fun select(index: Int) {
        if (!enabled || index !in 0..accesses.lastIndex) return
        scope.launch {
            if (!policy.allowSpatialMotion) {
                pager.scrollToPage(index)
            } else {
                pager.animateScrollToPage(index, animationSpec = tween(policy.durationMillis))
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Compact Chips Row for fast direct role selection
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            accesses.forEachIndexed { index, access ->
                val selected = pager.currentPage == index
                val description = stringResource(R.string.auth_select_access, stringResource(access.title))

                Box(
                    modifier =
                        Modifier
                            .testTag("role_" + access.experience.name)
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.Tab,
                                onClick = { select(index) },
                            ).background(
                                color =
                                    if (selected) {
                                        accent.copy(alpha = 0.2f)
                                    } else if (authDark()) {
                                        Color(0x14FFFFFF)
                                    } else {
                                        Color(0x0A000000)
                                    },
                                shape = RoundedCornerShape(20.dp),
                            ).border(
                                border =
                                    BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) accent else muted.copy(alpha = 0.25f),
                                    ),
                                shape = RoundedCornerShape(20.dp),
                            ).padding(horizontal = 14.dp, vertical = 9.dp)
                            .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = access.icon,
                            contentDescription = null,
                            tint = if (selected) accent else muted,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(access.title),
                            color = if (selected) ink else muted,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Compact Info Banner & Pager Control
        V8FrostedGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            emphasized = true,
            themeAware = true,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { select(pager.currentPage - 1) },
                    enabled = enabled && pager.currentPage > 0,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .testTag("access_previous"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.auth_previous_access),
                        tint = if (pager.currentPage > 0 && enabled) ink else muted.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp),
                    )
                }

                HorizontalPager(
                    state = pager,
                    userScrollEnabled = enabled,
                    pageSpacing = 8.dp,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("role_pager"),
                ) { index ->
                    val access = accesses[index]
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(access.title),
                            color = ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .testTag("role_title_" + access.experience.name)
                                    .semantics { heading() },
                        )
                        Text(
                            text = stringResource(access.description),
                            color = muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(
                    onClick = { select(pager.currentPage + 1) },
                    enabled = enabled && pager.currentPage < accesses.lastIndex,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .testTag("access_next"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        stringResource(R.string.auth_next_access),
                        tint = if (pager.currentPage < accesses.lastIndex && enabled) ink else muted.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
