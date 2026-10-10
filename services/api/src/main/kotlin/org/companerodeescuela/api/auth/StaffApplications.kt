package org.companerodeescuela.api.auth

import org.companerodeescuela.shared.contracts.*

/** Old verified registration intent can become a request; it never becomes authority by itself. */
internal fun PlatformAccount.staffApplications():List<StaffApplication> {
    if(roleApplications.isNotEmpty()) return roleApplications
    val intent=registrationIntent ?: return emptyList()
    if(accountStatus!=AccountStatus.PENDING_APPROVAL || emailVerifiedAt==null) return emptyList()
    val role=when(intent.profile) { RegistrationAccountType.TEACHER->StaffRole.TEACHER;RegistrationAccountType.TUTOR->StaffRole.TUTOR;else->return emptyList() }
    val institution=intent.requestedInstitutionId ?: return emptyList()
    val reference=intent.identityReference ?: return emptyList()
    return listOf(StaffApplication("registration-$id",role,institution,reference,intent.preferredGroup,emailVerifiedAt.epochSecond))
}
internal fun PlatformAccount.withStaffApplication(expectedRevision:Long,application:StaffApplication):PlatformAccount? {
    if(!permitsSession || accountStatus!=AccountStatus.ACTIVE || emailVerifiedAt==null || authRevision!=expectedRevision ||
        UserRole.TEACHER !in roles || UserRole.TUTOR in roles || application.role!=StaffRole.TUTOR ||
        application.status!=StaffApplicationStatus.PENDING || application.decidedBy!=null || application.decidedAtEpochSeconds!=null ||
        application.decisionOperationId!=null || application.decisionDigest!=null || application.grantedRoleGeneration!=null ||
        application.requestedAtEpochSeconds<0 || applicationRevision==Long.MAX_VALUE || roleApplications.size>=20 ||
        application.id.isBlank() || application.id.length>128 || application.id.any(Char::isISOControl) ||
        application.requestedInstitutionId.isBlank() || application.requestedInstitutionId.length>128 ||
        application.identityReference.isBlank() || application.identityReference.length>160 || application.identityReference.any(Char::isISOControl) ||
        (application.preferredGroup?.let { it.isBlank() || it.length>120 || it.any(Char::isISOControl) } == true)) return null
    roleApplications.find { it.id==application.id }?.let { return if(it==application) this else null }
    if(roleApplications.any { it.role==application.role && it.status==StaffApplicationStatus.PENDING }) return null
    return copy(roleApplications=roleApplications+application,applicationRevision=applicationRevision+1)
}
