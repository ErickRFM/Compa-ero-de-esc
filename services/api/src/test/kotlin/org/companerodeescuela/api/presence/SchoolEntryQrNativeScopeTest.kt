package org.companerodeescuela.api.presence

import java.time.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.*
import org.companerodeescuela.shared.contracts.*

class SchoolEntryQrNativeScopeTest {
    private inner class Fixture {
        val clock=QaClock();val accounts=InMemoryPlatformAccountRepository();val repository=InMemorySchoolEntryQrRepository()
        val admin=UserSummary("operator","Operator",roles=setOf(UserRole.ADMIN),institutionId="campus")
        val teacher=UserSummary("teacher","Teacher",roles=setOf(UserRole.TEACHER),institutionId="campus")
        lateinit var qa:QaMfaOperator;lateinit var service:SchoolEntryQrService
        suspend fun initialize() { qa=QaMfaOperator.create(accounts,admin,clock);accounts.create(ScopedAcademicFixtures.account(teacher));service=SchoolEntryQrService(repository,clock,accounts,qa.mfa,institutions=org.companerodeescuela.api.institutions.InMemoryInstitutionRepository(listOf(InstitutionSummary("campus","Campus")))) }
        fun request(op:String="create")=CreateSchoolEntryQrRequest("Main entrance",validFromEpochSeconds=clock.now.epochSecond-60,expiresAtEpochSeconds=clock.now.plusSeconds(7200).epochSecond,institutionId="campus",operationId=op)
        suspend fun signed(request:CreateSchoolEntryQrRequest)=request.copy(proof=qa.proof(StepUpAction.MANAGE_ACADEMICS,request.institutionId!!,request.operationId!!,PrivilegedCommandDigests.schoolQrCreation(request.institutionId!!,request)))
    }
    @Test fun `native QR creation requires current capability tenant and actual bound MFA`()=runTest {
        val f=Fixture();f.initialize()
        assertFailsWith<ApiException.Forbidden> { f.service.createScoped(f.teacher,f.request()) }
        assertFailsWith<ApiException.Forbidden> { f.service.createScoped(f.admin,f.request().copy(institutionId="other")) }
        assertFailsWith<ApiException.Forbidden> { f.service.createScoped(f.admin,f.request()) }
        val command=f.signed(f.request())
        assertFailsWith<ApiException.Forbidden> { f.service.createScoped(f.admin,command.copy(name="tampered")) }
        val created=f.service.createScoped(f.admin,command)
        assertEquals("campus",created.institutionId);assertNotNull(created.token)
        assertEquals(created.id,f.service.createScoped(f.admin,command).id)
        assertEquals(1,f.repository.list().size)
    }
    @Test fun `native QR listing hides foreign and unmigrated codes and does not expose token`()=runTest {
        val f=Fixture();f.initialize();val own=f.service.createScoped(f.admin,f.signed(f.request()))
        f.repository.save(StoredSchoolEntryQr(own.copy(id="foreign",institutionId="other",token=null),"foreign-hash"))
        f.repository.save(StoredSchoolEntryQr(own.copy(id="legacy",institutionId=null,token=null),"legacy-hash"))
        assertFailsWith<ApiException.Forbidden> { f.service.listScoped(f.teacher) }
        assertEquals(listOf(own.id),f.service.listScoped(f.admin).map { it.id });assertNull(f.service.listScoped(f.admin).single().token)
    }
    @Test fun `native QR revocation uses revision MFA and retains exact operation evidence`()=runTest {
        val f=Fixture();f.initialize();val own=f.service.createScoped(f.admin,f.signed(f.request()))
        val request=AcademicOperationRequest("revoke",own.revision,"")
        assertFailsWith<ApiException.Forbidden> { f.service.revokeScoped(f.admin,own.id,request) }
        val signed=request.copy(proof=f.qa.proof(StepUpAction.MANAGE_ACADEMICS,own.id,request.operationId,PrivilegedCommandDigests.schoolQrRevocation(own.id,request)))
        val revoked=f.service.revokeScoped(f.admin,own.id,signed)
        assertEquals(SchoolEntryQrStatus.REVOKED,revoked.status);assertEquals(own.revision+1,revoked.revision)
        assertEquals(request.operationId,revoked.operationId);assertEquals(f.admin.id,revoked.updatedBy)
        assertEquals(revoked,f.service.revokeScoped(f.admin,own.id,signed))
        assertNull(f.service.verifyAndRecordUse(own.token!!))
        val conflict=AcademicOperationRequest("stale",own.revision,"")
        val proof=f.qa.proof(StepUpAction.MANAGE_ACADEMICS,own.id,conflict.operationId,PrivilegedCommandDigests.schoolQrRevocation(own.id,conflict))
        assertFailsWith<ApiException.Conflict> { f.service.revokeScoped(f.admin,own.id,conflict.copy(proof=proof)) }
    }
    @Test fun `regeneration retains creation and revocation audit without duplicating replacement or secrets`()=runTest {
        val f=Fixture();f.initialize();val created=f.service.createScoped(f.admin,f.signed(f.request()))
        assertEquals(listOf("create"),created.audit.map { it.operationId })
        val request=AcademicOperationRequest("regenerate",created.revision,"")
        val signed=request.copy(proof=f.qa.proof(StepUpAction.MANAGE_ACADEMICS,created.id,request.operationId,PrivilegedCommandDigests.schoolQrRegeneration(created.id,request)))
        val result=f.service.regenerateScoped(f.admin,created.id,signed)
        assertEquals(listOf("create","regenerate"),result.revoked.audit.map { it.operationId })
        assertEquals("campus",result.replacement.institutionId);assertNotNull(result.replacement.token)
        assertNull(f.service.verifyAndRecordUse(created.token!!,"campus"))
        val retry=f.service.regenerateScoped(f.admin,created.id,signed)
        assertEquals(result.replacement.id,retry.replacement.id);assertNull(retry.replacement.token)
        assertEquals(2,f.repository.list().size)
        assertFalse(result.toString().contains(result.replacement.token!!))
        assertFalse(signed.toString().contains(signed.proof))
    }

}
