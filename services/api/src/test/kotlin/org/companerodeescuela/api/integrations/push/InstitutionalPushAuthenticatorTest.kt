package org.companerodeescuela.api.integrations.push

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstitutionalPushAuthenticatorTest {
    private val instant = Instant.parse("2026-10-14T12:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)

    @Test
    fun `valid signature resolves tenant from active credential and reserves nonce`() =
        runBlocking {
            val repository =
                InMemoryInstitutionalIntegrationRepository(
                    credentials = listOf(credential()),
                )
            val authenticator = InstitutionalPushAuthenticator(repository, repository, clock)
            val request = signedRequest()

            val result = authenticator.authenticate(request)

            assertEquals("school-north", result?.tenantId)
            assertEquals(setOf("schedule:write"), result?.scopes)
            assertNull(authenticator.authenticate(request))
        }

    @Test
    fun `invalid signature and expired timestamp do not reserve nonce`() =
        runBlocking {
            val repository = InMemoryInstitutionalIntegrationRepository(credentials = listOf(credential()))
            val authenticator = InstitutionalPushAuthenticator(repository, repository, clock)

            assertNull(authenticator.authenticate(signedRequest(signature = "00".repeat(32))))
            assertNull(authenticator.authenticate(signedRequest(timestamp = (instant.epochSecond - 301).toString())))
            assertEquals(0, repository.reservedNonceCount())
        }

    @Test
    fun `revoked credentials are rejected`() =
        runBlocking {
            val repository =
                InMemoryInstitutionalIntegrationRepository(
                    credentials = listOf(credential(enabled = false)),
                )
            val authenticator = InstitutionalPushAuthenticator(repository, repository, clock)

            assertNull(authenticator.authenticate(signedRequest()))
        }

    @Test
    fun `concurrent replay reserves nonce exactly once`() =
        runBlocking {
            val repository = InMemoryInstitutionalIntegrationRepository(credentials = listOf(credential()))
            val authenticator = InstitutionalPushAuthenticator(repository, repository, clock)
            val request = signedRequest()
            val accepted = (1..20).map { async { authenticator.authenticate(request) != null } }.awaitAll()

            assertEquals(1, accepted.count { it })
            assertEquals(1, repository.reservedNonceCount())
        }

    private fun credential(
        enabled: Boolean = true,
        scopes: Set<String> = setOf("schedule:write"),
    ) = IntegrationCredential(
        keyId = "key-01",
        tenantId = "school-north",
        secret = TEST_SECRET.toByteArray(),
        scopes = scopes,
        enabled = enabled,
        expiresAtEpochSeconds = instant.plusSeconds(3_600).epochSecond,
    )

    private fun signedRequest(
        timestamp: String = instant.epochSecond.toString(),
        entityType: String = "schedule",
        signature: String? = null,
    ): SignedIntegrationRequest {
        val body = "{\"eventId\":\"evt-1\",\"entityType\":\"$entityType\"}".toByteArray()
        val canonical =
            InstitutionalPushAuthenticator.canonicalMessage(
                "POST",
                "/integration/v1/events",
                timestamp,
                "nonce-1234567890",
                body,
            )
        return SignedIntegrationRequest(
            keyId = "key-01",
            timestamp = timestamp,
            nonce = "nonce-1234567890",
            signature = signature ?: InstitutionalPushAuthenticator.sign(TEST_SECRET.toByteArray(), canonical),
            method = "POST",
            path = "/integration/v1/events",
            body = body,
        )
    }

    private companion object {
        const val TEST_SECRET = "local-test-secret-with-sufficient-entropy-32"
    }
}

private class InMemoryInstitutionalIntegrationRepository(
    credentials: List<IntegrationCredential> = emptyList(),
) : IntegrationCredentialRepository,
    IntegrationNonceRepository {
    private val credentialById = credentials.associateBy(IntegrationCredential::keyId)
    private val nonces = mutableSetOf<Pair<String, String>>()

    override suspend fun findCredential(keyId: String): IntegrationCredential? = credentialById[keyId]

    override suspend fun reserveNonce(
        credentialId: String,
        nonce: String,
        expiresAt: Instant,
    ): Boolean =
        synchronized(nonces) {
            nonces.add(credentialId to nonce)
        }

    fun reservedNonceCount(): Int = synchronized(nonces) { nonces.size }
}
