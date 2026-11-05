package org.companerodeescuela.api.invitations

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.mail.ResendInvitationEmailGateway
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.net.InetSocketAddress
import java.net.URI
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvitationEmailTransportTest {
    @Test
    fun `provider acceptance returns accepted and email contains registration instructions`() =
        runTest {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            var body = ""
            server.createContext("/emails") { exchange ->
                body = exchange.requestBody.bufferedReader().readText()
                val response = """{"id":"mail-test"}""".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
                exchange.close()
            }
            server.start()
            try {
                val adapter =
                    ResendInvitationEmailGateway(
                        "local-test-only-key".toCharArray(),
                        "sender@example.test",
                        URI("http://127.0.0.1:${server.address.port}/emails"),
                    )
                val code = "a".repeat(43)
                val id = "b".repeat(43)
                val status = adapter.send("recipient@example.test", code, id, Instant.parse("2026-11-10T20:00:00Z"))
                assertEquals(VerificationDeliveryStatus.ACCEPTED, status)
                assertTrue(body.contains("recipient@example.test"))
                assertTrue(body.contains(code))
                assertTrue(body.contains("Compañero de Clase"))
                assertTrue(body.contains("regístrate"))
            } finally {
                server.stop(0)
            }
        }
}
