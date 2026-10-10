package org.companerodeescuela.feature.tutoring

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.GroupRepresentativeAssignment
import org.companerodeescuela.shared.contracts.RepresentativePosition
import org.companerodeescuela.shared.contracts.RepresentativeStatus

@OptIn(ExperimentalCoroutinesApi::class)
class StudentRepresentativesViewModelTest {
    @Test fun `student accepts and rejects invitations through the existing session`()=runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store=Store(token("student-a"))
        var accepted=false
        val invitation=invite("student-a")
        val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { req ->
            val response=when(req.url.encodedPath) {
                "/representatives/me" -> Json.encodeToString(ApiResponse(listOf(invitation)))
                "/representatives/inv-student-a/accept" -> {
                    accepted=true
                    Json.encodeToString(ApiResponse(invitation.copy(status=RepresentativeStatus.ACTIVE)))
                }
                else -> Json.encodeToString(ApiResponse(invitation))
            }
            respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,ContentType.Application.Json.toString()))
        })
        val model=StudentRepresentativesViewModel(
            GroupRepresentativeRepository(client,SessionRefreshCoordinator(client,store)),store)
        try {
            model.state.first { !it.loading && it.assignments.isNotEmpty() }
            model.decide("inv-student-a",true)
            model.state.first { !it.submitting && it.assignments.firstOrNull()?.status==RepresentativeStatus.ACTIVE }
            assertTrue(accepted)
            assertEquals(RepresentativeStatus.ACTIVE,model.state.value.assignments.single().status)
        } finally {
            model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun `switching student accounts immediately discards the old invitations`()=runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store=Store(token("student-a"))
        val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { req ->
            val owner=if(req.headers[HttpHeaders.Authorization]=="Bearer ${token("student-a")}") "student-a" else "student-b"
            respond(Json.encodeToString(ApiResponse(listOf(invite(owner)))),HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType,ContentType.Application.Json.toString()))
        })
        val model=StudentRepresentativesViewModel(
            GroupRepresentativeRepository(client,SessionRefreshCoordinator(client,store)),store)
        try {
            model.state.first { !it.loading && it.assignments.firstOrNull()?.studentUserId=="student-a" }
            store.value.value=token("student-b")
            model.state.first { !it.loading && it.assignments.firstOrNull()?.studentUserId=="student-b" }
            assertEquals("student-b",model.state.value.assignments.single().studentUserId)
            store.value.value=null
            model.state.first { !it.loading && it.assignments.isEmpty() }
            assertTrue(model.state.value.assignments.isEmpty())
        } finally {
            model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            client.close()
            Dispatchers.resetMain()
        }
    }

    private class Store(access:String?):SessionTokenStore {
        val value=MutableStateFlow(access)
        override suspend fun readAccessToken()=value.value
        override suspend fun writeAccessToken(token:String) { value.value=token }
        override suspend fun clear() { value.value=null }
        override fun observeAccessToken()=value
    }

    private fun invite(owner:String)=GroupRepresentativeAssignment(
        id="inv-$owner",academicGroupId="9A",academicGroupName="9 A",studentUserId=owner,
        position=RepresentativePosition.DEPUTY,appointedBy="tutor-1",appointedAtEpochSeconds=1L)
    private fun token(owner:String):String {
        val payload="""{"sub":"$owner","session_id":"session-$owner","exp":${System.currentTimeMillis()/1000+3600},"roles":["STUDENT"]}"""
        return "e30."+Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())+".test"
    }
}
