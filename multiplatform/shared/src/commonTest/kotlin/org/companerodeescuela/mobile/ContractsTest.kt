package org.companerodeescuela.mobile

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.shared.contracts.*
import kotlin.test.*

class ContractsTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun loginUsesExistingWireContract() {
        assertEquals("""{"username":"student","password":"test-only"}""",
            json.encodeToString(LoginRequest("student", "test-only")))
        val response = json.decodeFromString<ApiResponse<LoginResponse>>(
            """{"data":{"accessToken":"test-token","expiresAtEpochSeconds":1900000000,"sessionId":"test-session","refreshToken":"test-refresh","user":{"id":"s1","displayName":"Alumno","roles":["student"]}},"requestId":"r1"}""")
        assertEquals("s1", response.data.user.id)
        assertEquals(setOf(UserRole.STUDENT), response.data.user.roles)
        assertFalse(response.data.user.isStaff)
        assertEquals("r1", response.requestId)
    }
    @Test fun pendingTeacherIsNotStaff() {
        assertFalse(UserRole.TEACHER_PENDING.isStaff)
        assertTrue(UserRole.TEACHER.isStaff)
        assertTrue(UserRole.SUPER_ADMIN.isAdministrative)
        assertFalse(UserRole.TEACHER.isAdministrative)
    }
    @Test fun apiErrorsRetainSerialNames() {
        val error = json.decodeFromString<ApiError>(
            """{"code":"dependency_unavailable","message":"Unavailable","requestId":"r2"}""")
        assertEquals(ApiErrorCode.DEPENDENCY_UNAVAILABLE, error.code)
    }
    @Test fun invitationContractsAreAvailableOnAllTargets() {
        val request = json.encodeToString(
            AcceptRegistrationInvitationRequest("inv-123", "code-456", "op-789"))
        assertTrue(request.contains("\"invitationId\":\"inv-123\""))
        assertTrue(request.contains("\"code\":\"code-456\""))
    }
    @Test fun academicContractsAreAvailableOnAllTargets() {
        val profile = AcademicProfile("s1", "Student Name")
        val payload = json.encodeToString(profile)
        assertTrue(payload.contains("\"id\":\"s1\""))
        assertTrue(payload.contains("\"displayName\":\"Student Name\""))
    }
    @Test fun attendanceContractsAreAvailableOnAllTargets() {
        val payload = json.encodeToString(AttendanceStatus.VERIFIED)
        assertTrue(payload.contains("\"verified\""))
    }
    @Test fun privilegeSecurityContractsAreAvailableOnAllTargets() {
        val status = MfaStatus(enabled = true, available = true)
        val payload = json.encodeToString(status)
        assertTrue(payload.contains("\"enabled\":true"))
        assertTrue(payload.contains("\"available\":true"))
    }
    @Test fun stepUpActionsAreAvailableOnAllTargets() {
        val payload = json.encodeToString(StepUpAction.ADMIN_INVITE)
        assertTrue(payload.contains("\"ADMIN_INVITE\""))
    }
}
