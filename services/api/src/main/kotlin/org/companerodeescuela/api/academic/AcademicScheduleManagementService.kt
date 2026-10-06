package org.companerodeescuela.api.academic

import java.time.Clock
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.AcademicProvenance
import org.companerodeescuela.shared.contracts.ManagedScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleShiftRules
import org.companerodeescuela.shared.contracts.UpsertScheduleBlockRequest

class AcademicScheduleManagementService(
    private val repository: AcademicScheduleOverrideRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { "SCH-" + UUID.randomUUID().toString().take(8) },
) {
    suspend fun listForOwner(ownerId: String): List<ManagedScheduleBlock> {
        if (ownerId.isBlank()) throw ApiException.Validation("ownerId is required")
        return repository.listForOwner(ownerId.trim())
    }

    suspend fun upsert(
        actorId: String,
        source: AcademicDataSource,
        request: UpsertScheduleBlockRequest,
    ): ManagedScheduleBlock {
        requireStaffSource(source)
        val ownerId = request.ownerId.trim().takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("ownerId is required")
        val day = request.dayOfWeek.trim().uppercase()
        if (day !in VALID_DAYS) {
            throw ApiException.Validation("dayOfWeek must be MONDAY through SUNDAY")
        }
        val start = normalizeTime(request.startTime)
        val end = normalizeTime(request.endTime)
        if (!ScheduleShiftRules.isInsideAcademicDay(start, end)) {
            throw ApiException.Validation("Schedule block must be inside 07:00-20:00 and start before end")
        }
        val subjectName = request.subjectName.trim().takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("subjectName is required")
        val now = clock.instant().epochSecond
        val id = request.id?.trim()?.takeIf(String::isNotBlank) ?: newId()
        val existing = repository.findById(id)
        if (existing != null && existing.ownerId != ownerId) {
            throw ApiException.Forbidden("Schedule block belongs to another owner")
        }

        val createdAt = existing?.block?.provenance?.createdAtEpochSeconds ?: now
        val createdBy = existing?.block?.provenance?.createdBy ?: actorId
        val block = ScheduleBlock(
            id = id,
            dayOfWeek = day,
            startTime = start,
            endTime = end,
            subjectId = request.subjectId?.trim()?.takeIf(String::isNotBlank),
            subjectName = subjectName,
            teacherId = request.teacherId?.trim()?.takeIf(String::isNotBlank),
            teacherName = request.teacherName?.trim()?.takeIf(String::isNotBlank),
            room = request.room?.trim()?.takeIf(String::isNotBlank),
            groupId = request.groupId?.trim()?.takeIf(String::isNotBlank),
            groupName = request.groupName?.trim()?.takeIf(String::isNotBlank),
            provenance = AcademicProvenance(
                source = source,
                sourceId = request.sourceId?.trim()?.takeIf(String::isNotBlank),
                verified = true,
                createdBy = createdBy,
                updatedBy = actorId,
                createdAtEpochSeconds = createdAt,
                updatedAtEpochSeconds = now,
            ),
            status = request.status,
            shift = ScheduleShiftRules.forBlock(start, end),
            isContraturno = request.isContraturno,
        )
        return repository.save(
            ManagedScheduleBlock(
                ownerId = ownerId,
                block = block,
                reason = request.reason?.trim()?.takeIf(String::isNotBlank),
            ),
        )
    }

    suspend fun delete(actorId: String, id: String): Boolean {
        if (actorId.isBlank()) throw ApiException.Unauthorized()
        val normalized = id.trim().takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("id is required")
        return repository.delete(normalized)
    }

    private fun requireStaffSource(source: AcademicDataSource) {
        if (source != AcademicDataSource.ADMIN_MANUAL &&
            source != AcademicDataSource.SUPERVISOR_MANUAL
        ) {
            throw ApiException.Validation("Staff schedule source is required")
        }
    }

    private fun normalizeTime(raw: String): String {
        val parts = raw.trim().replace('.', ':').split(':')
        if (parts.size != 2) throw ApiException.Validation("Time must be HH:mm")
        val hour = parts[0].toIntOrNull() ?: throw ApiException.Validation("Time must be HH:mm")
        val minute = parts[1].toIntOrNull() ?: throw ApiException.Validation("Time must be HH:mm")
        if (hour !in 0..23 || minute !in 0..59) throw ApiException.Validation("Time must be HH:mm")
        return "%02d:%02d".format(hour, minute)
    }

    private companion object {
        val VALID_DAYS = setOf(
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY",
        )
    }
}
