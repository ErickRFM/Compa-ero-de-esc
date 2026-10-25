package org.companerodeescuela.api.integrations.academic.siii

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.push.InstitutionalPushAuthenticator
import org.companerodeescuela.api.integrations.push.IntegrationCredentialRepository
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import java.net.HttpURLConnection
import java.net.URI
import java.time.Clock
import java.util.UUID

@Serializable
data class SiiiAuthorityTarget(
    val origin: String,
    val sourceInstitutionId: String,
    val keyId: String,
)

@Serializable
private data class SiiiAuthorityRequest(
    val contract: String,
    val institutionId: String,
    val kind: String,
    val externalId: String,
)

/** Server-only purpose-bound HMAC request. It never reads credentials from Android. */
class SiiiHttpAcademicAuthority(
    private val targets: Map<String, SiiiAuthorityTarget>,
    private val credentials: IntegrationCredentialRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val endpoint: (SiiiAuthorityTarget) -> URI = { URI(it.origin + PATH) },
) : SiiiAcademicAuthority {
    override suspend fun current(
        institutionId: String,
        kind: InstitutionalIdentityKind,
        externalId: String,
    ): SiiiAuthorityManifest {
        val target = targets[institutionId] ?: throw ApiException.DependencyUnavailable("Institutional authority is not configured")
        val credential =
            credentials.findCredential(target.keyId)
                ?: throw ApiException.DependencyUnavailable("Institutional authority credential is unavailable")
        if (!credential.enabled ||
            credential.keyId != target.keyId ||
            credential.tenantId != institutionId ||
            credential.secret.size < 32 ||
            ("group:write" !in credential.scopes && "academic:*:write" !in credential.scopes) ||
            credential.expiresAtEpochSeconds?.let { it <= clock.instant().epochSecond } == true
        ) {
            throw ApiException.DependencyUnavailable("Institutional authority credential is unavailable")
        }
        val body =
            Json
                .encodeToString(
                    SiiiAuthorityRequest(
                        "siii.academic_authority_request.v1",
                        target.sourceInstitutionId,
                        kind.name.lowercase(),
                        externalId,
                    ),
                ).toByteArray()
        val timestamp = clock.instant().epochSecond.toString()
        val nonce = UUID.randomUUID().toString()
        val signature =
            InstitutionalPushAuthenticator.sign(
                credential.secret,
                InstitutionalPushAuthenticator.canonicalMessage("POST", PATH, timestamp, nonce, body),
            )
        return withContext(Dispatchers.IO) {
            val connection = endpoint(target).toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-Integration-Key-Id", target.keyId)
                connection.setRequestProperty("X-Integration-Timestamp", timestamp)
                connection.setRequestProperty("X-Integration-Nonce", nonce)
                connection.setRequestProperty("X-Integration-Signature", signature)
                connection.outputStream.use { it.write(body) }
                when (connection.responseCode) {
                    403 -> throw ApiException.Forbidden("Institutional academic authority was revoked")
                    404 -> throw ApiException.NotFound("Institutional academic identity was not found")
                    200 -> Unit
                    else -> throw ApiException.DependencyUnavailable("Institutional authority is unavailable")
                }
                val bytes = connection.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
                if (bytes.size > MAX_BYTES) throw ApiException.DependencyUnavailable("Institutional authority response is too large")
                val result = Json.decodeFromString<SiiiAuthorityManifest>(String(bytes, Charsets.UTF_8))
                if (result.institutionId != target.sourceInstitutionId ||
                    result.kind != kind.name.lowercase() ||
                    result.externalId != externalId
                ) {
                    throw ApiException.DependencyUnavailable("Institutional authority scope did not match")
                }
                result
            } catch (error: ApiException) {
                throw error
            } catch (_: Exception) {
                throw ApiException.DependencyUnavailable("Institutional authority is unavailable")
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        const val PATH = "/api/integrations/companion/v1/authority"
        private const val MAX_BYTES = 131072

        fun targetsFromJson(raw: String?): Map<String, SiiiAuthorityTarget> {
            if (raw.isNullOrBlank()) return emptyMap()
            val targets = Json.decodeFromString<Map<String, SiiiAuthorityTarget>>(raw)
            require(targets.size <= 1000) { "Too many institutional authority targets" }
            targets.forEach { (tenant, target) ->
                require(tenant.isNotBlank() && tenant.length <= 128 && tenant == tenant.trim() && tenant.none(Char::isISOControl))
                val uri = URI(target.origin)
                require(
                    uri.scheme == "https" &&
                        !uri.host.isNullOrBlank() &&
                        uri.userInfo == null &&
                        uri.port == -1 &&
                        uri.rawPath.isNullOrEmpty() &&
                        uri.query == null &&
                        uri.fragment == null,
                ) { "Institutional authority requires a pinned HTTPS origin" }
                require(UUID.fromString(target.sourceInstitutionId).toString() == target.sourceInstitutionId)
                require(Regex("[A-Za-z0-9_-]{1,64}").matches(target.keyId))
            }
            return targets
        }
    }
}
