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

    @Test
    fun `rotating an invitation code produces a new idempotency key`() =
        runTest {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val observedKeys = mutableListOf<String>()
            server.createContext("/emails") { exchange ->
                observedKeys.add(exchange.requestHeaders.getFirst("Idempotency-Key").orEmpty())
                exchange.requestBody.bufferedReader().readText()
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
                val invitationId = "b".repeat(43)
                val firstCode = "a".repeat(43)
                val rotatedCode = "c".repeat(43)
                val expires = Instant.parse("2026-11-10T20:00:00Z")

                assertEquals(
                    VerificationDeliveryStatus.ACCEPTED,
                    adapter.send(
                        "recipient@example.test",
                        firstCode,
                        invitationId,
                        expires,
                    ),
                )
                assertEquals(
                    VerificationDeliveryStatus.ACCEPTED,
                    adapter.send(
                        "recipient@example.test",
                        firstCode,
                        invitationId,
                        expires,
                    ),
                )
                assertEquals(
                    VerificationDeliveryStatus.ACCEPTED,
                    adapter.send(
                        "recipient@example.test",
                        rotatedCode,
                        invitationId,
                        expires,
                    ),
                )

                assertEquals(3, observedKeys.size)
                assertEquals(observedKeys[0], observedKeys[1])
                assertTrue(observedKeys[0] != observedKeys[2])
                assertTrue(observedKeys.all { it.matches(Regex("invite-v2-[a-f0-9]{64}")) })
            } finally {
                server.stop(0)
            }
        }
}
