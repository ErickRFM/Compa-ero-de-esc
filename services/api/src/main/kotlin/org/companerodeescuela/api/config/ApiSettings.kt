package org.companerodeescuela.api.config

import org.companerodeescuela.shared.validation.Validators

/**
 * MongoDB connection settings, resolved once at startup.
 *
 * @property uri connection string, always supplied through the environment.
 *   It is never logged, never serialized and never returned by any endpoint.
 * @property databaseName logical database name.
 * @property connectTimeoutMs TCP connect timeout.
 * @property serverSelectionTimeoutMs budget for the driver to pick a server.
 *   Kept short so a misconfigured DSN fails fast instead of hanging requests.
 */
data class MongoSettings(
    val uri: String,
    val databaseName: String,
    val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val serverSelectionTimeoutMs: Int = DEFAULT_SERVER_SELECTION_TIMEOUT_MS,
) {
    val isConfigured: Boolean
        get() = uri.isNotBlank()

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000
        const val DEFAULT_SERVER_SELECTION_TIMEOUT_MS = 5_000
    }
}

/**
 * Fully resolved, immutable runtime configuration.
 *
 * Instances are created once during boot and then read from everywhere else.
 * Nothing in the request path may read environment variables directly.
 */
data class ApiSettings(
    val serviceName: String,
    val environment: Environment,
    val host: String,
    val port: Int,
    val version: String,
    val apiVersion: String,
    val mongo: MongoSettings,
    /**
     * Secret used to sign authentication tokens.
     *
     * Held as [CharArray] rather than [String] so it can be zeroed after boot.
     * `null` means "authentication is not configured", which is the only
     * acceptable state in [Environment.LOCAL].
     */
    val jwtSecret: CharArray?,
    val jwtIssuer: String,
    val jwtAudience: String,
    val attendanceQrSecret: CharArray? = null,
    val schoolPresenceQrSha256: String = "",
    val schoolWifiSsids: Set<String> = emptySet(),
    val schoolWifiBssids: Set<String> = emptySet(),
    val attendanceGraceMinutes: Int = 5,
) {
    val hasAuthentication: Boolean
        get() = jwtSecret != null

    val hasAttendanceQrSigning: Boolean
        get() = attendanceQrSecret != null

    val hasSchoolPresenceVerification: Boolean
        get() = schoolWifiSsids.isNotEmpty() || schoolWifiBssids.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ApiSettings) return false
        return serviceName == other.serviceName &&
            environment == other.environment &&
            host == other.host &&
            port == other.port &&
            version == other.version &&
            apiVersion == other.apiVersion &&
            mongo == other.mongo &&
            jwtIssuer == other.jwtIssuer &&
            jwtAudience == other.jwtAudience &&
            hasAuthentication == other.hasAuthentication &&
            hasAttendanceQrSigning == other.hasAttendanceQrSigning &&
            schoolPresenceQrSha256 == other.schoolPresenceQrSha256 &&
            schoolWifiSsids == other.schoolWifiSsids &&
            schoolWifiBssids == other.schoolWifiBssids &&
            attendanceGraceMinutes == other.attendanceGraceMinutes
    }

    override fun hashCode(): Int {
        var result = serviceName.hashCode()
        result = 31 * result + environment.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + port
        result = 31 * result + version.hashCode()
        result = 31 * result + apiVersion.hashCode()
        result = 31 * result + mongo.hashCode()
        result = 31 * result + jwtIssuer.hashCode()
        result = 31 * result + jwtAudience.hashCode()
        result = 31 * result + hasAuthentication.hashCode()
        result = 31 * result + hasAttendanceQrSigning.hashCode()
        result = 31 * result + schoolPresenceQrSha256.hashCode()
        result = 31 * result + schoolWifiSsids.hashCode()
        result = 31 * result + schoolWifiBssids.hashCode()
        result = 31 * result + attendanceGraceMinutes
        return result
    }

    /**
     * Safe to log: deliberately omits the Mongo URI and the JWT secret.
     */
    override fun toString(): String =
        "ApiSettings(" +
            "serviceName=$serviceName, " +
            "environment=$environment, " +
            "host=$host, " +
            "port=$port, " +
            "version=$version, " +
            "apiVersion=$apiVersion, " +
            "mongoConfigured=${mongo.isConfigured}, " +
            "mongoDatabase=${mongo.databaseName}, " +
            "authenticationConfigured=$hasAuthentication, " +
            "attendanceQrSigningConfigured=$hasAttendanceQrSigning, " +
            "schoolPresenceConfigured=$hasSchoolPresenceVerification" +
            ")"
}

/**
 * Reads configuration from an environment-variable source.
 *
 * Taking the source as a parameter (`(String) -> String?`) instead of reading
 * `System.getenv()` directly keeps [load] fully testable.
 */
class SettingsLoader(
    private val env: (String) -> String?,
) {
    fun load(): ApiSettings {
        val environment = Environment.parse(env("APP_ENV"))

        val host = env("API_HOST")?.trim().orEmpty().ifEmpty { DEFAULT_HOST }
        val portText = env("API_PORT")?.trim()
        val databaseName = env("MONGODB_DATABASE")?.trim().orEmpty()
        val mongoUri = env("MONGODB_URI")?.trim().orEmpty()
        val jwtSecret = env("JWT_SECRET")?.takeIf { it.isNotBlank() }
        val jwtIssuer = env("JWT_ISSUER")?.trim().orEmpty().ifEmpty { DEFAULT_JWT_ISSUER }
        val jwtAudience = env("JWT_AUDIENCE")?.trim().orEmpty().ifEmpty { DEFAULT_JWT_AUDIENCE }
        val attendanceQrSecret = env("ATTENDANCE_QR_SECRET")?.takeIf { it.isNotBlank() }
        val schoolPresenceQrSha256 = env("SCHOOL_PRESENCE_QR_SHA256")?.trim().orEmpty().lowercase()
        val schoolWifiSsids = parseCsv(env("SCHOOL_WIFI_SSIDS"))
        val schoolWifiBssids = parseCsv(env("SCHOOL_WIFI_BSSIDS")).map { it.lowercase() }.toSet()
        val attendanceGraceMinutes = env("ATTENDANCE_GRACE_MINUTES")?.trim()?.toIntOrNull() ?: 5
        if (attendanceGraceMinutes !in 0..15) {
            throw ConfigurationException("ATTENDANCE_GRACE_MINUTES must be between 0 and 15")
        }

        val results = listOf(
            Validators.port(ENV_API_PORT, portText),
            Validators.notBlank(ENV_MONGODB_DATABASE, databaseName),
        )

        val invalid = results.filterNot { it.isValid }
        if (invalid.isNotEmpty()) {
            throw ConfigurationException(
                "Invalid configuration: ${invalid.joinToString("; ") { it.describe() }}",
            )
        }

        validateSecret(environment, jwtSecret)
        validateOptionalSecret("ATTENDANCE_QR_SECRET", attendanceQrSecret)
        validateSchoolPresence(schoolPresenceQrSha256, schoolWifiSsids, schoolWifiBssids)

        return ApiSettings(
            serviceName = DEFAULT_SERVICE_NAME,
            environment = environment,
            host = host,
            port = portText!!.trim().toInt(),
            version = env("APP_VERSION")?.trim().orEmpty().ifEmpty { UNKNOWN_VERSION },
            apiVersion = env("API_VERSION")?.trim().orEmpty().ifEmpty { DEFAULT_API_VERSION },
            mongo = MongoSettings(
                uri = mongoUri,
                databaseName = databaseName,
                connectTimeoutMs = env("MONGODB_CONNECT_TIMEOUT_MS")
                    ?.trim()
                    ?.toIntOrNull()
                    ?: MongoSettings.DEFAULT_CONNECT_TIMEOUT_MS,
                serverSelectionTimeoutMs = env("MONGODB_SERVER_SELECTION_TIMEOUT_MS")
                    ?.trim()
                    ?.toIntOrNull()
                    ?: MongoSettings.DEFAULT_SERVER_SELECTION_TIMEOUT_MS,
            ),
            jwtSecret = jwtSecret?.toCharArray(),
            jwtIssuer = jwtIssuer,
            jwtAudience = jwtAudience,
            attendanceQrSecret = attendanceQrSecret?.toCharArray(),
            schoolPresenceQrSha256 = schoolPresenceQrSha256,
            schoolWifiSsids = schoolWifiSsids,
            schoolWifiBssids = schoolWifiBssids,
            attendanceGraceMinutes = attendanceGraceMinutes,
        )
    }

    private fun parseCsv(raw: String?): Set<String> =
        raw.orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private fun validateSchoolPresence(
        qrSha256: String,
        ssids: Set<String>,
        bssids: Set<String>,
    ) {
        val partiallyConfigured = qrSha256.isNotBlank() || ssids.isNotEmpty() || bssids.isNotEmpty()
        if (!partiallyConfigured) return
        if (
            qrSha256.isNotBlank() &&
            (qrSha256.length != 64 || qrSha256.any { it !in "0123456789abcdef" })
        ) {
            throw ConfigurationException("SCHOOL_PRESENCE_QR_SHA256 must be a lowercase SHA-256 hex digest")
        }
        if (ssids.isEmpty() && bssids.isEmpty()) {
            throw ConfigurationException("Configure SCHOOL_WIFI_SSIDS or SCHOOL_WIFI_BSSIDS")
        }
    }

    private fun validateOptionalSecret(name: String, secret: String?) {
        if (!secret.isNullOrBlank() && secret.length < MIN_SECRET_LENGTH) {
            throw ConfigurationException("$name must be at least $MIN_SECRET_LENGTH characters long")
        }
    }

    private fun validateSecret(environment: Environment, jwtSecret: String?) {
        if (environment.isProduction && jwtSecret.isNullOrBlank()) {
            throw ConfigurationException(
                "JWT_SECRET is required when APP_ENV=production",
            )
        }
        if (!jwtSecret.isNullOrBlank() && jwtSecret.length < MIN_SECRET_LENGTH) {
            throw ConfigurationException(
                "JWT_SECRET must be at least $MIN_SECRET_LENGTH characters long",
            )
        }
    }

    private companion object {
        const val DEFAULT_HOST = "0.0.0.0"
        const val DEFAULT_SERVICE_NAME = "companero-api"
        const val DEFAULT_API_VERSION = "v1"
        const val DEFAULT_JWT_ISSUER = "companero-de-escuela"
        const val DEFAULT_JWT_AUDIENCE = "companero-android"
        const val UNKNOWN_VERSION = "0.0.0-dev"
        const val MIN_SECRET_LENGTH = 32

        const val ENV_API_PORT = "API_PORT"
        const val ENV_MONGODB_DATABASE = "MONGODB_DATABASE"
    }
}
