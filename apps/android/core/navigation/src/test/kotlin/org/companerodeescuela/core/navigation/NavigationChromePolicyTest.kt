package org.companerodeescuela.core.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.companerodeescuela.shared.contracts.UserRole

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
    fun `tutor requests are a primary destination`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.TUTOR))
        val tabs = config.topLevelDestinations.map { it.destination.route }
        assertFalse(isSecondaryRoute(Destination.TutorRequests.route, config.startDestination.route, tabs))
    }
}
