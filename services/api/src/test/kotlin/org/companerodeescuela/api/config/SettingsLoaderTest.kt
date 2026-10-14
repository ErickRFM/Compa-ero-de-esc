package org.companerodeescuela.api.config

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SettingsLoaderTest {

    private fun loader(vararg values: Pair<String, String>): SettingsLoader {
        val provided = values.toMap()
        val map = if ("APP_ENV" in provided) provided else provided + ("APP_ENV" to "local")
        return SettingsLoader(env = { map[it] })
    }

    @Test
    @DisplayName("Local defaults apply when APP_ENV is explicitly local")
    fun loadsWithMinimalEnvironment() {
        val settings = loader("APP_ENV" to "local", "API_PORT" to "8080", "MONGODB_DATABASE" to "companero").load()

        assertEquals(Environment.LOCAL, settings.environment)
        assertEquals("0.0.0.0", settings.host)
        assertEquals(8080, settings.port)
        assertEquals("companero-api", settings.serviceName)
        assertEquals("v1", settings.apiVersion)
        assertEquals(7_200L, settings.lateSyncReviewWindowSeconds)
        assertEquals(3, settings.attendanceQrNonceReuseThreshold)
        assertFalse(settings.mongo.isConfigured)
        assertFalse(settings.hasAuthentication)
        assertFalse(settings.trustedProxy)
        assertEquals(1L, settings.loginProgressiveDelayBaseSeconds)
        assertEquals(30L, settings.loginProgressiveDelayMaxSeconds)
        assertEquals(60L, settings.refreshRotationGraceSeconds)
        assertEquals(90L, settings.maximumRefreshSessionAgeDays)
    }

    @Test
    fun trustedProxyAndProgressiveDelayMustBeExplicitlyValid() {
        val enabled = loader(
            "API_PORT" to "8080", "MONGODB_DATABASE" to "c", "TRUSTED_PROXY" to "true",
            "LOGIN_PROGRESSIVE_DELAY_BASE_SECONDS" to "2", "LOGIN_PROGRESSIVE_DELAY_MAX_SECONDS" to "12",
        ).load()
        assertTrue(enabled.trustedProxy)
        assertEquals(2L, enabled.loginProgressiveDelayBaseSeconds)
        assertEquals(12L, enabled.loginProgressiveDelayMaxSeconds)

        assertContains(assertFailsWith<ConfigurationException> {
            loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c", "TRUSTED_PROXY" to "yes").load()
        }.message!!, "TRUSTED_PROXY")
        assertContains(assertFailsWith<ConfigurationException> {
            loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c", "LOGIN_PROGRESSIVE_DELAY_BASE_SECONDS" to "31").load()
        }.message!!, "LOGIN_PROGRESSIVE_DELAY_BASE_SECONDS")
        assertContains(assertFailsWith<ConfigurationException> {
            loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c", "LOGIN_PROGRESSIVE_DELAY_BASE_SECONDS" to "10", "LOGIN_PROGRESSIVE_DELAY_MAX_SECONDS" to "2").load()
        }.message!!, "must be at least")
    }

    @Test
    fun refreshSessionWindowsAreBoundedAndFailFast() {
        val configured = loader(
            "API_PORT" to "8080", "MONGODB_DATABASE" to "c",
            "REFRESH_ROTATION_GRACE_SECONDS" to "45", "MAX_REFRESH_SESSION_AGE_DAYS" to "120",
        ).load()
        assertEquals(45L, configured.refreshRotationGraceSeconds)
        assertEquals(120L, configured.maximumRefreshSessionAgeDays)

        assertContains(assertFailsWith<ConfigurationException> {
            loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c", "REFRESH_ROTATION_GRACE_SECONDS" to "0").load()
        }.message!!, "REFRESH_ROTATION_GRACE_SECONDS")
        assertContains(assertFailsWith<ConfigurationException> {
            loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c", "MAX_REFRESH_SESSION_AGE_DAYS" to "bad").load()
        }.message!!, "MAX_REFRESH_SESSION_AGE_DAYS")
    }

    @Test
    @DisplayName("An invalid port aborts startup instead of defaulting")
    fun rejectsInvalidPort() {
        val error = assertFailsWith<ConfigurationException> {
            loader("API_PORT" to "not-a-port", "MONGODB_DATABASE" to "companero").load()
        }
        assertContains(error.message!!, "API_PORT")
    }

    @Test
    @DisplayName("Attendance QR abuse controls use bounded configurable defaults")
    fun attendanceQrDecisionDefaultsAndOverrides() {
        val defaults = loader("API_PORT" to "8080", "MONGODB_DATABASE" to "companero").load()
        val configured = loader(
            "API_PORT" to "8080",
            "MONGODB_DATABASE" to "companero",
            "LATE_SYNC_REVIEW_WINDOW_SECONDS" to "3600",
            "ATTENDANCE_QR_NONCE_REUSE_THRESHOLD" to "4",
        ).load()

        assertEquals(7_200L, defaults.lateSyncReviewWindowSeconds)
        assertEquals(3, defaults.attendanceQrNonceReuseThreshold)
        assertEquals(3_600L, configured.lateSyncReviewWindowSeconds)
        assertEquals(4, configured.attendanceQrNonceReuseThreshold)
    }

    @Test
    @DisplayName("Invalid attendance QR abuse settings fail startup")
    fun invalidAttendanceQrSettingsAreRejected() {
        for ((name, value) in listOf(
            "LATE_SYNC_REVIEW_WINDOW_SECONDS" to "not-a-number",
            "LATE_SYNC_REVIEW_WINDOW_SECONDS" to "0",
            "ATTENDANCE_QR_NONCE_REUSE_THRESHOLD" to "not-a-number",
            "ATTENDANCE_QR_NONCE_REUSE_THRESHOLD" to "0",
        )) {
            val error = assertFailsWith<ConfigurationException> {
                loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c", name to value).load()
            }
            assertContains(error.message!!, name)
        }
    }

    @Test
    @DisplayName("A missing database name aborts startup")
    fun rejectsMissingDatabaseName() {
        val error = assertFailsWith<ConfigurationException> {
            loader("API_PORT" to "8080").load()
        }
        assertContains(error.message!!, "MONGODB_DATABASE")
    }

    @Test
    @DisplayName("An unknown APP_ENV is rejected rather than silently downgraded")
    fun rejectsUnknownEnvironment() {
        val error = assertFailsWith<ConfigurationException> {
            loader("APP_ENV" to "prodution", "API_PORT" to "8080", "MONGODB_DATABASE" to "c").load()
        }
        assertContains(error.message!!, "APP_ENV")
    }

    @Test
    @DisplayName("A missing APP_ENV is rejected instead of selecting local mode")
    fun rejectsMissingEnvironment() {
        val error = assertFailsWith<ConfigurationException> {
            SettingsLoader(env = { name ->
                mapOf("API_PORT" to "8080", "MONGODB_DATABASE" to "c")[name]
            }).load()
        }
        assertContains(error.message!!, "APP_ENV")
    }

    @Test
    @DisplayName("Production requires a JWT secret")
    fun productionRequiresSecret() {
        val error = assertFailsWith<ConfigurationException> {
            loader("APP_ENV" to "production", "API_PORT" to "8080", "MONGODB_DATABASE" to "c").load()
        }
        assertContains(error.message!!, "JWT_SECRET")
    }

    @Test
    @DisplayName("Production requires MongoDB and the MFA encryption key")
    fun productionRequiresMongoAndMfaKey() {
        val jwt = "j".repeat(40)
        val mongoError = assertFailsWith<ConfigurationException> {
            loader("APP_ENV" to "production", "API_PORT" to "8080", "MONGODB_DATABASE" to "c",
                "JWT_SECRET" to jwt, "MFA_ENCRYPTION_KEY" to "A".repeat(43)).load()
        }
        assertContains(mongoError.message!!, "MONGODB_URI")

        val mfaError = assertFailsWith<ConfigurationException> {
            loader("APP_ENV" to "production", "API_PORT" to "8080", "MONGODB_DATABASE" to "c",
                "MONGODB_URI" to "mongodb://localhost:27017", "JWT_SECRET" to jwt).load()
        }
        assertContains(mfaError.message!!, "MFA_ENCRYPTION_KEY")
    }

    @Test
    @DisplayName("JWT and attendance QR signing secrets cannot be reused")
    fun rejectsReusedJwtAndAttendanceQrSecret() {
        val secret = "same-secret-".padEnd(40, 'x')
        val error = assertFailsWith<ConfigurationException> {
            loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c",
                "JWT_SECRET" to secret, "ATTENDANCE_QR_SECRET" to secret).load()
        }
        assertContains(error.message!!, "ATTENDANCE_QR_SECRET")
    }

    @Test
    @DisplayName("Present invalid numeric configuration fails instead of using a default")
    fun rejectsInvalidNumericConfiguration() {
        for (name in listOf("ATTENDANCE_GRACE_MINUTES", "MONGODB_CONNECT_TIMEOUT_MS", "MONGODB_SERVER_SELECTION_TIMEOUT_MS")) {
            val error = assertFailsWith<ConfigurationException>(name) {
                loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c", name to "invalid").load()
            }
            assertContains(error.message!!, name)
        }
        for ((name, value) in listOf("ATTENDANCE_GRACE_MINUTES" to "16", "MONGODB_CONNECT_TIMEOUT_MS" to "0",
            "MONGODB_SERVER_SELECTION_TIMEOUT_MS" to "-1")) {
            val error = assertFailsWith<ConfigurationException>(name) {
                loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c", name to value).load()
            }
            assertContains(error.message!!, name)
        }
    }

    @Test
    @DisplayName("A short JWT secret is rejected in every environment")
    fun rejectsShortSecret() {
        val error = assertFailsWith<ConfigurationException> {
            loader(
                "API_PORT" to "8080",
                "MONGODB_DATABASE" to "c",
                "JWT_SECRET" to "too-short",
            ).load()
        }
        assertContains(error.message!!, "JWT_SECRET")
    }

    @Test
    fun currentUserCacheTtlDefaultsOffAndCanBeBounded() {
        val defaults = loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c").load()
        val configured = loader(
            "API_PORT" to "8080",
            "MONGODB_DATABASE" to "c",
            "CURRENT_USER_CACHE_TTL_MILLIS" to "5000",
        ).load()

        assertEquals(0, defaults.currentUserCacheTtlMillis)
        assertEquals(5_000, configured.currentUserCacheTtlMillis)
    }

    @Test
    fun invalidCurrentUserCacheTtlFailsFast() {
        for (raw in listOf("", "invalid", "-1", "5001")) {
            val error = assertFailsWith<ConfigurationException> {
                loader(
                    "API_PORT" to "8080",
                    "MONGODB_DATABASE" to "c",
                    "CURRENT_USER_CACHE_TTL_MILLIS" to raw,
                ).load()
            }
            assertContains(error.message!!, "CURRENT_USER_CACHE_TTL_MILLIS")
        }
    }

    @Test
    @DisplayName("A valid secret enables authentication and is not exposed in toString")
    fun secretIsNotLogged() {
        val secret = "s".repeat(40)
        val settings = loader(
            "API_PORT" to "8080",
            "MONGODB_DATABASE" to "companero",
            "MONGODB_URI" to "mongodb+srv://user:pass@cluster.example/?retryWrites=true",
            "JWT_SECRET" to secret,
        ).load()

        assertTrue(settings.hasAuthentication)
        assertEquals(secret, settings.jwtSecret?.concatToString())

        val rendered = settings.toString()
        assertFalse(rendered.contains(secret), "toString must not contain the secret")
        assertFalse(rendered.contains("mongodb+srv"), "toString must not contain the URI")
        assertContains(rendered, "authenticationConfigured=true")
        assertContains(rendered, "mongoConfigured=true")
    }

    @Test
    @DisplayName("QR signing secret is optional, validated, and never logged")
    fun attendanceQrSecretIsProtected() {
        val secret = "q".repeat(48)
        val settings = loader(
            "API_PORT" to "8080",
            "MONGODB_DATABASE" to "c",
            "ATTENDANCE_QR_SECRET" to secret,
        ).load()

        assertTrue(settings.hasAttendanceQrSigning)
        assertEquals(secret, settings.attendanceQrSecret?.concatToString())
        assertFalse(settings.toString().contains(secret))
        assertContains(settings.toString(), "attendanceQrSigningConfigured=true")
    }

    @Test
    @DisplayName("Short QR signing secret is rejected")
    fun rejectsShortAttendanceQrSecret() {
        val error = assertFailsWith<ConfigurationException> {
            loader(
                "API_PORT" to "8080",
                "MONGODB_DATABASE" to "c",
                "ATTENDANCE_QR_SECRET" to "too-short",
            ).load()
        }
        assertContains(error.message!!, "ATTENDANCE_QR_SECRET")
    }

    @Test
    @DisplayName("Absent JWT_SECRET yields null rather than an empty array")
    fun absentSecretIsNull() {
        val settings = loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c").load()
        assertNull(settings.jwtSecret)
    }

    @Test
    @DisplayName("Mongo timeouts fall back to safe defaults")
    fun mongoTimeoutsDefault() {
        val settings = loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c").load()

        assertEquals(MongoSettings.DEFAULT_CONNECT_TIMEOUT_MS, settings.mongo.connectTimeoutMs)
        assertEquals(
            MongoSettings.DEFAULT_SERVER_SELECTION_TIMEOUT_MS,
            settings.mongo.serverSelectionTimeoutMs,
        )
    }

    @Test
    @DisplayName("Settings with identical values are equal without comparing the secret")
    fun equalsIgnoresSecretContent() {
        val base = loader("API_PORT" to "8080", "MONGODB_DATABASE" to "c")
        val first = base.load()
        val second = base.load()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}

class EnvironmentTest {

    @Test
    @DisplayName("Missing or empty APP_ENV is rejected")
    fun rejectsMissingEnvironment() {
        assertFailsWith<ConfigurationException> { Environment.parse(null) }
        assertFailsWith<ConfigurationException> { Environment.parse("  ") }
    }

    @Test
    @DisplayName("Common aliases are accepted")
    fun acceptsAliases() {
        assertEquals(Environment.LOCAL, Environment.parse("local"))
        assertEquals(Environment.DEVELOPMENT, Environment.parse("dev"))
        assertEquals(Environment.DEVELOPMENT, Environment.parse("development"))
        assertEquals(Environment.STAGING, Environment.parse("staging"))
        assertEquals(Environment.PRODUCTION, Environment.parse("PROD"))
    }

    @Test
    @DisplayName("Only production reports isProduction")
    fun isProduction() {
        assertTrue(Environment.PRODUCTION.isProduction)
        assertFalse(Environment.STAGING.isProduction)
        assertFalse(Environment.LOCAL.isProduction)
    }
}
