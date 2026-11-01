package org.companerodeescuela.shared.contracts

/** Generic routing only. Academic data and permissions are always loaded after authentication. */
object AcademicChangePushContract {
    const val TYPE = "siii_academic_changed"
    const val INSTITUTION_ID = "institutionId"

    fun notificationTag(institutionId: String): String = "academic-change:$institutionId"
}
