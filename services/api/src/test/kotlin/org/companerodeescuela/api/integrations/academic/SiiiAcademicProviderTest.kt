package org.companerodeescuela.api.integrations.academic

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicProvider
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiiiAcademicProviderTest {
    private val student =
        """
        {"externalId":"SYNTHETIC_01","fullName":"Synthetic Student",
         "institutionalEmail":null,"programCode":"TEST","enrollmentYear":null}
        """.trimIndent()
    private val teacher = """{"externalId":"T01","fullName":"Synthetic Teacher","institutionalEmail":null,"departmentCode":null}"""
    private val course =
        """
        {"externalId":"section-01","subject":{"externalCode":"TEST01","name":"Synthetic subject","credits":6},
         "teacher":$teacher,"term":"2026-TEST","groupName":"A"}
        """.trimIndent()
    private val slot =
        """
        {"courseId":"section-01","groupName":"A","weekdayName":"SABADO","startTime":"09:00","endTime":"10:00",
         "buildingCode":"TEST","classroomCode":"R01","campusName":"Synthetic campus"}
        """.trimIndent()
    private val load =
        """
        {"student":$student,"enrollments":[{"course":$course,"enrolledAtIso":"2026-01-01T00:00:00.000Z"}],
         "schedule":[$slot]}
        """.trimIndent()
    private val token = "synthetic_companion_token_0123456789abcdef"

    @Test
    fun `reads all five v1 endpoints through the real HTTP boundary`() =
        runBlocking {
            val requests = ConcurrentLinkedQueue<Pair<String, String?>>()
            serve({ path ->
                when {
                    path.endsWith("/academic-load") -> 200 to load
                    path.contains("/students/") -> 200 to student
                    path.endsWith("/courses") -> 200 to "[$course]"
                    path.endsWith("/teachers") -> 200 to "[$teacher]"
                    else -> 200 to "[$slot]"
                }
            }, requests) { provider ->
                assertEquals("Synthetic Student", provider.getStudent("SYNTHETIC_01")?.fullName)
                val result = provider.getAcademicLoad("SYNTHETIC_01")
                assertEquals(
                    "section-01",
                    result.enrollments
                        .single()
                        .course.externalId,
                )
                assertEquals("SABADO", result.schedule.single().weekdayName)
                assertEquals(
                    6,
                    provider
                        .listCourses()
                        .single()
                        .subject.credits,
                )
                assertEquals("T01", provider.listTeachers().single().externalId)
                assertEquals("09:00", provider.getSchedule("SYNTHETIC_01", LocalDate.parse("2026-10-17")).single().startTime)
            }
            assertEquals(5, requests.size)
            assertTrue(requests.all { it.second == "Bearer $token" })
            assertTrue(requests.any { it.first == "/api/integrations/companion/v1/schedules/SYNTHETIC_01?weekOf=2026-10-17" })
        }

    @Test
    fun `maps upstream failures without disclosing response bodies or credentials`() =
        runBlocking {
            for ((status, category) in listOf(
                401 to IntegrationException.Category.UNAUTHORIZED,
                403 to IntegrationException.Category.UNAUTHORIZED,
                409 to IntegrationException.Category.BAD_REQUEST,
                422 to IntegrationException.Category.BAD_REQUEST,
                429 to IntegrationException.Category.UNAVAILABLE,
                503 to IntegrationException.Category.UNAVAILABLE,
            )) {
                serve({ status to "private upstream $token" }) { provider ->
                    val error = assertFailsWith<IntegrationException> { provider.listCourses() }
                    assertEquals(category, error.category)
                    assertFalse(error.toString().contains(token))
                    assertFalse(error.toString().contains("private upstream"))
                }
            }
        }

    @Test
    fun `student absence is nullable while missing academic load fails explicitly`() =
        runBlocking {
            serve({ 404 to "" }) { provider ->
                assertNull(provider.getStudent("ABSENT"))
                assertEquals(
                    IntegrationException.Category.NOT_FOUND,
                    assertFailsWith<IntegrationException> {
                        provider.getAcademicLoad("ABSENT")
                    }.category,
                )
            }
        }

    @Test
    fun `rejects malformed or mismatched data and oversized responses`() =
        runBlocking {
            for (body in listOf("{}", student.replace("SYNTHETIC_01", "OTHER"), "x".repeat(1_048_577))) {
                serve({ 200 to body }) { provider ->
                    assertEquals(
                        IntegrationException.Category.MALFORMED_RESPONSE,
                        assertFailsWith<IntegrationException> {
                            provider.getStudent("SYNTHETIC_01")
                        }.category,
                    )
                }
            }
            serve({ 200 to load.replace("09:00", "invalid") }) { provider ->
                assertEquals(
                    IntegrationException.Category.MALFORMED_RESPONSE,
                    assertFailsWith<IntegrationException> {
                        provider.getAcademicLoad("SYNTHETIC_01")
                    }.category,
                )
            }
        }

    @Test
    fun `requires secure origins and rejects identifiers that can change the request path`() =
        runBlocking {
            for (origin in listOf(
                "http://institution.example",
                "https://user:secret@institution.example",
                "https://institution.example/?secret=1",
            )) {
                assertFailsWith<IllegalArgumentException> { SiiiAcademicProvider(URI(origin), token) }
            }
            serve({ 200 to student }) { provider ->
                assertEquals(
                    IntegrationException.Category.BAD_REQUEST,
                    assertFailsWith<IntegrationException> {
                        provider.getStudent("../teachers")
                    }.category,
                )
            }
        }

    @Test
    fun `reads from HTTP servers that do not support h2c upgrades`() =
        runBlocking {
            serve({ 200 to "[]" }, rejectHttp2Upgrade = true) { provider ->
                assertTrue(provider.listCourses().isEmpty())
            }
        }

    @Test
    fun `does not follow redirects carrying service credentials`() =
        runBlocking {
            val sinkRequests = ConcurrentLinkedQueue<Pair<String, String?>>()
            val sink = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            sink.createContext("/") { exchange ->
                sinkRequests.add(exchange.requestURI.toString() to exchange.requestHeaders.getFirst("Authorization"))
                val bytes = "[]".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            sink.start()
            try {
                serve({ 302 to "" }, redirectLocation = "http://127.0.0.1:${sink.address.port}/") { provider ->
                    assertEquals(
                        IntegrationException.Category.UNAVAILABLE,
                        assertFailsWith<IntegrationException> { provider.listCourses() }.category,
                    )
                }
                assertTrue(sinkRequests.isEmpty(), "The redirect destination must receive no request or credential")
            } finally {
                sink.stop(0)
            }
        }

    @Test
    fun `rejects end of day values the existing LocalTime contract cannot represent`() =
        runBlocking {
            serve({ 200 to "[${slot.replace("10:00", "24:00")}]" }) { provider ->
                assertEquals(
                    IntegrationException.Category.MALFORMED_RESPONSE,
                    assertFailsWith<IntegrationException> { provider.getSchedule("SYNTHETIC_01") }.category,
                )
            }
        }

    @Test
    fun `bounds request duration`() =
        runBlocking {
            serve({
                Thread.sleep(300)
                200 to student
            }, timeout = Duration.ofMillis(50)) { provider ->
                assertEquals(
                    IntegrationException.Category.TIMEOUT,
                    assertFailsWith<IntegrationException> {
                        provider.getStudent("SYNTHETIC_01")
                    }.category,
                )
            }
        }

    private suspend fun serve(
        response: (String) -> Pair<Int, String>,
        requests: ConcurrentLinkedQueue<Pair<String, String?>> = ConcurrentLinkedQueue(),
        timeout: Duration = Duration.ofSeconds(3),
        redirectLocation: String = "http://127.0.0.1:1/",
        rejectHttp2Upgrade: Boolean = false,
        action: suspend (SiiiAcademicProvider) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(exchange.requestURI.toString() to exchange.requestHeaders.getFirst("Authorization"))
            val (status, body) =
                if (rejectHttp2Upgrade && exchange.requestHeaders.getFirst("Upgrade") != null) {
                    503 to ""
                } else {
                    response(exchange.requestURI.path)
                }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            if (status == 302) exchange.responseHeaders.add("Location", redirectLocation)
            runCatching {
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        try {
            action(SiiiAcademicProvider(URI("http://127.0.0.1:${server.address.port}"), token, timeout, allowInsecureLoopback = true))
        } finally {
            server.stop(0)
        }
    }
}
