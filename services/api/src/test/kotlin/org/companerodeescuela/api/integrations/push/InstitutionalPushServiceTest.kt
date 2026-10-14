package org.companerodeescuela.api.integrations.push

import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

class InstitutionalPushServiceTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val repository = RecordingEvents()
    private val service = InstitutionalPushService(repository, Clock.fixed(now, ZoneOffset.UTC))
    private val institution = VerifiedInstitution("key-a", "tenant-a", setOf("schedule:write"))
    private val body =
        """{"schemaVersion":1,"eventId":"evt-1","entityType":"schedule","externalId":"class-01","sourceVersion":3,"occurredAtEpochSeconds":${now.epochSecond},"operation":"upsert","payload":{"subject":"Math"}}"""
            .toByteArray()

    @Test
    fun `accepted event stores authenticated tenant and provenance`() =
        runBlocking {
            val result = assertIs<InstitutionalPushResult.Accepted>(service.accept(institution, body))

            assertEquals(false, result.duplicate)
            assertEquals("tenant-a", repository.lastRecord?.tenantId)
            assertEquals("key-a", repository.lastRecord?.sourceCredentialId)
            assertEquals("evt-1", repository.lastRecord?.event?.eventId)
        }

    @Test
    fun `unknown fields and unsupported versions fail closed`() =
        runBlocking {
            val unknownField =
                body
                    .decodeToString()
                    .replace(
                        "\"schemaVersion\":1,",
                        "\"schemaVersion\":1,\"tenantId\":\"attacker\",",
                    ).toByteArray()
            val unsupportedVersion = body.decodeToString().replace("\"schemaVersion\":1", "\"schemaVersion\":2").toByteArray()

            assertEquals(InstitutionalPushResult.Invalid, service.accept(institution, unknownField))
            assertEquals(InstitutionalPushResult.Invalid, service.accept(institution, unsupportedVersion))
            assertEquals(0, repository.records.size)
        }

    @Test
    fun `scope is checked against authenticated institution`() =
        runBlocking {
            val result = service.accept(institution.copy(scopes = setOf("groups:write")), body)

            assertEquals(InstitutionalPushResult.Forbidden, result)
            assertEquals(0, repository.records.size)
        }

    @Test
    fun `repeat event is idempotent and conflicting event id is rejected`() =
        runBlocking {
            assertEquals(false, assertIs<InstitutionalPushResult.Accepted>(service.accept(institution, body)).duplicate)
            assertEquals(true, assertIs<InstitutionalPushResult.Accepted>(service.accept(institution, body)).duplicate)
            val conflict = body.decodeToString().replace("Math", "History").toByteArray()

            assertEquals(InstitutionalPushResult.Conflict, service.accept(institution, conflict))
            assertEquals(1, repository.records.size)
        }

    private class RecordingEvents : InstitutionalEventRepository {
        val records = mutableMapOf<Pair<String, String>, InstitutionalEventRecord>()
        var lastRecord: InstitutionalEventRecord? = null

        override suspend fun accept(record: InstitutionalEventRecord): InstitutionalEventAcceptance {
            lastRecord = record
            val key = record.tenantId to record.event.eventId
            val prior = records[key]
            if (prior != null) {
                return if (prior.payloadSha256 == record.payloadSha256) {
                    InstitutionalEventAcceptance.DUPLICATE
                } else {
                    InstitutionalEventAcceptance.CONFLICT
                }
            }
            records[key] = record
            return InstitutionalEventAcceptance.ACCEPTED
        }
    }
}

class InstitutionalSecretProtectorTest {
    @Test
    fun `credential secret is authenticated and encrypted at rest`() {
        val protector = InstitutionalSecretProtector(ByteArray(32) { it.toByte() })
        val secret = ByteArray(32) { (it + 11).toByte() }

        val first = protector.encrypt(secret)
        val second = protector.encrypt(secret)

        kotlin.test.assertNotEquals(first, second)
        kotlin.test.assertContentEquals(secret, protector.decrypt(first))
        assertFails {
            val tampered = first.dropLast(1) + if (first.last() == 'A') "B" else "A"
            protector.decrypt(tampered)
        }
    }
}
