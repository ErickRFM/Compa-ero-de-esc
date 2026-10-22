package org.companerodeescuela.core.navigation

/**
 * Which independently installed product may display an authenticated experience.
 *
 * This is a presentation boundary, NOT an authorization grant. Every API action
 * must still verify live identity, capabilities, resource and institution scope.
 * LEGACY preserves the current single-APK behavior until CI/release builds are
 * migrated to separate Android application IDs.
 */
enum class AppDistribution {
    LEGACY,
    ACADEMIC,
    MANAGEMENT,
    ;

    fun allows(experience: AppExperience): Boolean =
        when (this) {
            LEGACY -> experience != AppExperience.UNSUPPORTED
            ACADEMIC ->
                experience == AppExperience.STUDENT ||
                    experience == AppExperience.TEACHER ||
                    experience == AppExperience.TUTOR
            MANAGEMENT ->
                experience == AppExperience.PRINCIPAL ||
                    experience == AppExperience.COORDINATOR ||
                    experience == AppExperience.ADMIN ||
                    experience == AppExperience.SUPER_ADMIN
        }
}
