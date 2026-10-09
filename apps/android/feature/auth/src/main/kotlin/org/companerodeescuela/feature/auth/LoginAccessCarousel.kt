package org.companerodeescuela.feature.auth

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel
import org.companerodeescuela.core.motion.CompaneroMotionPolicy
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences
import org.companerodeescuela.core.motion.MotionRole
import org.companerodeescuela.core.navigation.AppExperience

internal data class LoginAccess(val experience: AppExperience, val title: Int, val description: Int, val icon: ImageVector) {
    val canRegister get() = experience == AppExperience.STUDENT || experience == AppExperience.TEACHER
}

internal val loginAccesses = listOf(
    LoginAccess(AppExperience.STUDENT, R.string.auth_role_student, R.string.auth_student_description, Icons.Filled.School),
    LoginAccess(AppExperience.TEACHER, R.string.auth_role_teacher, R.string.auth_teacher_description, Icons.Filled.MenuBook),
    LoginAccess(AppExperience.TUTOR, R.string.auth_role_tutor, R.string.auth_tutor_description, Icons.Filled.Groups),
    LoginAccess(AppExperience.ADMIN, R.string.auth_role_admin, R.string.auth_admin_description, Icons.Filled.AdminPanelSettings),
    LoginAccess(AppExperience.SUPER_ADMIN, R.string.auth_role_super_admin, R.string.auth_super_admin_description, Icons.Filled.VerifiedUser),
)

@Composable
internal fun LoginAccessCarousel(pager: PagerState, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    val reduced = LocalCompaneroMotionPreferences.current.reducedMotion
    val policy = CompaneroMotionPolicy.resolve(MotionRole.STATE_CHANGE, reduced)
    val ink = authInk()
    val muted = authMuted()
    val accent = authAccent()
    fun select(index: Int) {
        if (!enabled) return
        scope.launch {
            if (!policy.allowSpatialMotion) pager.scrollToPage(index)
            else pager.animateScrollToPage(index, animationSpec = tween(policy.durationMillis))
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { select(pager.currentPage - 1) }, enabled = enabled && pager.currentPage > 0,
                modifier = Modifier.testTag("access_previous")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.auth_previous_access), tint = muted)
            }
            Text(stringResource(R.string.auth_access_position, pager.currentPage + 1, loginAccesses.size),
                color = muted, fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            IconButton(onClick = { select(pager.currentPage + 1) }, enabled = enabled && pager.currentPage < loginAccesses.lastIndex,
                modifier = Modifier.testTag("access_next")) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.auth_next_access), tint = muted)
            }
        }
        HorizontalPager(state = pager, userScrollEnabled = enabled, pageSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth().height((172 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp).testTag("role_pager")) { index ->
            val access = loginAccesses[index]
            V8FrostedGlassPanel(
                modifier = Modifier.fillMaxSize().selectable(
                    selected = pager.currentPage == index, enabled = enabled, role = Role.Tab, onClick = { select(index) }),
                emphasized = pager.currentPage == index, themeAware = true,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(access.icon, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
                        Text(stringResource(access.title), color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).testTag("role_title_" + access.experience.name).semantics { heading() })
                    }
                    Text(stringResource(access.description), color = muted, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.Center) {
            loginAccesses.forEachIndexed { index, access ->
                val description = stringResource(R.string.auth_select_access, stringResource(access.title))
                Box(Modifier.size(48.dp).testTag("role_" + access.experience.name)
                    .selectable(selected = pager.currentPage == index, enabled = enabled, role = Role.Tab, onClick = { select(index) })
                    .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                    Box(Modifier.width(if (pager.currentPage == index) 22.dp else 8.dp).height(6.dp)
                        .background(if (pager.currentPage == index) accent else muted.copy(alpha = 0.6f), RoundedCornerShape(3.dp)))
                }
            }
        }
        Text(stringResource(R.string.auth_access_is_intention), color = muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
