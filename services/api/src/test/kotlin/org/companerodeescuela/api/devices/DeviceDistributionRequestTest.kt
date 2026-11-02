package org.companerodeescuela.api.devices

import org.companerodeescuela.api.errors.ApiException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DeviceDistributionRequestTest {
    @Test
    fun `management and academic Android package pairs are accepted`() {
        DeviceRegistrationRequest(
            token = "test-token",
            appDistribution = "MANAGEMENT",
            packageName = "org.companerodeescuela.gestion",
        ).requireConsistentDistribution()
        DeviceRegistrationRequest(
            token = "test-token",
            appDistribution = "ACADEMIC",
            packageName = "org.companerodeescuela",
        ).requireConsistentDistribution()
    }

    @Test
    fun `management cannot claim academic or nonexistent app package`() {
        for (invalidPackage in listOf("org.companerodeescuela", "org.companerodeescuela.management", "")) {
            assertFailsWith<ApiException.Validation> {
                DeviceRegistrationRequest(
                    token = "test-token",
                    appDistribution = "MANAGEMENT",
                    packageName = invalidPackage,
                ).requireConsistentDistribution()
            }
        }
    }

    @Test
    fun `partial metadata or unsupported distribution fail closed`() {
        val candidates =
            listOf(
                DeviceRegistrationRequest(token = "test-token", appDistribution = "MANAGEMENT"),
                DeviceRegistrationRequest(token = "test-token", packageName = "org.companerodeescuela.gestion"),
                DeviceRegistrationRequest(token = "test-token", appDistribution = "LEGACY", packageName = "org.companerodeescuela"),
            )
        for (request in candidates) {
            assertFailsWith<ApiException.Validation> { request.requireConsistentDistribution() }
        }
        // Existing older applications omit both optional fields.
        DeviceRegistrationRequest(token = "test-token").requireConsistentDistribution()
    }
}
