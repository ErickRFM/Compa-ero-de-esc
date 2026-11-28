package org.companerodeescuela.api.admin

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.audit.InMemoryPlatformAuditLog
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.auth.RefreshSession
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.auth.authRoutes
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.invitations.InMemoryInvitationRepository
import org.companerodeescuela.api.invitations.InvitationDispatchService
import org.companerodeescuela.api.invitations.InvitationEmailGateway
import org.companerodeescuela.api.invitations.InvitationStatus
import org.companerodeescuela.api.invitations.invitationRoutes
import org.companerodeescuela.api.mail.VerificationEmailGateway
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.security.Totp
import org.companerodeescuela.api.security.mfaRoutes
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.api.tutoring.InMemoryTutorAssignmentRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentService
import org.companerodeescuela.shared.contracts.AcceptRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.AdminUserSummary
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AuthorizeRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.LoginResponse
import org.companerodeescuela.shared.contracts.MfaStepUpRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.PrivilegedCommandDigests
import org.companerodeescuela.shared.contracts.RegisterRequest
import org.companerodeescuela.shared.contracts.RegistrationAccountType
import org.companerodeescuela.shared.contracts.RegistrationInvitationDispatchReceipt
import org.companerodeescuela.shared.contracts.RegistrationInvitationPreparation
import org.companerodeescuela.shared.contracts.RegistrationInvitationSummary
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.StepUpProof
import org.companerodeescuela.shared.contracts.TutorApprovalScope
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import org.companerodeescuela.shared.contracts.VerifyEmailRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvitationAuthorizationRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val password = "super-admin-password-123"

    private class MutableClock : Clock() {
        var now: Instant = Instant.now()

        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private inner class Harness {
        val clock = MutableClock()
        val settings =
            TestFixtures.settings().copy(
                environment = Environment.LOCAL,
                jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray(),
                mfaEncryptionKey = ByteArray(32) { it.toByte() },
            )
        val accounts = InMemoryPlatformAccountRepository()
        val sessions = InMemoryRefreshSessionRepository()
        val invitations = InMemoryInvitationRepository()
        val groups = InMemoryAcademicGroupRepository()
        val tutorAssignments = InMemoryTutorAssignmentRepository()
        val institutions = InMemoryInstitutionRepository(listOf(InstitutionSummary("UPTLAX", "UPTLAX")))
        val identity = MockIdentityProvider()
        val mfa = MfaService(accounts, InMemoryMfaRepository(), MfaSecretCipher(ByteArray(32) { it.toByte() }), clock)
        val tutoring = TutorAssignmentService(tutorAssignments, groups, clock, accounts)
        val auditLog = InMemoryPlatformAuditLog()
        val adminService =
            AccountAdministrationService(
                accounts = accounts,
                institutions = institutions,
                groups = groups,
                tutoring = tutoring,
                mfa = mfa,
                clock = clock,
                invitations = invitations,
            )
        val sentCodes = mutableMapOf<String, String>()
        val verificationTokens = mutableMapOf<String, String>()
        val inviteService =
            InvitationDispatchService(
                accounts = accounts,
                mfa = mfa,
                repository = invitations,
                email =
                    InvitationEmailGateway { recipient, code, _, _ ->
                        sentCodes[recipient] = code
                        VerificationDeliveryStatus.ACCEPTED
                    },
                institutions = institutions,
                clock = clock,
            )
        val tokens = AuthTokenService(settings)
        lateinit var ownerToken: String
        lateinit var ownerSecret: ByteArray

        suspend fun bootstrapOwner() {
            ownerToken = createSessionAccount("owner", "owner@example.test", setOf(UserRole.SUPER_ADMIN), "UPTLAX")
            val ownerAccount = requireNotNull(accounts.findById("owner"))
            val enrollment = mfa.beginEnrollment(ownerAccount, password)
            mfa.confirmEnrollment(
                ownerAccount,
                enrollment.enrollmentId,
                Totp.code(Totp.decodeSecret(enrollment.secret), clock.now.epochSecond),
            )
            ownerSecret = Totp.decodeSecret(enrollment.secret)
        }

        suspend fun createSessionAccount(
            id: String,
            email: String,
            roles: Set<UserRole>,
            institutionId: String? = null,
            capabilities: Set<AdminCapability> = emptySet(),
        ): String {
            val account =
                PlatformAccount(
                    id = id,
                    displayName = id,
                    email = email,
                    passwordHash = PasswordHasher().hash(password),
                    roles = roles,
                    accountStatus = AccountStatus.ACTIVE,
                    emailVerifiedAt = clock.now.minusSeconds(60),
                    institutionId = institutionId,
                    adminCapabilities = capabilities,
                )
            accounts.create(account)
            val summary = account.toUserSummary()
            val sessionId = "sess-$id"
            sessions.create(
                RefreshSession(
                    id = sessionId,
                    tokenHash = "hash-$id",
                    user = summary,
                    createdAt = clock.now,
                    expiresAt = clock.now.plusSeconds(3600),
                    identitySource = SessionIdentitySource.NATIVE,
                ),
            )
            return tokens.issue(summary, sessionId).value
        }

        fun mount(builder: ApplicationTestBuilder) {
            builder.application {
                val sessionAuthority = PlatformSessionAuthority(accounts, identity, clock)
                configurePlugins(settings, sessions, sessionAuthority)
                routing {
                    authRoutes(
                        settings = settings,
                        identityProvider = identity,
                        sessions = sessions,
                        accounts = accounts,
                        institutions = institutions,
                        verificationEmail =
                            VerificationEmailGateway { email, token, _ ->
                                verificationTokens[email] = token
                                VerificationDeliveryStatus.ACCEPTED
                            },
                        verificationClock = clock,
                        sessionAuthority = sessionAuthority,
                        invitations = invitations,
                    )
                    mfaRoutes(settings, identity, sessions, accounts, mfa)
                    invitationRoutes(
                        settings = settings,
                        identity = identity,
                        sessions = sessions,
                        accounts = accounts,
                        service = inviteService,
                        invitations = invitations,
                        clock = clock,
                        auditLog = auditLog,
                    )
                    accountAdministrationRoutes(
                        settings = settings,
                        identity = identity,
                        sessions = sessions,
                        accounts = accounts,
                        service = adminService,
                        auditLog = auditLog,
                    )
                }
            }
        }

        suspend fun nextStepUpProof(
            builder: ApplicationTestBuilder,
            action: StepUpAction,
            resourceId: String,
            operationId: String,
            digest: String,
        ): String {
            clock.now = clock.now.plusSeconds(35)
            val otp = Totp.code(ownerSecret, clock.now.epochSecond)
            val resp =
                builder.client.post("/auth/mfa/step-up") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(MfaStepUpRequest(password, otp, action, resourceId, operationId, digest)))
                }
            assertEquals(HttpStatusCode.OK, resp.status)
            return json.decodeFromString<ApiResponse<StepUpProof>>(resp.bodyAsText()).data.proof
        }

        suspend fun issueInvitation(
            builder: ApplicationTestBuilder,
            recipientEmail: String,
            profile: String,
            opSuffix: String,
        ): String {
            val req = PrepareRegistrationInvitationRequest(recipientEmail, "UPTLAX", profile, "issue-$opSuffix")
            val prepResp =
                builder.client.post("/admin/invitations/prepare") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(req))
                }
            assertEquals(HttpStatusCode.OK, prepResp.status)
            val prep = json.decodeFromString<ApiResponse<RegistrationInvitationPreparation>>(prepResp.bodyAsText()).data
            val proof = nextStepUpProof(builder, StepUpAction.ADMIN_INVITE, prep.resourceId, prep.operationId, prep.payloadDigest)
            val sendResp =
                builder.client.post("/admin/invitations") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(req.copy(proof = proof)))
                }
            assertEquals(HttpStatusCode.Created, sendResp.status)
            return json.decodeFromString<ApiResponse<RegistrationInvitationDispatchReceipt>>(sendResp.bodyAsText()).data.invitationId
        }
    }

    @Test
    fun `accepting teacher and admin invitations grants zero privilege until super admin authorizes with MFA`() =
        testApplication {
            val h = Harness()
            h.bootstrapOwner()
            val regularAdminToken =
                h.createSessionAccount(
                    "regular-admin",
                    "admin@example.test",
                    setOf(UserRole.ADMIN),
                    "UPTLAX",
                    setOf(AdminCapability.APPROVE_STAFF, AdminCapability.MANAGE_USERS, AdminCapability.MANAGE_ACADEMICS),
                )
            val teacherCandidateToken = h.createSessionAccount("cand-teacher", "teacher.cand@example.test", setOf(UserRole.STUDENT))
            val adminCandidateToken = h.createSessionAccount("cand-admin", "admin.cand@example.test", setOf(UserRole.STUDENT))
            h.mount(this)

            // 1. Unaccepted TEACHER invitation cannot be authorized
            val teacherInvId = h.issueInvitation(this, "teacher.cand@example.test", "TEACHER", "teacher01")
            val prematureCmd =
                AuthorizeRegistrationInvitationRequest(
                    operationId = "auth-premature-01",
                    invitationId = teacherInvId,
                    expectedAuthRevision = 0,
                    institutionId = "UPTLAX",
                    profile = "TEACHER",
                    proof = "",
                )
            val prematureProof =
                h.nextStepUpProof(
                    this,
                    StepUpAction.ADMIN_AUTHORITY,
                    teacherInvId,
                    prematureCmd.operationId,
                    PrivilegedCommandDigests.invitationAuthorization(teacherInvId, prematureCmd),
                )
            assertEquals(
                HttpStatusCode.Conflict,
                client
                    .post("/admin/accounts/invitation-authorization") {
                        bearerAuth(h.ownerToken)
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(prematureCmd.copy(proof = prematureProof)))
                    }.status,
            )

            // 2. Acceptance grants ZERO teacher/admin authority
            assertEquals(
                HttpStatusCode.OK,
                client
                    .post("/auth/invitations/accept") {
                        bearerAuth(teacherCandidateToken)
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                AcceptRegistrationInvitationRequest(
                                    teacherInvId,
                                    requireNotNull(h.sentCodes["teacher.cand@example.test"]),
                                    "acc-teacher-01",
                                ),
                            ),
                        )
                    }.status,
            )
            val afterAcceptTeacher = requireNotNull(h.accounts.findById("cand-teacher"))
            assertEquals(setOf(UserRole.STUDENT), afterAcceptTeacher.roles)
            assertNull(afterAcceptTeacher.institutionId)

            // Regular ADMIN cannot authorize accepted invitations (403)
            assertEquals(
                HttpStatusCode.Forbidden,
                client
                    .post("/admin/accounts/invitation-authorization") {
                        bearerAuth(regularAdminToken)
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(prematureCmd.copy(operationId = "auth-nonowner-01", proof = "A".repeat(43))))
                    }.status,
            )

            // 3. SUPER_ADMIN authorizes TEACHER -> grants TEACHER + UPTLAX, invalidates stale session, marks ACTIVE
            val teacherAuthCmd = prematureCmd.copy(operationId = "auth-teacher-01")
            val teacherAuthProof =
                h.nextStepUpProof(
                    this,
                    StepUpAction.ADMIN_AUTHORITY,
                    teacherInvId,
                    teacherAuthCmd.operationId,
                    PrivilegedCommandDigests.invitationAuthorization(teacherInvId, teacherAuthCmd),
                )
            val teacherAuthResp =
                client.post("/admin/accounts/invitation-authorization") {
                    bearerAuth(h.ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(teacherAuthCmd.copy(proof = teacherAuthProof)))
                }
            assertEquals(HttpStatusCode.OK, teacherAuthResp.status)
            val authorizedTeacher = json.decodeFromString<ApiResponse<AdminUserSummary>>(teacherAuthResp.bodyAsText()).data
            assertEquals(setOf(UserRole.TEACHER), authorizedTeacher.user.roles)
            assertEquals("UPTLAX", authorizedTeacher.user.institutionId)
            assertEquals(InvitationStatus.ACTIVE, h.invitations.find(teacherInvId)?.status)
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/admin/accounts") { bearerAuth(teacherCandidateToken) }.status,
            )

            // 4. INSTITUTION_ADMIN invitation -> accept -> authorize -> grants ADMIN + capabilities, NEVER SUPER_ADMIN
            val adminInvId = h.issueInvitation(this, "admin.cand@example.test", "INSTITUTION_ADMIN", "admin001")
            assertEquals(
                HttpStatusCode.OK,
                client
                    .post("/auth/invitations/accept") {
                        bearerAuth(adminCandidateToken)
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                AcceptRegistrationInvitationRequest(
                                    adminInvId,
                                    requireNotNull(h.sentCodes["admin.cand@example.test"]),
                                    "acc-admin-01",
                                ),
                            ),
                        )
                    }.status,
            )
            assertEquals(setOf(UserRole.STUDENT), h.accounts.findById("cand-admin")?.roles)
            val adminAuthCmd =
                AuthorizeRegistrationInvitationRequest(
                    operationId = "auth-admin-01",
                    invitationId = adminInvId,
                    expectedAuthRevision = 0,
                    institutionId = "UPTLAX",
                    profile = "INSTITUTION_ADMIN",
                    proof = "",
                )
            val adminAuthProof =
                h.nextStepUpProof(
                    this,
                    StepUpAction.ADMIN_AUTHORITY,
                    adminInvId,
                    adminAuthCmd.operationId,
                    PrivilegedCommandDigests.invitationAuthorization(adminInvId, adminAuthCmd),
                )
            assertEquals(
                HttpStatusCode.OK,
                client
                    .post("/admin/accounts/invitation-authorization") {
                        bearerAuth(h.ownerToken)
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(adminAuthCmd.copy(proof = adminAuthProof)))
                    }.status,
            )
            val persistedAdmin = requireNotNull(h.accounts.findById("cand-admin"))
            assertEquals(setOf(UserRole.ADMIN), persistedAdmin.roles)
            assertFalse(UserRole.SUPER_ADMIN in persistedAdmin.roles)
            assertEquals("UPTLAX", persistedAdmin.institutionId)
            assertTrue(AdminCapability.APPROVE_STAFF in persistedAdmin.adminCapabilities)
            assertEquals(InvitationStatus.ACTIVE, h.invitations.find(adminInvId)?.status)
        }

    @Test
    fun `tutor and student invitations require explicit authorization and validate tutor group scopes`() =
        testApplication {
            val h = Harness()
            h.bootstrapOwner()
            h.groups.create(
                AcademicGroupRecord(
                    id = "group-6a",
                    name = "6A",
                    active = true,
                    createdAt = h.clock.now.minusSeconds(3600),
                    institutionId = "UPTLAX",
                    periodId = "2026-2",
                    startsAt = h.clock.now.minusSeconds(3600),
                    endsAt = h.clock.now.plusSeconds(86400 * 90L),
                    scopeRevision = 1,
                ),
            )
            val tutorCandidateToken = h.createSessionAccount("cand-tutor", "tutor.cand@example.test", setOf(UserRole.STUDENT))
            val studentCandidateToken = h.createSessionAccount("cand-student", "student.cand@example.test", setOf(UserRole.STUDENT))
            h.mount(this)

            val tutorInvId = h.issueInvitation(this, "tutor.cand@example.test", "TUTOR", "tutor001")
            assertEquals(
                HttpStatusCode.OK,
                client
                    .post("/auth/invitations/accept") {
                        bearerAuth(tutorCandidateToken)
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                AcceptRegistrationInvitationRequest(
                                    tutorInvId,
                                    requireNotNull(h.sentCodes["tutor.cand@example.test"]),
                                    "acc-tutor-01",
                                ),
                            ),
                        )
                    }.status,
            )
            val validUntil = h.clock.now.plusSeconds(86400 * 30L)
            val tutorScope =
                TutorApprovalScope(
                    academicGroupId = "group-6a",
                    periodId = "2026-2",
                    startsAtEpochSeconds = h.clock.now.epochSecond,
                    endsAtEpochSeconds = validUntil.epochSecond,
                    groupScopeRevision = 1,
                )
            val tutorAuthCmd =
                AuthorizeRegistrationInvitationRequest(
                    operationId = "auth-tutor-01",
                    invitationId = tutorInvId,
                    expectedAuthRevision = 0,
                    institutionId = "UPTLAX",
                    profile = "TUTOR",
                    tutorScopes = listOf(tutorScope),
                    proof = "",
                )
            val tutorAuthProof =
                h.nextStepUpProof(
                    this,
                    StepUpAction.ADMIN_AUTHORITY,
                    tutorInvId,
                    tutorAuthCmd.operationId,
                    PrivilegedCommandDigests.invitationAuthorization(tutorInvId, tutorAuthCmd),
                )
            assertEquals(
                HttpStatusCode.OK,
                client
                    .post("/admin/accounts/invitation-authorization") {
                        bearerAuth(h.ownerToken)
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(tutorAuthCmd.copy(proof = tutorAuthProof)))
                    }.status,
            )
            assertEquals(setOf(UserRole.TUTOR), h.accounts.findById("cand-tutor")?.roles)
            assertEquals(InvitationStatus.ACTIVE, h.invitations.find(tutorInvId)?.status)

            val studentInvId = h.issueInvitation(this, "student.cand@example.test", "STUDENT", "student1")
            assertEquals(
                HttpStatusCode.OK,
                client
                    .post("/auth/invitations/accept") {
                        bearerAuth(studentCandidateToken)
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                AcceptRegistrationInvitationRequest(
                                    studentInvId,
                                    requireNotNull(h.sentCodes["student.cand@example.test"]),
                                    "acc-student-01",
                                ),
                            ),
                        )
                    }.status,
            )
            assertNull(h.accounts.findById("cand-student")?.institutionId)
            val studentAuthCmd =
                AuthorizeRegistrationInvitationRequest(
                    operationId = "auth-student-01",
                    invitationId = studentInvId,
                    expectedAuthRevision = 0,
                    institutionId = "UPTLAX",
                    profile = "STUDENT",
                    proof = "",
                )
            val studentAuthProof =
                h.nextStepUpProof(
                    this,
                    StepUpAction.ADMIN_AUTHORITY,
                    studentInvId,
                    studentAuthCmd.operationId,
                    PrivilegedCommandDigests.invitationAuthorization(studentInvId, studentAuthCmd),
                )
            assertEquals(
                HttpStatusCode.OK,
                client
                    .post("/admin/accounts/invitation-authorization") {
                        bearerAuth(h.ownerToken)
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(studentAuthCmd.copy(proof = studentAuthProof)))
                    }.status,
            )
            val affiliatedStudent = requireNotNull(h.accounts.findById("cand-student"))
            assertEquals(setOf(UserRole.STUDENT), affiliatedStudent.roles)
            assertEquals("UPTLAX", affiliatedStudent.institutionId)
            assertEquals(InvitationStatus.ACTIVE, h.invitations.find(studentInvId)?.status)
        }

    @Test
    fun `direct INVITED registration for admin and teacher auto-accepts on email verification without premature privileges`() =
        testApplication {
            val h = Harness()
            h.bootstrapOwner()
            h.mount(this)

            val adminEmail = "nuevo.admin@uptlax.edu.mx"
            val adminInvId = h.issueInvitation(this, adminEmail, "INSTITUTION_ADMIN", "direct-admin-01")
            val adminCode = requireNotNull(h.sentCodes[adminEmail])

            // Reject mismatched email or code
            val wrongEmailResp =
                client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            RegisterRequest(
                                displayName = "Otro Correo",
                                email = "otro@uptlax.edu.mx",
                                password = "secure-password-123",
                                accountType = RegistrationAccountType.INVITED,
                                invitationId = adminInvId,
                                invitationCode = adminCode,
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, wrongEmailResp.status)

            val regResp =
                client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            RegisterRequest(
                                displayName = "Coordinadora UPTLAX",
                                email = adminEmail,
                                password = "secure-password-123",
                                accountType = RegistrationAccountType.INVITED,
                                identityReference = "ADM-UPTLAX-09",
                                invitationId = adminInvId,
                                invitationCode = adminCode,
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, regResp.status)
            val created = json.decodeFromString<ApiResponse<LoginResponse>>(regResp.bodyAsText()).data
            assertEquals(AccountStatus.PENDING_VERIFICATION, created.user.accountStatus)
            assertTrue(created.user.roles.isEmpty())
            assertEquals(InvitationStatus.SENT, h.invitations.find(adminInvId)?.status)

            // Verify email -> auto-accepts invitation and moves to PENDING_APPROVAL with ZERO roles
            val verifyResp =
                client.post("/auth/verification/confirm") {
                    bearerAuth(created.accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(VerifyEmailRequest(requireNotNull(h.verificationTokens[adminEmail]))))
                }
            assertEquals(HttpStatusCode.OK, verifyResp.status)
            val verified = json.decodeFromString<ApiResponse<LoginResponse>>(verifyResp.bodyAsText()).data
            assertEquals(AccountStatus.PENDING_APPROVAL, verified.user.accountStatus)
            assertTrue(verified.user.roles.isEmpty())
            assertTrue(verified.user.invitationAccepted)
            assertNull(verified.user.registrationAccountType)
            assertEquals("INVITED", verified.user.registrationProfile)
            assertEquals(InvitationStatus.ACCEPTED, h.invitations.find(adminInvId)?.status)

            val meResp =
                client.get("/auth/me") {
                    bearerAuth(verified.accessToken)
                }
            assertEquals(HttpStatusCode.OK, meResp.status)
            val meSummary = json.decodeFromString<ApiResponse<UserSummary>>(meResp.bodyAsText()).data
            assertTrue(meSummary.invitationAccepted)
            assertTrue(meSummary.roles.isEmpty())
            assertNull(meSummary.registrationAccountType)
            assertEquals("INVITED", meSummary.registrationProfile)

            val listResp =
                client.get("/admin/invitations") {
                    bearerAuth(h.ownerToken)
                }
            assertEquals(HttpStatusCode.OK, listResp.status)
            val listed = json.decodeFromString<ApiResponse<List<RegistrationInvitationSummary>>>(listResp.bodyAsText()).data
            val summaryItem = listed.first { it.invitationId == adminInvId }
            assertEquals("ACCEPTED", summaryItem.status)
            assertEquals("ADM-UPTLAX-09", summaryItem.acceptedIdentityReference)

            // Explicit MFA authorization by SUPER_ADMIN grants ADMIN + UPTLAX
            val authCmd =
                AuthorizeRegistrationInvitationRequest(
                    operationId = "auth-direct-admin-01",
                    invitationId = adminInvId,
                    expectedAuthRevision = verified.user.authRevision,
                    institutionId = "UPTLAX",
                    profile = "INSTITUTION_ADMIN",
                    proof = "",
                )
            val authProof =
                h.nextStepUpProof(
                    this,
                    StepUpAction.ADMIN_AUTHORITY,
                    adminInvId,
                    authCmd.operationId,
                    PrivilegedCommandDigests.invitationAuthorization(adminInvId, authCmd),
                )
            val authorizeResp =
                client.post("/admin/accounts/invitation-authorization") {
                    bearerAuth(h.ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(authCmd.copy(proof = authProof)))
                }
            assertEquals(HttpStatusCode.OK, authorizeResp.status)
            val authorizedSummary = json.decodeFromString<ApiResponse<AdminUserSummary>>(authorizeResp.bodyAsText()).data
            assertEquals(setOf(UserRole.ADMIN), authorizedSummary.user.roles)
            assertEquals(AccountStatus.ACTIVE, authorizedSummary.user.accountStatus)
            assertEquals("UPTLAX", authorizedSummary.user.institutionId)
            assertEquals("ADM-UPTLAX-09", authorizedSummary.identityReference)
        }
}
