package org.companerodeescuela.feature.admin

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.test.*
import org.companerodeescuela.core.common.result.*
import org.companerodeescuela.core.network.*
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.*

class AdminRepositoryTest {
    private class Store:SessionTokenStore {
        var value:String?=listOf("e30",Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"admin","session_id":"session","exp":4102444800}""".toByteArray()),"test").joinToString(".")
        override suspend fun readAccessToken()=value
        override suspend fun writeAccessToken(token:String) { value=token }
        override suspend fun clear() { value=null }
    }
    @Test fun `administration loads real users metrics approval groups and tutor assignments`()=runTest {
        val overview=AdministrationOverview(emptyList(),AdministrationMetrics(7,2,3,1,1));val group=AcademicGroupSummary("group","Actual Group",institutionId="campus",periodId="period",startsAtEpochSeconds=1,endsAtEpochSeconds=4102444800)
        val assignment=TutorAssignmentSummary("assignment","tutor","group","Actual Group",true,"admin",1)
        val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { request ->
            assertNotNull(request.headers[HttpHeaders.Authorization])
            val body=when(request.url.encodedPath) {
                "/admin/accounts" -> Json.encodeToString(ApiResponse(overview))
                "/admin/accounts/approval-groups" -> Json.encodeToString(ApiResponse(listOf(group)))
                "/tutoring/assignments" -> Json.encodeToString(ApiResponse(listOf(assignment)))
                else -> error("Unexpected endpoint")
            };respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        val repo=AdminRepository(client,Store())
        assertEquals(overview,assertIs<Outcome.Success<AdministrationOverview>>(repo.overview()).value)
        assertEquals(listOf(group),assertIs<Outcome.Success<List<AcademicGroupSummary>>>(repo.approvalGroups()).value)
        assertEquals(listOf(assignment),assertIs<Outcome.Success<List<TutorAssignmentSummary>>>(repo.assignments()).value);client.close()
    }
    @Test fun `critical administrative commands preserve exact scope proof and operation with encoded target IDs`()=runTest {
        val target=AdminUserSummary(UserSummary("target","Actual Tutor",roles=setOf(UserRole.TEACHER,UserRole.TUTOR)),emptyList())
        val assignment=TutorAssignmentSummary("assignment","tutor","group","Actual Group",false,"admin",1)
        val bodies=mutableListOf<String>();val paths=mutableListOf<String>()
        val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { request ->
            paths+=request.url.encodedPath;bodies+=(request.body as TextContent).text
            assertNotNull(request.headers[HttpHeaders.Authorization])
            val body=if(request.url.encodedPath.startsWith("/tutoring")) Json.encodeToString(ApiResponse(assignment)) else Json.encodeToString(ApiResponse(target))
            respond(body,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        val repo=AdminRepository(client,Store());val scope=TutorApprovalScope("group","period",1,100,4)
        val approval=ApproveStaffRequest("operation",3,"application","campus",listOf(scope),"test-proof")
        assertEquals(target,assertIs<Outcome.Success<AdminUserSummary>>(repo.approve("target/part",approval)).value)
        assertEquals(approval,Json.decodeFromString<ApproveStaffRequest>(bodies[0]))
        assertEquals(target,assertIs<Outcome.Success<AdminUserSummary>>(repo.lifecycle("target",AccountLifecycleRequest("suspend",4,AccountStatus.SUSPENDED,"test-proof"))).value)
        assertEquals(assignment,assertIs<Outcome.Success<TutorAssignmentSummary>>(repo.revokeAssignment("assignment",RevokeTutorAssignmentRequest("revoke","test-proof"))).value)
        assertEquals(listOf("/admin/accounts/target%2Fpart/staff-approval","/admin/accounts/target/lifecycle","/tutoring/assignments/assignment/revoke"),paths);client.close()
    }
    @Test fun `denied or unavailable administration never reports zero-user success`()=runTest {
        for(status in listOf(HttpStatusCode.Forbidden,HttpStatusCode.ServiceUnavailable)) {
            val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { respond("{}",status,headersOf(HttpHeaders.ContentType,"application/json")) })
            val failure=assertIs<Outcome.Failure>(AdminRepository(client,Store()).overview())
            assertEquals(status.value,assertIs<AppError.Http>(failure.error).status);client.close()
        }
    }
}
