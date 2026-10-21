package org.companerodeescuela.core.navigation

import org.companerodeescuela.shared.contracts.UserRole

enum class AppExperience {
    STUDENT,
    TEACHER,
    TUTOR,
    COORDINATOR,
    ADMIN,
    SUPER_ADMIN,
    GUEST,
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
    fun available(
        roles: Set<UserRole>,
        distribution: AppDistribution = AppDistribution.LEGACY,
    ): List<AppExperience> =
        buildList {
            if (UserRole.STUDENT in roles) add(AppExperience.STUDENT)
            if (UserRole.TEACHER in roles || UserRole.TEACHER_PENDING in roles) {
                add(AppExperience.TEACHER)
            }
            if (UserRole.TUTOR in roles) add(AppExperience.TUTOR)
            if (UserRole.COORDINATOR in roles) add(AppExperience.COORDINATOR)
            if (UserRole.ADMIN in roles) add(AppExperience.ADMIN)
            if (UserRole.SUPER_ADMIN in roles) add(AppExperience.SUPER_ADMIN)
            if (UserRole.GUEST in roles || UserRole.WORKSHOP_PARTICIPANT in roles) add(AppExperience.GUEST)
        }.filter(distribution::allows)

    /** A login card is an intention, never a role grant. Null asks the user to choose. */
    fun preferredAfterLogin(
        roles: Set<UserRole>,
        requested: AppExperience?,
        saved: AppExperience?,
        distribution: AppDistribution = AppDistribution.LEGACY,
    ): AppExperience? {
        val granted = available(roles, distribution)
        if (requested != null) return requested.takeIf(granted::contains)
        return saved?.takeIf(granted::contains) ?: granted.singleOrNull()
    }

    fun resolve(
        roles: Set<UserRole>,
        preferredExperience: AppExperience? = null,
        distribution: AppDistribution = AppDistribution.LEGACY,
    ): RoleExperienceConfig {
        val available = available(roles, distribution)
        val selected =
            preferredExperience
                ?.takeIf(available::contains)
                ?: available.firstOrNull()
                ?: AppExperience.UNSUPPORTED

        return when (selected) {
            AppExperience.STUDENT ->
                RoleExperienceConfig(
                    experience = selected,
                    startDestination = Destination.Home,
                    topLevelDestinations =
                        listOf(
                            TopLevelDestination.Home,
                            TopLevelDestination.Schedule,
                            TopLevelDestination.Attendance,
                            TopLevelDestination.Classrooms,
                            TopLevelDestination.Grading,
                            TopLevelDestination.Profile,
                        ),
                )
            AppExperience.TEACHER ->
                RoleExperienceConfig(
                    experience = selected,
                    startDestination = Destination.TeacherHome,
                    topLevelDestinations =
                        if (UserRole.TEACHER in roles) {
                            listOf(
                                TopLevelDestination.TeacherHome,
                                TopLevelDestination.Schedule,
                                TopLevelDestination.Attendance,
                                TopLevelDestination.Classrooms,
                                TopLevelDestination.Channel,
                            )
                        } else {
                            // Pending approval is not a teacher permission grant.
                            listOf(TopLevelDestination.TeacherHome)
                        },
                )
            AppExperience.TUTOR ->
                RoleExperienceConfig(
                    experience = selected,
                    startDestination = Destination.TutorHome,
                    topLevelDestinations =
                        listOf(
                            TopLevelDestination.TutorHome,
                            TopLevelDestination.TutorGroups,
                            TopLevelDestination.TutorRequests,
                        ),
                )
            AppExperience.COORDINATOR ->
                RoleExperienceConfig(
                    experience = selected,
                    startDestination = Destination.CoordinatorHome,
                    topLevelDestinations =
                        listOf(
                            TopLevelDestination.CoordinatorHome,
                            TopLevelDestination.Schedule,
                        ),
                )
            AppExperience.ADMIN,
            AppExperience.SUPER_ADMIN,
            ->
                RoleExperienceConfig(
                    experience = selected,
                    startDestination = Destination.AdminHome,
                    topLevelDestinations =
                        listOf(
                            TopLevelDestination.AdminHome,
                            TopLevelDestination.Schedule,
                            TopLevelDestination.Classrooms,
                            TopLevelDestination.Channel,
                        ),
                )
            AppExperience.GUEST ->
                RoleExperienceConfig(
                    experience = selected,
                    startDestination = Destination.Profile,
                    topLevelDestinations = listOf(TopLevelDestination.Profile),
                )
            AppExperience.UNSUPPORTED ->
                RoleExperienceConfig(
                    experience = selected,
                    startDestination = Destination.RoleUnavailable,
                    topLevelDestinations = emptyList(),
                )
        }
    }
}
