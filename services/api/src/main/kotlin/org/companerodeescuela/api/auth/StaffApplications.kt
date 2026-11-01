package org.companerodeescuela.api.auth

import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.StaffApplication
import org.companerodeescuela.shared.contracts.StaffApplicationStatus
import org.companerodeescuela.shared.contracts.StaffRole
import org.companerodeescuela.shared.contracts.UserRole

/** Older verified registration intentions remain requests, never grants. */
internal fun PlatformAccount.staffApplications(): List<StaffApplication> {
    if (roleApplications.isNotEmpty()) return roleApplications
    val intent = registrationIntent ?: return emptyList()
    if (accountStatus != AccountStatus.PENDING_APPROVAL || emailVerifiedAt == null) return emptyList()

    val role =
        when (intent.profile) {
            RegistrationAccountType.TEACHER -> StaffRole.TEACHER
            RegistrationAccountType.TUTOR -> StaffRole.TUTOR
            else -> return emptyList()
        }
    val institution = intent.requestedInstitutionId ?: return emptyList()
    val reference = intent.identityReference ?: return emptyList()
    return listOf(
        StaffApplication(
            id = "registration-$id",
            role = role,
            requestedInstitutionId = institution,
            identityReference = reference,
            preferredGroup = intent.preferredGroup,
            requestedAtEpochSeconds = emailVerifiedAt.epochSecond,
        ),
    )
}

/** A request is not authority; only a separate MFA-protected approval may change roles. */
internal fun PlatformAccount.withStaffApplication(
    expectedRevision: Long,
    application: StaffApplication,
): PlatformAccount? {
    val eligible =
        when (application.role) {
            StaffRole.TEACHER ->
                UserRole.STUDENT in roles &&
                    UserRole.TEACHER !in roles &&
                    roles.none {
                        it == UserRole.ADMIN ||
                            it == UserRole.SUPER_ADMIN ||
                            it == UserRole.PRINCIPAL ||
                            it == UserRole.COORDINATOR
                    }
            StaffRole.TUTOR -> UserRole.TEACHER in roles && UserRole.TUTOR !in roles
        }
    if (!permitsSession ||
        accountStatus != AccountStatus.ACTIVE ||
        emailVerifiedAt == null ||
        authRevision != expectedRevision ||
        !eligible ||
        (institutionId != null && institutionId != application.requestedInstitutionId) ||
        application.status != StaffApplicationStatus.PENDING ||
        application.decidedBy != null ||
        application.decidedAtEpochSeconds != null ||
        application.decisionOperationId != null ||
        application.decisionDigest != null ||
        application.grantedRoleGeneration != null ||
        application.requestedAtEpochSeconds < 0 ||
        applicationRevision == Long.MAX_VALUE ||
        roleApplications.size >= 20 ||
        application.id.isBlank() ||
        application.id.length > 128 ||
        application.id.any(Char::isISOControl) ||
        application.requestedInstitutionId.isBlank() ||
        application.requestedInstitutionId.length > 128 ||
        application.identityReference.isBlank() ||
        application.identityReference.length > 160 ||
        application.identityReference.any(Char::isISOControl) ||
        (application.preferredGroup?.let {
            it.isBlank() || it.length > 120 || it.any(Char::isISOControl)
        } == true)
    ) {
        return null
    }

    val existing = roleApplications.firstOrNull { it.id == application.id }
    if (existing != null) return if (existing == application) this else null
    if (roleApplications.any { it.role == application.role && it.status == StaffApplicationStatus.PENDING }) {
        return null
    }
    return copy(
        roleApplications = roleApplications + application,
        applicationRevision = applicationRevision + 1,
    )
}
