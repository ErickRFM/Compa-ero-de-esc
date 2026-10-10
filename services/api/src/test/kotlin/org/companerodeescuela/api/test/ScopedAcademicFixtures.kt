package org.companerodeescuela.api.test

import java.time.Instant
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.tutoring.TutorAssignmentRecord
import org.companerodeescuela.shared.contracts.*

/** Explicit authority fixtures, used only by service privacy tests; never production seeds. */
object ScopedAcademicFixtures {
    const val INSTITUTION="qa-campus"
    const val PERIOD="qa-period"
    val END:Instant=Instant.parse("2100-01-01T00:00:00Z")
    fun group(id:String,name:String=id)=AcademicGroupRecord(id,name,true,Instant.EPOCH,INSTITUTION,PERIOD,Instant.EPOCH,END)
    fun assignment(id:String,tutor:String,group:String,actor:String="qa-admin")=TutorAssignmentRecord(id,tutor,group,true,actor,Instant.EPOCH,
        institutionId=INSTITUTION,periodId=PERIOD,startsAt=Instant.EPOCH,endsAt=END,tutorRoleGeneration=0)
    fun account(actor:UserSummary)=PlatformAccount(actor.id,actor.displayName,"${actor.id}@example.test","test-only-hash",actor.roles,
        active=actor.active,accountStatus=actor.accountStatus,authRevision=actor.authRevision,institutionId=actor.institutionId,
        emailVerifiedAt=Instant.EPOCH,adminCapabilities=if(UserRole.ADMIN in actor.roles) setOf(AdminCapability.MANAGE_ACADEMICS) else emptySet())
    suspend fun accounts(vararg actors:UserSummary)=InMemoryPlatformAccountRepository().also { repo -> actors.forEach { repo.create(account(it)) } }
}
