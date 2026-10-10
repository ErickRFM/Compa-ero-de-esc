package org.companerodeescuela.api.academic

import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.AcademicProvenance
import org.companerodeescuela.shared.contracts.ManagedScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleConflictRules
import org.companerodeescuela.shared.contracts.ScheduleRecurrence
import org.companerodeescuela.shared.contracts.ScheduleShiftRules
import org.companerodeescuela.shared.contracts.UpsertScheduleBlockRequest

class AcademicScheduleManagementService(
    private val repository: AcademicScheduleOverrideRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { "SCH-" + UUID.randomUUID().toString().take(8) },
    private val accounts:org.companerodeescuela.api.auth.PlatformAccountRepository?=null,
    private val groups:org.companerodeescuela.api.academic.groups.AcademicGroupRepository?=null,
    private val mfa:org.companerodeescuela.api.security.MfaService?=null,
    private val classrooms:org.companerodeescuela.api.classroom.ClassroomService?=null,
) {
    suspend fun listScoped(actor:org.companerodeescuela.shared.contracts.UserSummary,ownerId:String):List<ManagedScheduleBlock> {
        val institution=ownerInstitution(ownerId)
        policy().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,institution)
        return repository.listForOwner(ownerId).filter { record ->
            val group=record.block.groupId?.let { groups?.find(it) }
            group!=null && group.active && group.institutionId==institution && record.institutionId==institution && record.groupScopeRevision==group.scopeRevision
        }
    }
    suspend fun upsertScoped(actor:org.companerodeescuela.shared.contracts.UserSummary,request:UpsertScheduleBlockRequest):ManagedScheduleBlock {
        val groupId=identifier(request.groupId ?: throw ApiException.Validation("Actual group is required"))
        val group=groups?.find(groupId) ?: throw ApiException.Validation("Actual group is required")
        val institution=group.institutionId ?: throw ApiException.Forbidden("Explicit institution is required")
        val current=policy().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,institution)
        if(ownerInstitution(request.ownerId)!=institution) throw ApiException.Forbidden("Schedule owner belongs to another institution")
        val operation=identifier(request.operationId ?: throw ApiException.Forbidden("Scoped MFA operation is required"))
        val proof=request.proof ?: throw ApiException.Forbidden("Scoped MFA proof is required")
        val digest=org.companerodeescuela.shared.contracts.PrivilegedCommandDigests.scheduleUpsert(request.ownerId,request)
        (mfa ?: throw ApiException.DependencyUnavailable("MFA is unavailable")).requireStepUp(current,proof,org.companerodeescuela.shared.contracts.StepUpAction.MANAGE_ACADEMICS,request.ownerId,operation,digest)
        if(request.expectedRevision<0 || request.expectedRevision==Long.MAX_VALUE || !group.active || group.scopeRevision<0 || group.scopeRevision!=request.groupScopeRevision) throw ApiException.Validation("Current bounded schedule and group revisions are required")
        val classId=identifier(request.subjectId ?: throw ApiException.Validation("Actual assigned classroom is required"))
        val classroom=(classrooms ?: throw ApiException.DependencyUnavailable("Classroom authority is unavailable")).requireCanRead(actor,classId)
        if(classroom.groupId!=group.id) throw ApiException.Validation("Classroom must belong to the schedule group")
        if(request.teacherId!=null && request.teacherId!=classroom.teacherId) throw ApiException.Validation("Teacher must match the actual assigned class")
        if(request.ownerId!=group.id) {
            val owner=accounts?.findById(request.ownerId) ?: throw ApiException.Validation("Actual schedule owner is required")
            if(org.companerodeescuela.shared.contracts.UserRole.STUDENT in owner.roles && groups.isUserInGroup(owner.id,group.id)!=true) throw ApiException.Forbidden("Student schedule owner must belong to this group")
            if(org.companerodeescuela.shared.contracts.UserRole.TEACHER in owner.roles && owner.id!=classroom.teacherId) throw ApiException.Forbidden("Teacher schedule owner must be assigned to this class")
        }
        val id=request.id?.let(::identifier) ?: "SCH-"+UUID.nameUUIDFromBytes((current.id+"\u0000"+operation).toByteArray(Charsets.UTF_8))
        val existing=repository.findById(id)
        if(existing!=null && (existing.ownerId!=request.ownerId || existing.institutionId!=institution)) throw ApiException.Forbidden("Existing schedule scope requires explicit migration")
        if(existing?.revision==request.expectedRevision+1 && existing.operationId==operation && existing.operationDigest==digest && existing.block.provenance.updatedBy==current.id) return existing
        if((existing==null && request.expectedRevision!=0L) || (existing!=null && existing.revision!=request.expectedRevision)) throw ApiException.Conflict("Schedule revision changed")
        val candidate=buildBlock(current.id,AcademicDataSource.ADMIN_MANUAL,request.copy(id=id,subjectName=classroom.name,teacherId=classroom.teacherId,teacherName=classroom.teacherDisplayName,groupName=group.name))
            .copy(institutionId=institution,groupScopeRevision=group.scopeRevision,revision=request.expectedRevision+1,operationId=operation,operationDigest=digest)
        return repository.saveScoped(candidate,request.expectedRevision) ?: throw ApiException.Conflict("Schedule changed concurrently")
    }
    suspend fun deleteScoped(actor:org.companerodeescuela.shared.contracts.UserSummary,id:String,request:org.companerodeescuela.shared.contracts.AcademicOperationRequest):Boolean {
        val current=repository.findById(identifier(id)) ?: throw ApiException.NotFound("Schedule block was not found")
        val group=current.block.groupId?.let { groups?.find(it) } ?: throw ApiException.Forbidden("Actual schedule group is required")
        val institution=group.institutionId ?: throw ApiException.Forbidden("Explicit institution is required")
        val administrator=policy().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,institution)
        if(current.institutionId!=institution) throw ApiException.Forbidden("Existing schedule scope requires explicit migration")
        identifier(request.operationId)
        if(request.expectedRevision<0 || request.expectedRevision==Long.MAX_VALUE) throw ApiException.Validation("Bounded revision is required")
        val digest=org.companerodeescuela.shared.contracts.PrivilegedCommandDigests.scheduleCancellation(id,request)
        (mfa ?: throw ApiException.DependencyUnavailable("MFA is unavailable")).requireStepUp(administrator,request.proof,org.companerodeescuela.shared.contracts.StepUpAction.MANAGE_ACADEMICS,id,request.operationId,digest)
        if(current.revision==request.expectedRevision+1 && current.operationId==request.operationId && current.operationDigest==digest && current.block.provenance.updatedBy==administrator.id) return true
        if(current.revision!=request.expectedRevision) throw ApiException.Conflict("Schedule revision changed")
        val cancelled=current.copy(block=current.block.copy(status=org.companerodeescuela.shared.contracts.BlockStatus.CANCELLED,
            provenance=current.block.provenance.copy(updatedBy=administrator.id,updatedAtEpochSeconds=clock.instant().epochSecond)),revision=current.revision+1,operationId=request.operationId,operationDigest=digest)
        repository.saveScoped(cancelled,request.expectedRevision) ?: throw ApiException.Conflict("Schedule changed concurrently")
        return true
    }
    private suspend fun ownerInstitution(ownerId:String):String {
        identifier(ownerId)
        groups?.find(ownerId)?.let { return it.institutionId ?: throw ApiException.Forbidden("Explicit group institution is required") }
        val owner=accounts?.findById(ownerId) ?: throw ApiException.Validation("Actual schedule owner is required")
        if(!owner.permitsSession || owner.accountStatus!=org.companerodeescuela.shared.contracts.AccountStatus.ACTIVE || owner.emailVerifiedAt==null || owner.emailVerifiedAt>clock.instant() ||
            owner.roles.none { it==org.companerodeescuela.shared.contracts.UserRole.TEACHER || it==org.companerodeescuela.shared.contracts.UserRole.STUDENT }) throw ApiException.Validation("Verified active academic schedule owner is required")
        return owner.institutionId ?: throw ApiException.Forbidden("Explicit owner institution is required")
    }
    private fun identifier(value:String):String {
        if(value.isBlank() || value!=value.trim() || value.length>128 || value.any(Char::isISOControl)) throw ApiException.Validation("A bounded operation or resource is required")
        return value
    }
    private fun policy()=org.companerodeescuela.api.admin.InstitutionScopePolicy(accounts ?: throw ApiException.DependencyUnavailable("Native identity authority is unavailable"),clock)
    internal suspend fun listForOwner(ownerId: String): List<ManagedScheduleBlock> {
        if (ownerId.isBlank()) throw ApiException.Validation("ownerId is required")
        return repository.listForOwner(ownerId.trim())
    }

    internal suspend fun upsert(actorId:String,source:AcademicDataSource,request:UpsertScheduleBlockRequest):ManagedScheduleBlock = repository.save(buildBlock(actorId,source,request))
    private suspend fun buildBlock(
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
        val effectiveDate = normalizeEffectiveDate(request.recurrence, request.effectiveDate)
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
            recurrence = request.recurrence,
            seriesId = request.seriesId?.trim()?.takeIf(String::isNotBlank)
                ?: existing?.block?.seriesId
                ?: if (request.recurrence == ScheduleRecurrence.WEEKLY) id else null,
            effectiveDate = effectiveDate,
        )
        val conflicts = ScheduleConflictRules.conflictsFor(
            candidate = block,
            existing = repository.listForOwner(ownerId).map { it.block },
        )
        if (conflicts.isNotEmpty()) {
            val first = conflicts.first()
            throw ApiException.Conflict(
                "Schedule conflicts with ${first.subjectName} ${first.startTime}-${first.endTime}",
            )
        }
        return ManagedScheduleBlock(
                ownerId = ownerId,
                block = block,
                reason = request.reason?.trim()?.takeIf(String::isNotBlank),
        )
    }

    internal suspend fun delete(actorId: String, id: String): Boolean {
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

    private fun normalizeEffectiveDate(
        recurrence: ScheduleRecurrence,
        raw: String?,
    ): String? {
        if (recurrence != ScheduleRecurrence.ONE_TIME) return null
        val value = raw?.trim()?.takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("effectiveDate is required for one-time classes")
        return runCatching { LocalDate.parse(value) }
            .getOrElse { throw ApiException.Validation("effectiveDate must be YYYY-MM-DD") }
            .toString()
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
