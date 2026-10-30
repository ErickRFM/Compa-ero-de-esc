package org.companerodeescuela.api.events

import java.time.Instant
import java.util.UUID
import org.companerodeescuela.shared.contracts.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.admin.InstitutionScopePolicy
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.security.MfaService
import java.time.Clock
import java.time.LocalTime
import java.net.URI
import kotlinx.coroutines.CancellationException
import org.companerodeescuela.api.devices.PushOutboxService

class AcademicEventService(
    private val repository: AcademicEventRepository,
    private val accounts:PlatformAccountRepository?=null,
    private val groups:AcademicGroupRepository?=null,
    private val classrooms:ClassroomService?=null,
    private val institutions:InstitutionRepository?=null,
    private val mfa:MfaService?=null,
    private val clock:Clock=Clock.systemUTC(),
    private val notifications: PushOutboxService? = null,
) {
    private fun policy()=InstitutionScopePolicy(accounts ?: throw ApiException.DependencyUnavailable("Native accounts are unavailable"),clock)
    private fun id(value:String):String {
        if(value.isBlank() || value!=value.trim() || value.length>128 || value.any(Char::isISOControl)) throw ApiException.Validation("A bounded identifier is required")
        return value
    }
    private fun admin(actor:UserSummary)=actor.roles.any { it==UserRole.ADMIN || it==UserRole.SUPER_ADMIN }
    private suspend fun reader(actor:UserSummary):PlatformAccount {
        val current=policy().live(actor)
        if(current.roles.none { it in setOf(UserRole.STUDENT,UserRole.TEACHER,UserRole.TUTOR,UserRole.ADMIN,UserRole.SUPER_ADMIN,UserRole.PRINCIPAL,UserRole.COORDINATOR) }) throw ApiException.Forbidden("Academic authority is required")
        return current
    }
    private data class Scope(val institution:String,val groupRevision:Long,val groupName:String?=null,val subjectName:String?=null)
    private suspend fun scope(actor:UserSummary,event:AcademicEvent,write:Boolean):Scope {
        val current=reader(actor);val target=id(event.target.id)
        val management=admin(actor)
        if(write && !management && UserRole.TEACHER !in current.roles) throw ApiException.Forbidden("Assigned teacher or scoped administration is required")
        suspend fun institution(institution:String) {
            if(management) policy().require(actor,AdminCapability.MANAGE_ACADEMICS,institution)
            else if(current.institutionId!=institution) throw ApiException.Forbidden("Event belongs to another institution")
        }
        suspend fun group(groupId:String):AcademicGroupRecord {
            val group=groups?.find(groupId) ?: throw ApiException.Forbidden("Actual academic group is required")
            if(!group.active || group.institutionId.isNullOrBlank() || group.periodId.isNullOrBlank() || group.scopeRevision<0 || group.startsAt==null || group.endsAt==null || group.startsAt>clock.instant() || clock.instant()>=group.endsAt) throw ApiException.Forbidden("Current academic group scope is required")
            institution(group.institutionId)
            if(event.groupScopeRevision!=group.scopeRevision) throw ApiException.Forbidden("Event group scope revision is stale")
            return group
        }
        return when(event.target.scope) {
            AcademicEventScope.INSTITUTION -> {
                institution(target)
                if((institutions ?: throw ApiException.DependencyUnavailable("Institution directory is unavailable")).findEnabled(target)==null) throw ApiException.Forbidden("Enabled institution is required")
                if(write && !management) throw ApiException.Forbidden("Only scoped administration can publish institution events")
                Scope(target,-1)
            }
            AcademicEventScope.COURSE -> {
                val classService=classrooms ?: throw ApiException.DependencyUnavailable("Classroom authority is unavailable")
                val classroom=try { if(write) classService.requireCanPublish(actor,target) else classService.requireCanRead(actor,target) }
                    catch(_:ApiException.NotFound) { throw ApiException.Forbidden("Actual assigned native classroom is required") }
                val group=group(classroom.groupId ?: throw ApiException.Forbidden("Actual classroom group is required"))
                Scope(group.institutionId!!,group.scopeRevision,group.name,classroom.name)
            }
            AcademicEventScope.GROUP -> {
                val group=group(target)
                if(!management) {
                    val assigned=(classrooms ?: throw ApiException.DependencyUnavailable("Classroom authority is unavailable")).nativeChannelsFor(actor).any { it.groupId==group.id }
                    if(!assigned) throw ApiException.Forbidden("Actual group membership or teacher assignment is required")
                }
                if(write && event.type !in setOf(AcademicEventType.ANNOUNCEMENT,AcademicEventType.EVENT,AcademicEventType.CONFERENCE,AcademicEventType.ASSIGNMENT_NOTICE)) throw ApiException.Validation("Class modifications require a concrete classroom occurrence")
                Scope(group.institutionId!!,group.scopeRevision,group.name)
            }
            AcademicEventScope.STUDENT -> {
                val student=accounts?.findById(target) ?: throw ApiException.Forbidden("Actual student is required")
                val institutionId=student.institutionId ?: throw ApiException.Forbidden("Actual student institution is required")
                institution(institutionId)
                if(!student.permitsSession || student.emailVerifiedAt==null || UserRole.STUDENT !in student.roles) throw ApiException.Forbidden("Active verified student is required")
                if(!management && !(UserRole.STUDENT in current.roles && current.id==student.id && !write)) {
                    val assigned=(classrooms ?: throw ApiException.DependencyUnavailable("Classroom authority is unavailable")).nativeChannelsFor(actor)
                    var canAccess=false
                    for(classroom in assigned) if(classroom.teacherId==current.id && classroom.groupId!=null && groups?.isUserInGroup(student.id,classroom.groupId!!)==true) { canAccess=true;break }
                    if(!canAccess) throw ApiException.Forbidden("Student is not in a currently assigned classroom group")
                }
                Scope(institutionId,-1)
            }
            AcademicEventScope.CAREER,AcademicEventScope.CLASS_OCCURRENCE -> throw ApiException.Forbidden("This event target needs an explicit institutional or native occurrence adapter")
        }
    }
    private suspend fun read(actor:UserSummary,event:AcademicEvent) {
        if(event.institutionId.isNullOrBlank() || event.revision<1) throw ApiException.Forbidden("Unscoped event requires explicit migration")
        val actual=scope(actor,event,false)
        if(actual.institution!=event.institutionId) throw ApiException.Forbidden("Event institution does not match its actual resource")
    }
    suspend fun listScoped(actor:UserSummary):AcademicEventFeedResponse {
        reader(actor)
        val visible=mutableListOf<AcademicEvent>()
        for(event in repository.listEvents()) {
            if(event.cancelled || event.expiresAtEpochSeconds?.let { it<=clock.instant().epochSecond }==true) continue
            try { read(actor,event);visible+=event } catch(_:ApiException.Forbidden) { /* Other scopes and old rows remain private. */ }
        }
        return AcademicEventFeedResponse(visible,clock.instant().epochSecond)
    }
    suspend fun getScoped(actor:UserSummary,id:String):AcademicEvent? {
        reader(actor)
        val event=repository.findById(this.id(id)) ?: return null
        read(actor,event)
        return event
    }
    private suspend fun proof(actor:UserSummary,resource:String,operation:String,digest:String,proof:String?) {
        if(admin(actor)) (mfa ?: throw ApiException.DependencyUnavailable("MFA is unavailable")).requireStepUp(policy().live(actor),proof ?: throw ApiException.Forbidden("Scoped MFA proof is required"),StepUpAction.MANAGE_ACADEMICS,resource,operation,digest)
    }
    private suspend fun writer(actor:UserSummary,event:AcademicEvent) {
        read(actor,event)
        if(admin(actor)) return
        val current=reader(actor)
        if(event.source!=AcademicEventSource.TEACHER || event.sourceId!=current.id || event.authorRoleGeneration!=current.roleGenerations[UserRole.TEACHER]) throw ApiException.Forbidden("Only the current original teacher or scoped administration may edit this event")
        scope(actor,event,true)
    }
    private fun revision(value:Long) { if(value<0 || value==Long.MAX_VALUE) throw ApiException.Validation("Bounded event revision is required") }
    private fun validate(event:AcademicEvent) {
        if(event.title.trim().length !in 1..200 || event.title.any(Char::isISOControl) || (event.body?.length ?: 0)>2000) throw ApiException.Validation("Bounded event title and body are required")
        if(event.expiresAtEpochSeconds?.let { it<=clock.instant().epochSecond || it>4102444800L }==true) throw ApiException.Validation("Event expiration is invalid")
        for(time in listOf(event.previousStartsAt,event.newStartsAt,event.previousEndsAt,event.newEndsAt)) if(time!=null && runCatching { LocalTime.parse(time) }.isFailure) throw ApiException.Validation("Event time must be HH:mm")
        if(listOf(event.previousRoom,event.newRoom).any { (it?.length ?: 0)>120 }) throw ApiException.Validation("Event room is too long")
        val onlineUrl=event.onlineUrl
        if(onlineUrl!=null && (onlineUrl.length>2048 || runCatching { URI(onlineUrl).let { it.scheme=="https" && !it.host.isNullOrBlank() && it.userInfo==null } }.getOrDefault(false)!=true)) throw ApiException.Validation("Event online link must be an HTTPS URL")
    }
    suspend fun writeScoped(actor:UserSummary,id:String?,request:AcademicEventWriteRequest):AcademicEvent {
        val current=reader(actor);val operation=this.id(request.operationId);revision(request.expectedRevision)
        if(id==null && request.event.id.isNotBlank()) throw ApiException.Validation("Event ID is issued by the backend")
        if(id!=null && request.event.id.isNotBlank() && request.event.id!=id) throw ApiException.Validation("Event body ID does not match its path")
        validate(request.event)
        val actual=scope(actor,request.event,true)
        val resource=id ?: request.event.target.id
        val digest=PrivilegedCommandDigests.eventWrite(resource,request)
        proof(actor,resource,operation,digest,request.proof)
        val eventId=id?.let(::id) ?: "EVT-"+UUID.nameUUIDFromBytes((current.id+"\u0000"+operation).toByteArray(Charsets.UTF_8))
        val existing=repository.findById(eventId)
        if(existing!=null) {
            writer(actor,existing)
            if(existing.institutionId!=actual.institution) throw ApiException.Forbidden("Existing event institution is immutable")
            if(existing.revision==request.expectedRevision+1 && existing.operationId==operation && existing.commandDigest==digest && existing.audit.lastOrNull()?.actorId==current.id) {
                enqueueStudentNotification(existing)
                return existing
            }
            if(id==null || existing.revision!=request.expectedRevision || existing.cancelled) throw ApiException.Conflict("Event revision or operation changed")
        } else if(id!=null || request.expectedRevision!=0L) throw ApiException.Conflict("Existing event revision is required")
        val audit=existing?.audit.orEmpty()
        if(audit.size>=1000) throw ApiException.Conflict("Event audit capacity requires archival")
        val candidate=request.event.copy(id=eventId,source=existing?.source ?: if(admin(actor)) AcademicEventSource.ADMIN else AcademicEventSource.TEACHER,
            sourceId=existing?.sourceId ?: current.id,sourceDisplayName=existing?.sourceDisplayName ?: current.displayName,
            courseId=request.event.target.id.takeIf { request.event.target.scope==AcademicEventScope.COURSE },occurrenceId=null,
            subjectName=actual.subjectName,groupName=actual.groupName,createdAtEpochSeconds=existing?.createdAtEpochSeconds ?: clock.instant().epochSecond,
            institutionId=actual.institution,groupScopeRevision=actual.groupRevision,revision=request.expectedRevision+1,operationId=operation,commandDigest=digest,
            authorRoleGeneration=existing?.authorRoleGeneration ?: (current.roleGenerations[UserRole.TEACHER] ?: -1),cancelled=false,
            audit=audit+AcademicEventAuditEntry(current.id,operation,digest,request.expectedRevision,clock.instant().epochSecond))
        val saved = repository.saveScoped(candidate,request.expectedRevision) ?: throw ApiException.Conflict("Event changed concurrently")
        enqueueStudentNotification(saved)
        return saved
    }
    private suspend fun enqueueStudentNotification(event: AcademicEvent) {
        if (event.target.scope != AcademicEventScope.STUDENT || event.cancelled) return
        try {
            // The target has already passed current-account, role and institution checks in scope().
            // Keep private event content off lock screens; the authenticated feed has the full detail.
            notifications?.enqueueForOwner(
                eventId = "academic:${event.id}:${event.revision}",
                owner = "NATIVE:${event.target.id}",
                title = "Nuevo aviso académico",
                body = "Tienes una actualización en Compañero de Clase.",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The event has already committed: a push transport outage cannot roll it back.
            // Client retries with the same operation can safely re-enqueue the deterministic ID.
        }
    }

    suspend fun cancelScoped(actor:UserSummary,id:String,request:AcademicOperationRequest):Boolean {
        val event=repository.findById(this.id(id)) ?: throw ApiException.NotFound("Event was not found")
        writer(actor,event);val operation=this.id(request.operationId);revision(request.expectedRevision)
        val digest=PrivilegedCommandDigests.eventCancellation(id,request)
        proof(actor,id,operation,digest,request.proof)
        if(event.cancelled && event.revision==request.expectedRevision+1 && event.operationId==operation && event.commandDigest==digest && event.audit.lastOrNull()?.actorId==actor.id) return true
        if(event.revision!=request.expectedRevision || event.cancelled) throw ApiException.Conflict("Event revision changed")
        if(event.audit.size>=1000) throw ApiException.Conflict("Event audit capacity requires archival")
        val candidate=event.copy(cancelled=true,revision=request.expectedRevision+1,operationId=operation,commandDigest=digest,
            audit=event.audit+AcademicEventAuditEntry(actor.id,operation,digest,request.expectedRevision,clock.instant().epochSecond,true))
        return repository.saveScoped(candidate,request.expectedRevision)!=null || throw ApiException.Conflict("Event changed concurrently")
    }
    internal suspend fun listEvents(scopeIds: Set<String>? = null): AcademicEventFeedResponse {
        val events = repository.listEvents(scopeIds)
        return AcademicEventFeedResponse(
            events = events,
            generatedAtEpochSeconds = Instant.now().epochSecond,
        )
    }

    internal suspend fun getEvent(id: String): AcademicEvent? {
        return repository.findById(id)
    }

    internal suspend fun createEvent(
        event: AcademicEvent,
    ): AcademicEvent {
        val finalEvent = if (event.id.isBlank()) {
            event.copy(
                id = "EVT-" + UUID.randomUUID().toString().take(8),
                createdAtEpochSeconds = Instant.now().epochSecond,
            )
        } else {
            event.copy(createdAtEpochSeconds = Instant.now().epochSecond)
        }
        return repository.save(finalEvent)
    }

    internal suspend fun updateEvent(
        id: String,
        event: AcademicEvent,
    ): AcademicEvent? {
        return repository.update(id, event.copy(id = id))
    }

    internal suspend fun deleteEvent(id: String): Boolean {
        return repository.delete(id)
    }
}
