package org.companerodeescuela.shared.contracts

import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthResponseSerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    @DisplayName("HealthResponse serializes the documented wire format")
    fun serializesHealthResponse() {
        val response = HealthResponse(
            service = "companero-api",
            status = ServiceStatus.UP,
            version = "0.1.0",
            environment = "local",
            timestamp = Instant.parse("2026-01-31T12:00:00Z"),
            dependencies = listOf(
                DependencyStatus(name = "mongodb", status = ServiceStatus.UP),
            ),
        )

        val encoded = json.encodeToString(response)

        assertContains(encoded, "\"service\":\"companero-api\"")
        assertContains(encoded, "\"status\":\"up\"")
        assertContains(encoded, "\"timestamp\":\"2026-01-31T12:00:00Z\"")
        assertContains(encoded, "\"name\":\"mongodb\"")
    }

    @Test
    @DisplayName("Enum values are stable snake_case strings")
    fun usesStableEnumWireValues() {
        assertEquals("\"up\"", json.encodeToString(ServiceStatus.UP))
        assertEquals("\"degraded\"", json.encodeToString(ServiceStatus.DEGRADED))
        assertEquals("\"student\"", json.encodeToString(UserRole.STUDENT))
        assertEquals("\"super_admin\"", json.encodeToString(UserRole.SUPER_ADMIN))
        assertEquals("\"dependency_unavailable\"", json.encodeToString(ApiErrorCode.DEPENDENCY_UNAVAILABLE))
    }

    @Test
    @DisplayName("Instant round-trips through the ISO-8601 serializer")
    fun roundTripsTimestamp() {
        val original = Instant.parse("2026-06-15T08:30:45Z")
        val encoded = json.encodeToString(InstantAsIso8601Serializer, original)
        val decoded = json.decodeFromString(InstantAsIso8601Serializer, encoded)

        assertEquals(original, decoded)
    }
}

class UserRoleTest {

    @Test
    @DisplayName("Only students are non-staff")
    fun staffClassification() {
        assertFalse(UserRole.STUDENT.isStaff)
        assertTrue(UserRole.TEACHER.isStaff)
        assertTrue(UserRole.COORDINATOR.isStaff)
        assertTrue(UserRole.ADMIN.isStaff)
        assertTrue(UserRole.SUPER_ADMIN.isStaff)
    }

    @Test
    @DisplayName("Administrative roles are coordinator, admin and super admin")
    fun administrativeClassification() {
        assertFalse(UserRole.STUDENT.isAdministrative)
        assertFalse(UserRole.TEACHER.isAdministrative)
        assertTrue(UserRole.COORDINATOR.isAdministrative)
        assertTrue(UserRole.ADMIN.isAdministrative)
        assertTrue(UserRole.SUPER_ADMIN.isAdministrative)
    }
}

class UserSummaryTest {

    @Test
    @DisplayName("isStaff is true when any role is staff")
    fun isStaffAggregatesRoles() {
        assertFalse(UserSummary(id = "1", displayName = "Ana").isStaff)
        assertTrue(UserSummary(id = "1", displayName = "Ana", roles = setOf(UserRole.TEACHER)).isStaff)
        assertTrue(
            UserSummary(
                id = "1",
                displayName = "Ana",
                roles = setOf(UserRole.STUDENT, UserRole.COORDINATOR),
            ).isStaff,
        )
    }
}
