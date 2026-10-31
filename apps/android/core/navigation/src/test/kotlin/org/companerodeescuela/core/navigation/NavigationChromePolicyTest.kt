package org.companerodeescuela.core.navigation

import org.companerodeescuela.shared.contracts.UserRole
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationChromePolicyTest {
    @Test
    fun `student schedule and home are tabs`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.STUDENT))
        val tabs = config.topLevelDestinations.map { it.destination.route }
        assertFalse(isSecondaryRoute(Destination.Home.route, config.startDestination.route, tabs))
        assertFalse(isSecondaryRoute(Destination.Schedule.route, config.startDestination.route, tabs))
        assertFalse(isSecondaryRoute(Destination.Profile.route, config.startDestination.route, tabs))
    }

    @Test
    fun `student integration settings and channel are secondary`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.STUDENT))
        val tabs = config.topLevelDestinations.map { it.destination.route }
        assertTrue(isSecondaryRoute(Destination.IntegrationSettings.route, config.startDestination.route, tabs))
        assertTrue(isSecondaryRoute(Destination.Channel.route, config.startDestination.route, tabs))
    }

    @Test
    fun `teacher profile and grading provide back navigation`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.TEACHER))
        val tabs = config.topLevelDestinations.map { it.destination.route }
        assertFalse(isSecondaryRoute(Destination.TeacherHome.route, config.startDestination.route, tabs))
        assertTrue(isSecondaryRoute(Destination.Profile.route, config.startDestination.route, tabs))
        assertTrue(isSecondaryRoute(Destination.Grading.route, config.startDestination.route, tabs))
    }

    @Test
    fun `null destination and unsupported root are not secondary`() {
        val config = RoleExperienceResolver.resolve(emptySet())
        assertFalse(isSecondaryRoute(null, config.startDestination.route, emptyList()))
        assertFalse(isSecondaryRoute(config.startDestination.route, config.startDestination.route, emptyList()))
    }

    @Test
    fun `coordinator does not show a misleading institution-wide publishing tab`() {
        val coordinator = RoleExperienceResolver.resolve(setOf(UserRole.COORDINATOR))
        assertFalse(coordinator.topLevelDestinations.contains(TopLevelDestination.Channel))
        assertTrue(coordinator.topLevelDestinations.contains(TopLevelDestination.Schedule))
        val multipleRoles =
            RoleExperienceResolver.resolve(
                setOf(UserRole.TEACHER, UserRole.COORDINATOR),
                AppExperience.COORDINATOR,
            )
        assertFalse(multipleRoles.topLevelDestinations.contains(TopLevelDestination.Channel))
        // The same authenticated account may still use its assigned teacher channels
        // after switching to the Teacher experience; navigation grants no extra role.
        val teacher =
            RoleExperienceResolver.resolve(
                setOf(UserRole.TEACHER, UserRole.COORDINATOR),
                AppExperience.TEACHER,
            )
        assertTrue(teacher.topLevelDestinations.contains(TopLevelDestination.Channel))
    }

    @Test
    fun `tutor requests are a primary destination`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.TUTOR))
        val tabs = config.topLevelDestinations.map { it.destination.route }
        assertFalse(isSecondaryRoute(Destination.TutorRequests.route, config.startDestination.route, tabs))
    }
}
