package org.companerodeescuela.core.attendance

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence

fun interface SchoolNetworkEvidenceProvider {
    fun current(): SchoolNetworkEvidence?
}

@Singleton
class AndroidSchoolNetworkEvidenceProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SchoolNetworkEvidenceProvider {

    override fun current(): SchoolNetworkEvidence? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val activeNetwork = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(activeNetwork) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        val info = runCatching { wifi.connectionInfo }.getOrNull() ?: return null

        val ssid = info.ssid
            ?.trim()
            ?.removePrefix(""")
            ?.removeSuffix(""")
            ?.takeUnless { it.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true) }
            ?.takeIf(String::isNotBlank)

        val bssid = info.bssid
            ?.trim()
            ?.lowercase()
            ?.takeUnless { it == "02:00:00:00:00:00" }
            ?.takeIf(String::isNotBlank)

        return SchoolNetworkEvidence(
            ssid = ssid,
            bssid = bssid,
        ).takeIf { it.ssid != null || it.bssid != null }
    }
}
