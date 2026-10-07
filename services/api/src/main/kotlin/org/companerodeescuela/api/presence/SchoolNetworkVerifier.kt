package org.companerodeescuela.api.presence

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.NetworkVerificationMethod
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence

class SchoolNetworkVerifier(
    private val allowedSsids: Set<String>,
    private val allowedBssids: Set<String>,
) {
    val enabled: Boolean
        get() = allowedSsids.isNotEmpty() || allowedBssids.isNotEmpty()

    fun verify(network: SchoolNetworkEvidence): NetworkVerificationMethod {
        if (!enabled) {
            throw ApiException.DependencyUnavailable("School network verification is not configured")
        }

        val ssid = normalizeSsid(network.ssid)
        val bssid = normalizeBssid(network.bssid)
        val ssidOk = ssid != null && allowedSsids.any { normalizeSsid(it) == ssid }
        val bssidOk = bssid != null && allowedBssids.any { normalizeBssid(it) == bssid }

        if (allowedSsids.isNotEmpty() && allowedBssids.isNotEmpty()) {
            if (!ssidOk || !bssidOk) {
                throw ApiException.Forbidden("Connect to an authorized school Wi-Fi access point")
            }
            return NetworkVerificationMethod.SSID_BSSID
        }
        if (allowedBssids.isNotEmpty()) {
            if (!bssidOk) {
                throw ApiException.Forbidden("Connect to an authorized school Wi-Fi access point")
            }
            return NetworkVerificationMethod.BSSID
        }
        if (!ssidOk) {
            throw ApiException.Forbidden("Connect to the authorized school Wi-Fi network")
        }
        return NetworkVerificationMethod.SSID
    }

    private fun normalizeSsid(value: String?): String? =
        value?.trim()?.removePrefix(""")?.removeSuffix(""")?.takeIf { it.isNotBlank() }

    private fun normalizeBssid(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
}
