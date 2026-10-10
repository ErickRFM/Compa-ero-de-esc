package org.companerodeescuela.api.presence

import java.time.*
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.test.*
import org.companerodeescuela.shared.contracts.*

class SchoolPresenceNativeScopeTest {
    private inner class Fixture {
        val now=Instant.now();val clock=Clock.fixed(now,ZoneOffset.UTC);val qrRepo=InMemorySchoolEntryQrRepository();val repository=InMemorySchoolPresenceRepository()
        val student=UserSummary("student","Student",roles=setOf(UserRole.STUDENT),institutionId="campus")
        val teacher=UserSummary("teacher","Teacher",roles=setOf(UserRole.TEACHER),institutionId="campus")
        val accounts=InMemoryPlatformAccountRepository();lateinit var qr:SchoolEntryQrService
        fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        suspend fun initialize() {
            accounts.create(ScopedAcademicFixtures.account(student));accounts.create(ScopedAcademicFixtures.account(teacher));qr=SchoolEntryQrService(qrRepo,clock)
            for((id,institution) in listOf("own" to "campus","foreign" to "other","legacy" to null)) qrRepo.save(StoredSchoolEntryQr(SchoolEntryQrResponse(id,"Entrance",status=SchoolEntryQrStatus.ACTIVE,validFromEpochSeconds=now.epochSecond-60,expiresAtEpochSeconds=now.epochSecond+7200,createdBy="operator",createdAtEpochSeconds=now.epochSecond,institutionId=institution,operationId="op-$id",commandDigest="a".repeat(64),updatedBy="operator"),hash(id)))
        }
        fun service(fallbackInstitution:String?=null,fallbackHash:String="")=SchoolPresenceService(repository,SchoolPresencePolicy(fallbackHash,setOf("School"),emptySet(),institutionId=fallbackInstitution),SchoolEntryQrVerifier(qr,fallbackHash),clock=clock,accounts=accounts)
        fun request(token:String)=StartSchoolPresenceRequest("operation",token,SchoolNetworkEvidence(ssid="School"),now.epochSecond)
    }
    @Test fun `native student cannot start school day with another institution or unmigrated QR`()=runTest {
        val f=Fixture();f.initialize();val service=f.service()
        assertFailsWith<ApiException.Forbidden> { service.start(f.student.id,f.request("foreign"),f.student) }
        assertFailsWith<ApiException.Forbidden> { service.start(f.student.id,f.request("legacy"),f.student) }
        assertEquals(0,f.qrRepo.findById("foreign")!!.response.usageCount);assertNull(f.repository.findActiveForStudent(f.student.id,f.now.epochSecond))
        val own=service.start(f.student.id,f.request("own"),f.student)
        assertEquals("campus",own.institutionId);assertTrue(own.qrVerified);assertFalse(own.networkVerified)
    }
    @Test fun `native school day resolves live verified student authority and filters old unknown scope`()=runTest {
        val f=Fixture();f.initialize();val service=f.service()
        assertFailsWith<ApiException.Forbidden> { service.start(f.teacher.id,f.request("own"),f.teacher) }
        assertFailsWith<ApiException.Unauthorized> { service.start(f.student.id,f.request("own"),f.student.copy(institutionId="other")) }
        f.repository.save(SchoolPresenceResponse("legacy",f.student.id,f.now.epochSecond,f.now.epochSecond+3600,status=SchoolPresenceStatus.ACTIVE,qrVerified=true,networkVerified=true,networkVerificationMethod=NetworkVerificationMethod.SSID))
        assertNull(service.activeFor(f.student.id,f.student))
        val current=service.start(f.student.id,f.request("own"),f.student)
        assertEquals("campus",current.institutionId);assertFalse(current.networkVerified)
        val account=assertNotNull(f.accounts.findById(f.student.id))
        assertNotNull(f.accounts.changeIdentity(account.id,0,AccountIdentityChange("suspend","security",AccountStatus.SUSPENDED,account.roles,account.institutionId,f.now)))
        assertFailsWith<ApiException.Unauthorized> { service.activeFor(f.student.id,f.student) }
    }
    @Test fun `native legacy QR fallback requires explicitly configured matching institution`()=runTest {
        val f=Fixture();f.initialize();val hash=f.hash("configured-token")
        assertFailsWith<ApiException.Forbidden> { f.service(fallbackHash=hash).start(f.student.id,f.request("configured-token"),f.student) }
        assertFailsWith<ApiException.Forbidden> { f.service("other",hash).start(f.student.id,f.request("configured-token"),f.student) }
        assertEquals("campus",f.service("campus",hash).start(f.student.id,f.request("configured-token"),f.student).institutionId)
    }
    @Test fun `legacy fallback cannot revive a known foreign or revoked managed QR`()=runTest {
        val f=Fixture();f.initialize()
        assertFailsWith<ApiException.Forbidden> { f.service("campus",f.hash("foreign")).start(f.student.id,f.request("foreign"),f.student) }
        val own=assertNotNull(f.qrRepo.findById("own"))
        f.qrRepo.replace(own.copy(response=own.response.copy(status=SchoolEntryQrStatus.REVOKED)))
        assertFailsWith<ApiException.Forbidden> { f.service("campus",f.hash("own")).start(f.student.id,f.request("own"),f.student) }
        assertNull(f.repository.findActiveForStudent(f.student.id,f.now.epochSecond))
    }

}
