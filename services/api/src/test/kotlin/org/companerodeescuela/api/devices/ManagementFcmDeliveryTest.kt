package org.companerodeescuela.api.devices

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManagementFcmDeliveryTest {
    @Test
    fun `management outbox only delivers to the management package and current session`() =
        runBlocking {
            val devices = InMemoryDeviceTokenRepository()
            val academic = "fcm_token_academic_" + "a".repeat(60)
            val management = "fcm_token_management_" + "m".repeat(60)
            val legacy = "fcm_token_legacy_" + "l".repeat(60)
            val owner = "NATIVE:admin-test"

            devices.register(owner, academic, "android", null, "academic-device", "acad-session", "ACADEMIC", "org.companerodeescuela")
            devices.register(
                owner,
                management,
                "android",
                null,
                "management-device",
                "management-session",
                "MANAGEMENT",
                "org.companerodeescuela.gestion",
            )
            devices.register(owner, legacy, "android", null, "old-device", "old-session")
            val sent = mutableListOf<String>()
            val outbox =
                PushOutboxService(
                    devices,
                    InMemoryPushOutboxRepository(),
                    PushGateway { token, _, _ ->
                        sent += token
                        PushDeliveryResult.ACCEPTED
                    },
                    authorizeRecipient = { it == owner },
                    authorizeSession = { recipient, token ->
                        recipient == owner && token == management
                    },
                )
            assertEquals(
                1,
                outbox.enqueueForOwner(
                    eventId = "management-scope-test",
                    owner = owner,
                    title = "Gestión",
                    body = "Actualización disponible",
                    appDistribution = "MANAGEMENT",
                ),
            )
            assertTrue(outbox.dispatchNext())
            assertEquals(listOf(management), sent)
            assertFalse(outbox.dispatchNext())
            assertEquals(
                listOf(academic, management, legacy).toSet(),
                devices.listTokens(setOf(owner)).toSet(),
            )
        }
}
