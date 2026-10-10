package org.companerodeescuela.feature.auth

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.companerodeescuela.core.network.*
import org.companerodeescuela.core.security.SessionTokenStore

@OptIn(ExperimentalCoroutinesApi::class)
class MfaViewModelTest {
    // Ktor MockEngine uses a real dispatcher; virtual timeouts can expire before HTTP runs.
    private suspend fun <T> awaitReal(block:suspend ()->T):T = withContext(Dispatchers.Default) { withTimeout(5000) { block() } }
    private class Store:SessionTokenStore {
        val flow=MutableStateFlow<String?>(token("operator"))
        override suspend fun readAccessToken()=flow.value
        override suspend fun writeAccessToken(token:String) { flow.value=token }
        override suspend fun clear() { flow.value=null }
        override fun observeAccessToken():Flow<String?> = flow
        companion object { fun token(id:String)=listOf("e30",Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$id","session_id":"session-$id","exp":4102444800}""".toByteArray()),"test").joinToString(".") }
    }
    @Test fun `actual MFA enrollment is memory-only and confirmation removes setup secret`()=runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler));val store=Store()
        val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { request ->
            val body=when(request.url.encodedPath) {
                "/auth/mfa/status" -> """{"enabled":false,"available":true}"""
                "/auth/mfa/enrollment" -> """{"enrollmentId":"enrollment","secret":"TESTONLY","provisioningUri":"otpauth://totp/QA","expiresAtEpochSeconds":4102444800}"""
                "/auth/mfa/confirmation" -> """{"enabled":true,"available":true}"""
                else -> error("Unexpected endpoint")
            };respond("""{"data":$body}""",HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        val vm=MfaViewModel(MfaRepository(client,store));val original=store.flow.value
        try {
            awaitReal { vm.state.first { !it.loading } }
            vm.enroll("test-password")
            val enrolled=awaitReal { vm.state.first { !it.working && it.enrollment != null } }
            assertEquals("TESTONLY",enrolled.enrollment?.secret)
            assertFalse(enrolled.toString().contains("TESTONLY"))
            vm.confirm("123456")
            val confirmed=awaitReal { vm.state.first { !it.working && it.status?.enabled == true } }
            assertEquals(true,confirmed.status?.enabled);assertNull(confirmed.enrollment);assertEquals(original,store.flow.value)
        } finally { vm.viewModelScope.cancel();client.close();Dispatchers.resetMain() }
    }
    @Test fun `MFA unavailable remains explicit and cannot report successful setup`()=runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler));val store=Store()
        val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { respond("{}",HttpStatusCode.ServiceUnavailable,headersOf(HttpHeaders.ContentType,"application/json")) })
        val vm=MfaViewModel(MfaRepository(client,store))
        try {
            val state=awaitReal { vm.state.first { !it.loading } }
            assertNotNull(state.failure);assertNull(state.status);assertNull(state.enrollment)
        } finally { vm.viewModelScope.cancel();client.close();Dispatchers.resetMain() }
    }
    @Test fun `late enrollment response never exposes previous operator secret after account change`()=runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler));val store=Store();val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val client=createApiClient(ApiEnvironment("https://example.test/","test"),MockEngine { request ->
            val body=if(request.url.encodedPath.endsWith("enrollment")) {
                started.complete(Unit);withContext(NonCancellable) { release.await() }
                """{"enrollmentId":"old-enrollment","secret":"OLDSECRET","provisioningUri":"otpauth://totp/QA","expiresAtEpochSeconds":4102444800}"""
            } else """{"enabled":false,"available":true}"""
            respond("""{"data":$body}""",HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        val vm=MfaViewModel(MfaRepository(client,store))
        try {
            awaitReal { vm.state.first { !it.loading } };vm.enroll("test-password");runCurrent()
            awaitReal { started.await() }
            store.flow.value=Store.token("replacement");runCurrent();release.complete(Unit)
            awaitReal { vm.state.first { !it.loading && !it.working } };runCurrent()
            assertNull(vm.state.value.enrollment);assertFalse(vm.state.value.toString().contains("OLDSECRET"))
        } finally { release.complete(Unit);vm.viewModelScope.cancel();client.close();Dispatchers.resetMain() }
    }
}
