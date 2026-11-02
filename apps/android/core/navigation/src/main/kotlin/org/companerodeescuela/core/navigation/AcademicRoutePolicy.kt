package org.companerodeescuela.core.navigation

import org.companerodeescuela.shared.contracts.UserRole

/** Keeps academic actions within the selected experience; the API still authorizes each request. */
object AcademicRoutePolicy {
    fun studentActions(
        experience: AppExperience,
        roles: Set<UserRole>,
    ): Boolean = experience == AppExperience.STUDENT && UserRole.STUDENT in roles

    /** Notification navigation may switch only to an actually granted experience in this APK. */
    fun channelExperience(
        current: AppExperience,
        roles: Set<UserRole>,
        distribution: AppDistribution,
    ): AppExperience? {
        val granted = RoleExperienceResolver.available(roles, distribution)
        val allowed =
            granted.filter { experience ->
                when (experience) {
                    AppExperience.STUDENT -> UserRole.STUDENT in roles
                    AppExperience.TEACHER -> UserRole.TEACHER in roles
                    AppExperience.ADMIN -> UserRole.ADMIN in roles
                    AppExperience.SUPER_ADMIN -> UserRole.SUPER_ADMIN in roles
                    AppExperience.COORDINATOR -> UserRole.COORDINATOR in roles
                    AppExperience.PRINCIPAL -> UserRole.PRINCIPAL in roles
                    else -> false
                }
            }
        return if (current in allowed) current else allowed.firstOrNull()
    }

    fun teacherActions(
        experience: AppExperience,
        roles: Set<UserRole>,
    ): Boolean = experience == AppExperience.TEACHER && UserRole.TEACHER in roles
}
