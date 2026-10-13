package org.companerodeescuela.api.plugins

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.companerodeescuela.api.auth.LoginAttemptLimiter
import org.companerodeescuela.api.test.TestFixtures

class ClientAddressTest {
    @Test
    fun `forwarded client addresses are ignored unless proxy is trusted`() = testApplication {
        application {
            configurePlugins(TestFixtures.settings())
            routing { get("/client-address") { call.respondText(call.clientAddress()) } }
        }

        val response = client.get("/client-address") {
            header("X-Forwarded-For", "198.51.100.10")
        }

        assertNotEquals("198.51.100.10", response.bodyAsText())
    }

    @Test
    fun `trusted proxy preserves distinct forwarded client addresses`() = testApplication {
        application {
            configurePlugins(TestFixtures.settings().copy(trustedProxy = true))
            routing { get("/client-address") { call.respondText(call.clientAddress()) } }
        }

        val first = client.get("/client-address") {
            header("X-Forwarded-For", "198.51.100.10")
        }.bodyAsText()
        val second = client.get("/client-address") {
            header("X-Forwarded-For", "203.0.113.20")
        }.bodyAsText()

        assertEquals("198.51.100.10", first)
        assertEquals("203.0.113.20", second)
        val limiter = LoginAttemptLimiter(maxAttempts = 1)
        assertNull(limiter.acquireAddress(first))
        assertNotNull(limiter.acquireAddress(first))
        assertNull(limiter.acquireAddress(second))
    }
}
