package org.companerodeescuela.api.config

import org.companerodeescuela.shared.validation.Validators
import java.util.Base64

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
    val schoolPresenceInstitutionId:String?=null,
    val schoolWifiSsids: Set<String> = emptySet(),
    val schoolWifiBssids: Set<String> = emptySet(),
    val attendanceGraceMinutes: Int = 5,
    val verificationEmailApiKey: CharArray? = null,
    val verificationEmailSender: String? = null,
    val mfaEncryptionKey: ByteArray? = null,
    /** Private Firebase service-account JSON, decoded only at boot; never render or log. */
    val firebaseServiceAccountJson: CharArray? = null,
    val lateSyncReviewWindowSeconds: Long = DEFAULT_LATE_SYNC_REVIEW_WINDOW_SECONDS,
    val attendanceQrNonceReuseThreshold: Int = DEFAULT_ATTENDANCE_QR_NONCE_REUSE_THRESHOLD,
) {
    companion object {
        const val DEFAULT_LATE_SYNC_REVIEW_WINDOW_SECONDS = 7_200L
        const val DEFAULT_ATTENDANCE_QR_NONCE_REUSE_THRESHOLD = 3
    }

    val hasVerificationEmail: Boolean get() = verificationEmailApiKey != null && verificationEmailSender != null
    val hasFirebasePush: Boolean get() = firebaseServiceAccountJson != null

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
            schoolPresenceInstitutionId == other.schoolPresenceInstitutionId &&
            schoolWifiSsids == other.schoolWifiSsids &&
            schoolWifiBssids == other.schoolWifiBssids &&
            attendanceGraceMinutes == other.attendanceGraceMinutes &&
            lateSyncReviewWindowSeconds == other.lateSyncReviewWindowSeconds &&
            attendanceQrNonceReuseThreshold == other.attendanceQrNonceReuseThreshold &&
            hasVerificationEmail == other.hasVerificationEmail && verificationEmailSender == other.verificationEmailSender &&
            (mfaEncryptionKey != null) == (other.mfaEncryptionKey != null) &&
            hasFirebasePush == other.hasFirebasePush
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
        result = 31 * result + (schoolPresenceInstitutionId?.hashCode() ?: 0)
        result = 31 * result + schoolWifiSsids.hashCode()
        result = 31 * result + schoolWifiBssids.hashCode()
        result = 31 * result + attendanceGraceMinutes
        result = 31 * result + lateSyncReviewWindowSeconds.hashCode()
        result = 31 * result + attendanceQrNonceReuseThreshold
        result = 31 * result + hasVerificationEmail.hashCode()
        result = 31 * result + (verificationEmailSender?.hashCode() ?: 0)
        result = 31 * result + (mfaEncryptionKey != null).hashCode()
        result = 31 * result + hasFirebasePush.hashCode()
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
            "lateSyncReviewWindowSeconds=$lateSyncReviewWindowSeconds, " +
            "attendanceQrNonceReuseThreshold=$attendanceQrNonceReuseThreshold, " +
            "verificationEmailConfigured=$hasVerificationEmail, " +
            "mfaEncryptionConfigured=${mfaEncryptionKey != null}, " +
            "firebasePushConfigured=$hasFirebasePush, " +
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
        val schoolPresenceInstitutionId=env("SCHOOL_PRESENCE_INSTITUTION_ID")?.takeIf(String::isNotEmpty)
        if(schoolPresenceInstitutionId!=null && (schoolPresenceInstitutionId.isBlank() || schoolPresenceInstitutionId!=schoolPresenceInstitutionId.trim() || schoolPresenceInstitutionId.length>128 || schoolPresenceInstitutionId.any(Char::isISOControl))) throw ConfigurationException("SCHOOL_PRESENCE_INSTITUTION_ID must be an explicit bounded institution identifier")
        val schoolWifiSsids = parseCsv(env("SCHOOL_WIFI_SSIDS"))
        val schoolWifiBssids = parseCsv(env("SCHOOL_WIFI_BSSIDS")).map { it.lowercase() }.toSet()
        val attendanceGraceMinutes = env("ATTENDANCE_GRACE_MINUTES")?.trim()?.toIntOrNull() ?: 5
        if (attendanceGraceMinutes !in 0..15) {
            throw ConfigurationException("ATTENDANCE_GRACE_MINUTES must be between 0 and 15")
        }
        val lateSyncReviewWindowSeconds = boundedLongSetting(
            "LATE_SYNC_REVIEW_WINDOW_SECONDS",
            env("LATE_SYNC_REVIEW_WINDOW_SECONDS"),
            ApiSettings.DEFAULT_LATE_SYNC_REVIEW_WINDOW_SECONDS,
            1L..24L * 60L * 60L,
        )
        val attendanceQrNonceReuseThreshold = boundedIntSetting(
            "ATTENDANCE_QR_NONCE_REUSE_THRESHOLD",
            env("ATTENDANCE_QR_NONCE_REUSE_THRESHOLD"),
            ApiSettings.DEFAULT_ATTENDANCE_QR_NONCE_REUSE_THRESHOLD,
            1..1_000,
        )

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

        val verificationEmailKey = env("VERIFICATION_EMAIL_API_KEY")?.takeIf { it.isNotBlank() }
        val verificationEmailSender = env("VERIFICATION_EMAIL_SENDER")?.trim()?.takeIf { it.isNotEmpty() }
        if ((verificationEmailKey == null) != (verificationEmailSender == null)) throw ConfigurationException("Configure both VERIFICATION_EMAIL_API_KEY and VERIFICATION_EMAIL_SENDER")
        if (verificationEmailKey != null && (verificationEmailKey.length < 20 || verificationEmailKey.any { it.isISOControl() }))
            throw ConfigurationException("Invalid VERIFICATION_EMAIL_API_KEY")
        if (verificationEmailSender != null && (verificationEmailSender.length > 128 ||
            !Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$").matches(verificationEmailSender)))
            throw ConfigurationException("Invalid VERIFICATION_EMAIL_SENDER")

        val firebaseJson = env("FIREBASE_SERVICE_ACCOUNT_JSON_B64")?.takeIf { it.isNotBlank() }?.let { encoded ->
            if (encoded.length > 24_000 || !encoded.matches(Regex("[A-Za-z0-9+/]+={0,2}")))
                throw ConfigurationException("Invalid Firebase credentials encoding")
            val decoded = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
                ?: throw ConfigurationException("Invalid Firebase credentials encoding")
            val json = runCatching { decoded.toString(Charsets.UTF_8) }.getOrElse {
                throw ConfigurationException("Invalid Firebase credentials encoding")
            }
            org.companerodeescuela.api.devices.FirebaseServiceAccount.parse(json)
            json
        }
        val mfaKey = env("MFA_ENCRYPTION_KEY")?.let { encoded ->
            val decoded = runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull()
            if (!Regex("[A-Za-z0-9_-]{43}").matches(encoded) || decoded?.size != 32 ||
                Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) != encoded)
                throw ConfigurationException("Invalid MFA_ENCRYPTION_KEY; require canonical unpadded base64url of 32 random bytes")
            decoded
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
            schoolPresenceInstitutionId=schoolPresenceInstitutionId,
            schoolWifiSsids = schoolWifiSsids,
            schoolWifiBssids = schoolWifiBssids,
            attendanceGraceMinutes = attendanceGraceMinutes,
            verificationEmailApiKey = verificationEmailKey?.toCharArray(),
            verificationEmailSender = verificationEmailSender,
            mfaEncryptionKey = mfaKey,
            firebaseServiceAccountJson = firebaseJson?.toCharArray(),
            lateSyncReviewWindowSeconds = lateSyncReviewWindowSeconds,
            attendanceQrNonceReuseThreshold = attendanceQrNonceReuseThreshold,
        )
    }

    private fun boundedLongSetting(name: String, raw: String?, default: Long, range: LongRange): Long {
        if (raw == null) return default
        val value = raw.trim().toLongOrNull()
            ?: throw ConfigurationException("$name must be an integer between ${range.first} and ${range.last}")
        if (value !in range) throw ConfigurationException("$name must be an integer between ${range.first} and ${range.last}")
        return value
    }

    private fun boundedIntSetting(name: String, raw: String?, default: Int, range: IntRange): Int {
        if (raw == null) return default
        val value = raw.trim().toIntOrNull()
            ?: throw ConfigurationException("$name must be an integer between ${range.first} and ${range.last}")
        if (value !in range) throw ConfigurationException("$name must be an integer between ${range.first} and ${range.last}")
        return value
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
