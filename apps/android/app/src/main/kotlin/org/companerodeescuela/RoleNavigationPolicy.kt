package org.companerodeescuela

import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.core.navigation.TopLevelDestination
import org.companerodeescuela.shared.contracts.UserRole

data class RoleNavigationConfig(
    val startDestination: Destination,
    val destinations: List<TopLevelDestination>,
)

object RoleNavigationPolicy {
    fun resolve(roles: Set<UserRole>): RoleNavigationConfig {
        val teacherOnly = UserRole.TEACHER in roles && UserRole.STUDENT !in roles
        if (teacherOnly) {
            return RoleNavigationConfig(
                startDestination = Destination.TeacherHome,
                destinations = listOf(
                    TopLevelDestination.TeacherHome,
                    TopLevelDestination.Attendance,
                ),
            )
        }

        return RoleNavigationConfig(
            startDestination = Destination.Home,
            destinations = listOf(
                TopLevelDestination.Home,
                TopLevelDestination.Schedule,
                TopLevelDestination.Attendance,
            ),
        )
    }
}
