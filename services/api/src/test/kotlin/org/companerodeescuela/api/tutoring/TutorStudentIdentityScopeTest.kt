package org.companerodeescuela.api.tutoring

import java.time.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.academic.groups.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.*
import org.companerodeescuela.shared.contracts.*

class TutorStudentIdentityScopeTest {
    private inner class Fixture {
        val now=Instant.now();val groups=InMemoryAcademicGroupRepository();val accounts=InMemoryPlatformAccountRepository();val tutors=InMemoryTutorAssignmentRepository();val cases=InMemoryTutorCaseRepository()
        val student=UserSummary("student","Student",roles=setOf(UserRole.STUDENT),institutionId=ScopedAcademicFixtures.INSTITUTION)
        val tutor=UserSummary("tutor","Tutor",roles=setOf(UserRole.TUTOR),institutionId=ScopedAcademicFixtures.INSTITUTION)
        lateinit var service:TutorCaseService
        suspend fun initialize(verified:Boolean=true,studentInstitution:String=ScopedAcademicFixtures.INSTITUTION) {
            accounts.create(ScopedAcademicFixtures.account(student).copy(emailVerifiedAt=now.takeIf { verified },institutionId=studentInstitution));accounts.create(ScopedAcademicFixtures.account(tutor))
            groups.create(ScopedAcademicFixtures.group("9A"));groups.create(ScopedAcademicFixtures.group("8B").copy(institutionId="other"));groups.create(ScopedAcademicFixtures.group("legacy").copy(institutionId=null))
            groups.assign(AcademicGroupMembershipRecord("9A",student.id,now));tutors.create(ScopedAcademicFixtures.assignment("assignment",tutor.id,"9A"))
            service=TutorCaseService(cases,TutorAssignmentService(tutors,groups,accounts=accounts),groups)
            for(group in listOf("9A","8B","legacy")) cases.create(TutorCaseRecord(group,group,student.id,"Existing retained support",TutorCaseStatus.OPEN,tutor.id,now,now,
                institutionId=groups.find(group)?.institutionId,groupScopeRevision=0,
                notes=listOf(TutorCaseNoteRecord("note-$group","Published help for $group",TutorNoteVisibility.STUDENT_VISIBLE,tutor.id,now),TutorCaseNoteRecord("private-$group","Private detail",TutorNoteVisibility.TUTOR_INTERNAL,tutor.id,now))))
        }
    }
    @Test fun `published student notes require current verified native authority and do not trust a subject alone`()=runTest {
        val unverified=Fixture();unverified.initialize(false)
        assertFailsWith<ApiException.Forbidden> { unverified.service.myPublishedNotes(unverified.student) }
        val f=Fixture();f.initialize()
        assertFailsWith<ApiException.Unauthorized> { f.service.myPublishedNotes(f.student.copy(institutionId="other")) }
        val current=assertNotNull(f.accounts.findById(f.student.id))
        assertNotNull(f.accounts.changeIdentity(current.id,current.authRevision,AccountIdentityChange("suspend","security",AccountStatus.SUSPENDED,current.roles,current.institutionId,f.now)))
        assertFailsWith<ApiException.Unauthorized> { f.service.myPublishedNotes(f.student) }
    }
    @Test fun `published notes retain own known institution history while hiding foreign and unmigrated cases`()=runTest {
        val f=Fixture();f.initialize()
        assertEquals(listOf("9A"),f.service.myPublishedNotes(f.student).map { it.academicGroupId })
        assertEquals(3,f.cases.listForStudent(f.student.id).size)
        assertEquals(2,f.cases.find("8B")!!.notes.size)
    }
    @Test fun `tutor cannot create case from stale membership of unverified or foreign native student`()=runTest {
        val unverified=Fixture();unverified.initialize(false)
        assertFailsWith<ApiException.Forbidden> { unverified.service.create(unverified.tutor,CreateTutorCaseRequest("9A",unverified.student.id,"Academic support")) }
        val foreign=Fixture();foreign.initialize(studentInstitution="other")
        assertFailsWith<ApiException.Forbidden> { foreign.service.create(foreign.tutor,CreateTutorCaseRequest("9A",foreign.student.id,"Academic support")) }
    }
    @Test fun `current tutor assignment does not authorize historical cases with absent or stale case metadata`()=runTest {
        val f=Fixture();f.initialize()
        f.cases.create(TutorCaseRecord("unknown","9A",f.student.id,"Historical unknown scope",TutorCaseStatus.OPEN,f.tutor.id,f.now,f.now))
        f.cases.create(TutorCaseRecord("stale","9A",f.student.id,"Historical stale scope",TutorCaseStatus.OPEN,f.tutor.id,f.now,f.now,institutionId=ScopedAcademicFixtures.INSTITUTION,groupScopeRevision=7))
        assertFailsWith<ApiException.Forbidden> { f.service.detail(f.tutor,"unknown") }
        assertFailsWith<ApiException.Forbidden> { f.service.addNote(f.tutor,"stale",AddTutorCaseNoteRequest("New private note")) }
        assertEquals(listOf("9A"),f.service.list(f.tutor).map { it.id })
        assertTrue(f.cases.find("unknown")!!.notes.isEmpty())
    }

}
