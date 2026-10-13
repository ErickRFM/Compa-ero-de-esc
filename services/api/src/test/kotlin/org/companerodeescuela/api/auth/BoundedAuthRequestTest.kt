package org.companerodeescuela.api.auth

import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.LoginRequest
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.TestFixtures

class BoundedAuthRequestTest {
    @Test
    fun `default bounded body rejects one megabyte request`() = testApplication {
        application {
            configurePlugins(TestFixtures.settings())
            routing {
                post("/bounded") {
                    call.boundedBody(LoginRequest.serializer())
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

        val response = client.post("/bounded") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"username":"ana","password":"${"x".repeat(1_048_576)}"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
