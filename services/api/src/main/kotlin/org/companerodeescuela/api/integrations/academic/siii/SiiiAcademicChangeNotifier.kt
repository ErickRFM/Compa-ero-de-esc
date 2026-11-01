package org.companerodeescuela.api.integrations.academic.siii

import org.companerodeescuela.api.devices.PushOutboxService
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind

/** Generic change notices disclose no class or enrollment details on the lock screen. */
class SiiiAcademicChangeNotifier(
    private val identities: InstitutionalIdentityLinkService,
    private val push: PushOutboxService,
) {
    suspend fun owners(
        institutionId: String,
        snapshot: SiiiSectionSnapshot?,
    ): List<String> {
        if (snapshot == null) return emptyList()
        val owners = mutableSetOf<String>()
        for (teacher in snapshot.teachers) {
            identities.resolve(institutionId, InstitutionalIdentityKind.TEACHER, teacher.externalId)?.let { owners += "NATIVE:${it.id}" }
        }
        for (student in snapshot.enrollments.filter { it.status == "enrolled" && it.studentStatus == "active" }) {
            identities.resolve(institutionId, InstitutionalIdentityKind.STUDENT, student.studentExternalId)?.let {
                owners +=
                    "NATIVE:${it.id}"
            }
        }
        return owners.toList().sorted()
    }

    suspend fun enqueue(change: StoredSiiiSection) {
        for (owner in change.notificationOwners) {
            push.enqueueForOwner(
                eventId = "siii-academic:${change.institutionId}:${change.sectionId}:${change.sourceVersion}",
                owner = owner,
                title = "Información académica actualizada",
                body = "La institución actualizó tu información académica. Consulta tu horario oficial.",
                data = mapOf("type" to TYPE, "institutionId" to change.institutionId),
            )
        }
    }

    companion object {
        const val TYPE = org.companerodeescuela.shared.contracts.AcademicChangePushContract.TYPE
    }
}
