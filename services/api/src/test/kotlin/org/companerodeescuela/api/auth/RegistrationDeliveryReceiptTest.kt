package org.companerodeescuela.api.auth

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.mail.VerificationEmailGateway
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.RegisterRequest
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class RegistrationDeliveryReceiptTest {
    @Test
    fun `registration reflects the actual email provider receipt in the initial session`() =
        runTest {
            for (status in VerificationDeliveryStatus.entries) {
                val accounts = InMemoryPlatformAccountRepository()
                val mail = VerificationEmailGateway { _, _, _ -> status }
                val settings =
                    TestFixtures.settings().copy(
                        jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray(),
                    )
                val service =
                    AuthService(
                        identityProvider = MockIdentityProvider(),
                        tokenService = AuthTokenService(settings),
                        sessions = InMemoryRefreshSessionRepository(),
                        accounts = accounts,
                        verification = AccountVerificationService(accounts, mail),
                    )

                val response =
                    service.register(
                        RegisterRequest(
                            displayName = "QA Student",
                            email = "student@example.test",
                            password = "test-password-123",
                            accountType = RegistrationAccountType.STUDENT,
                        ),
                    )

                assertEquals(status, response.user.verificationDelivery)
                assertEquals(
                    status,
                    accounts.findById(response.user.id)?.emailVerification?.deliveryStatus,
                )
            }
        }
}
