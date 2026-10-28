package org.companerodeescuela.core.navigation

import org.companerodeescuela.shared.contracts.UserRole

/** Keeps academic actions within the selected experience; the API still authorizes each request. */
object AcademicRoutePolicy {
    fun studentActions(
        experience: AppExperience,
        roles: Set<UserRole>,
    ): Boolean = experience == AppExperience.STUDENT && UserRole.STUDENT in roles

    fun teacherActions(
        experience: AppExperience,
        roles: Set<UserRole>,
    ): Boolean = experience == AppExperience.TEACHER && UserRole.TEACHER in roles
}
