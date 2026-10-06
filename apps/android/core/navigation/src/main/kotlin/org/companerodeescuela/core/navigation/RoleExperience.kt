package org.companerodeescuela.core.navigation

import org.companerodeescuela.shared.contracts.UserRole

enum class AppExperience {
    STUDENT,
    TEACHER,
    COORDINATOR,
    ADMIN,
    SUPER_ADMIN,
    UNSUPPORTED,
}

data class RoleExperienceConfig(
    val experience: AppExperience,
    val startDestination: Destination,
    val topLevelDestinations: List<TopLevelDestination>,
)

/**
 * Resolves roles into an explicit product experience.
 *
 * Navigation must never infer roles ad-hoc inside individual screens. A preferred
 * experience is honored only when the authenticated role set actually grants it.
 */
object RoleExperienceResolver {
    fun available(roles: Set<UserRole>): List<AppExperience> = buildList {
        if (UserRole.STUDENT in roles) add(AppExperience.STUDENT)
        if (UserRole.TEACHER in roles || UserRole.TEACHER_PENDING in roles) {
            add(AppExperience.TEACHER)
        }
        if (UserRole.COORDINATOR in roles) add(AppExperience.COORDINATOR)
        if (UserRole.ADMIN in roles) add(AppExperience.ADMIN)
        if (UserRole.SUPER_ADMIN in roles) add(AppExperience.SUPER_ADMIN)
    }

    fun resolve(
        roles: Set<UserRole>,
        preferredExperience: AppExperience? = null,
    ): RoleExperienceConfig {
        val available = available(roles)
        val selected = preferredExperience
            ?.takeIf(available::contains)
            ?: available.firstOrNull()
            ?: AppExperience.UNSUPPORTED

        return when (selected) {
            AppExperience.STUDENT -> RoleExperienceConfig(
                experience = selected,
                startDestination = Destination.Home,
                topLevelDestinations = listOf(
                    TopLevelDestination.Home,
                    TopLevelDestination.Schedule,
                    TopLevelDestination.Classrooms,
                ),
            )
            AppExperience.TEACHER -> RoleExperienceConfig(
                experience = selected,
                startDestination = Destination.TeacherHome,
                topLevelDestinations = listOf(
                    TopLevelDestination.TeacherHome,
                    TopLevelDestination.Classrooms,
                    TopLevelDestination.Schedule,
                ),
            )
            AppExperience.COORDINATOR -> RoleExperienceConfig(
                experience = selected,
                startDestination = Destination.CoordinatorHome,
                topLevelDestinations = listOf(
                    TopLevelDestination.CoordinatorHome,
                    TopLevelDestination.Schedule,
                    TopLevelDestination.Attendance,
                    TopLevelDestination.Channel,
                ),
            )
            AppExperience.ADMIN,
            AppExperience.SUPER_ADMIN,
            -> RoleExperienceConfig(
                experience = selected,
                startDestination = Destination.AdminHome,
                topLevelDestinations = listOf(
                    TopLevelDestination.AdminHome,
                    TopLevelDestination.Schedule,
                    TopLevelDestination.Attendance,
                    TopLevelDestination.Channel,
                ),
            )
            AppExperience.UNSUPPORTED -> RoleExperienceConfig(
                experience = selected,
                startDestination = Destination.RoleUnavailable,
                topLevelDestinations = emptyList(),
            )
        }
    }
}
