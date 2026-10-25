package org.companerodeescuela.api.academic

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.academic.siii.SiiiAuthorityTarget
import org.companerodeescuela.api.integrations.academic.siii.SiiiHttpAcademicAuthority
import org.companerodeescuela.api.integrations.push.InstitutionalPushAuthenticator
import org.companerodeescuela.api.integrations.push.IntegrationCredential
import org.companerodeescuela.api.integrations.push.IntegrationCredentialRepository
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import java.net.InetSocketAddress
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SiiiHttpAcademicAuthorityTest {
    @Test
    fun `source HTTP request is signed scoped bounded and does not convert service 401 to user logout`() =
        runBlocking {
            val sourceInstitution = UUID.randomUUID().toString()
            val secret = ByteArray(32) { it.toByte() }
            val responseCode = AtomicInteger(200)
            val signed = AtomicBoolean(false)
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext(SiiiHttpAcademicAuthority.PATH) { exchange ->
                val body = exchange.requestBody.readBytes()
                val headers = exchange.requestHeaders
                signed.set(
                    headers.getFirst("X-Integration-Signature") ==
                        InstitutionalPushAuthenticator.sign(
                            secret,
                            InstitutionalPushAuthenticator.canonicalMessage(
                                "POST",
                                SiiiHttpAcademicAuthority.PATH,
                                headers.getFirst("X-Integration-Timestamp"),
                                headers.getFirst("X-Integration-Nonce"),
                                body,
                            ),
                        ),
                )
                val request = Json.parseToJsonElement(String(body)).jsonObject
                assertEquals(sourceInstitution, request.getValue("institutionId").jsonPrimitive.content)
                assertEquals("student", request.getValue("kind").jsonPrimitive.content)
                val response =
                    """{"contract":"siii.academic_authority.v1",
                        "institutionId":"$sourceInstitution","kind":"student","externalId":"STUDENT",
                        "verifiedAt":"${Instant.now()}","sections":[]}""".toByteArray()
                exchange.sendResponseHeaders(responseCode.get(), response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            server.start()
            try {
                val credential = IntegrationCredential("qa-key", "native-tenant", secret, setOf("group:write"), true, null)
                val credentials =
                    object : IntegrationCredentialRepository {
                        override suspend fun findCredential(keyId: String) = credential.takeIf { it.keyId == keyId }
                    }
                val authority =
                    SiiiHttpAcademicAuthority(
                        mapOf(
                            "native-tenant" to SiiiAuthorityTarget("https://source.example.test", sourceInstitution, "qa-key"),
                        ),
                        credentials,
                        endpoint = { URI("http://127.0.0.1:${server.address.port}" + SiiiHttpAcademicAuthority.PATH) },
                    )
                assertEquals(emptyList(), authority.current("native-tenant", InstitutionalIdentityKind.STUDENT, "STUDENT").sections)
                assertTrue(signed.get())
                responseCode.set(401)
                assertFailsWith<ApiException.DependencyUnavailable> {
                    authority.current(
                        "native-tenant",
                        InstitutionalIdentityKind.STUDENT,
                        "STUDENT",
                    )
                }
                responseCode.set(403)
                assertFailsWith<ApiException.Forbidden> { authority.current("native-tenant", InstitutionalIdentityKind.STUDENT, "STUDENT") }
                responseCode.set(404)
                assertFailsWith<ApiException.NotFound> { authority.current("native-tenant", InstitutionalIdentityKind.STUDENT, "STUDENT") }
            } finally {
                server.stop(0)
            }
        }
}
