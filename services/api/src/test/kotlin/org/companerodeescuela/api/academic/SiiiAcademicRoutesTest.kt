package org.companerodeescuela.api.academic

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.auth.RefreshSession
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicAuthority
import org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicService
import org.companerodeescuela.api.integrations.academic.siii.SiiiAuthorityManifest
import org.companerodeescuela.api.integrations.academic.siii.SiiiAuthoritySection
import org.companerodeescuela.api.integrations.academic.siii.SiiiEnrollment
import org.companerodeescuela.api.integrations.academic.siii.SiiiHttpAcademicAuthority
import org.companerodeescuela.api.integrations.academic.siii.SiiiOccurrence
import org.companerodeescuela.api.integrations.academic.siii.SiiiRoom
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionReader
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionSnapshot
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionSnapshotRepository
import org.companerodeescuela.api.integrations.academic.siii.SiiiSubject
import org.companerodeescuela.api.integrations.academic.siii.SiiiTeacher
import org.companerodeescuela.api.integrations.academic.siii.SiiiTerm
import org.companerodeescuela.api.integrations.academic.siii.StoredSiiiSection
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.integrations.push.InMemoryInstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLink
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.InstitutionalAcademicSection
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import org.companerodeescuela.shared.contracts.ScheduleRecurrence
import org.companerodeescuela.shared.contracts.ScheduleSource
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SiiiAcademicRoutesTest {
    @Test
    fun `institutional section endpoint requires authentication`() =
        testApplication {
            val settings = TestFixtures.settings().copy(jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray())
            application {
                configurePlugins(
                    settings,
                    InMemoryRefreshSessionRepository(),
                    PlatformSessionAuthority(InMemoryPlatformAccountRepository(), MockIdentityProvider()),
                )
                routing { academicRoutes(settings, MockAcademicProvider()) }
            }
            assertEquals(HttpStatusCode.Unauthorized, client.get("/academic/institutional/sections").status)
        }

    @Test
    fun `HTTP academic reads enforce native role identity tenant and section scope`() =
        testApplication {
            val f = Fixture().also { it.initialize() }
            application {
                configurePlugins(f.settings, f.sessions, PlatformSessionAuthority(f.accounts, MockIdentityProvider()))
                routing { academicRoutes(f.settings, MockAcademicProvider(), institutionalAcademic = f.service) }
            }

            suspend fun read(
                path: String,
                who: String = "student",
            ) = client.get(path) {
                header(HttpHeaders.Authorization, "Bearer ${f.tokens.getValue(who)}")
            }
            val response = read("/academic/institutional/sections")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            val sections = Json.decodeFromString<ApiResponse<List<InstitutionalAcademicSection>>>(response.bodyAsText()).data
            assertEquals("Official subject", sections.single().subjectName)
            assertEquals(listOf("Official teacher"), sections.single().teacherNames)
            assertFalse(response.bodyAsText().contains("studentExternalId"))
            val load = Json.decodeFromString<ApiResponse<AcademicLoadResponse>>(read("/academic/load").bodyAsText()).data
            assertEquals("student", load.student.id)
            assertEquals("SIII", load.institutionalSource)
            assertEquals(
                ScheduleSource.INSTITUTIONAL,
                load.schedule.entries
                    .single()
                    .source,
            )
            assertEquals(
                ScheduleRecurrence.ONE_TIME,
                load.schedule.entries
                    .single()
                    .recurrence,
            )
            assertEquals(
                "2026-10-22",
                load.schedule.entries
                    .single()
                    .effectiveDate,
            )
            assertEquals(HttpStatusCode.OK, read("/academic/institutional/sections", "teacher").status)
            assertEquals(HttpStatusCode.Forbidden, read("/academic/institutional/sections", "admin").status)
            assertEquals(HttpStatusCode.NotFound, read("/academic/institutional/sections", "unlinked").status)
            assertEquals(
                HttpStatusCode.Forbidden,
                read("/academic/institutional/sections?institutionId=tenant-a&externalId=STUDENT", "outsider").status,
            )
            assertEquals(HttpStatusCode.NotFound, read("/academic/institutional/sections/${UUID.randomUUID()}").status)
            f.sections = emptyList()
            val empty =
                Json
                    .decodeFromString<ApiResponse<List<InstitutionalAcademicSection>>>(
                        read("/academic/institutional/sections").bodyAsText(),
                    ).data
            assertEquals(emptyList(), empty)
            f.revoked = true
            assertEquals(HttpStatusCode.Forbidden, read("/academic/load").status)
        }

    @Test
    fun `HTTP reads reject stale snapshots enrollment withdrawal and teacher revocation`() =
        testApplication {
            val f = Fixture().also { it.initialize() }
            application {
                configurePlugins(f.settings, f.sessions, PlatformSessionAuthority(f.accounts, MockIdentityProvider()))
                routing { academicRoutes(f.settings, MockAcademicProvider(), institutionalAcademic = f.service) }
            }

            suspend fun read(who: String = "student") =
                client.get("/academic/load") {
                    header(HttpHeaders.Authorization, "Bearer ${f.tokens.getValue(who)}")
                }
            f.sections = listOf(SiiiAuthoritySection(f.sectionId, 2))
            f.stored = f.stored.copy(snapshot = f.snapshot.copy(enrollments = emptyList(), teachers = emptyList()))
            assertEquals(HttpStatusCode.ServiceUnavailable, read().status)
            assertEquals(HttpStatusCode.ServiceUnavailable, read("teacher").status)
            f.stored =
                f.stored.copy(
                    sourceVersion = 2,
                    snapshot = f.snapshot.copy(enrollments = f.snapshot.enrollments.map { it.copy(status = "withdrawn") }),
                )
            assertEquals(HttpStatusCode.Forbidden, read().status)
            f.stored = f.stored.copy(snapshot = f.snapshot.copy(teachers = emptyList()))
            assertEquals(HttpStatusCode.Forbidden, read("teacher").status)
            f.missing = true
            assertEquals(HttpStatusCode.ServiceUnavailable, read().status)
        }

    @Test
    fun `authority targets require explicit HTTPS source and credential references`() {
        assertEquals(emptyMap(), SiiiHttpAcademicAuthority.targetsFromJson(null))
        val source = UUID.randomUUID().toString()
        val valid = """{"tenant-a":{"origin":"https://source.example.test","sourceInstitutionId":"$source","keyId":"qa-key"}}"""
        assertEquals(source, SiiiHttpAcademicAuthority.targetsFromJson(valid).getValue("tenant-a").sourceInstitutionId)
        for (origin in listOf(
            "http://127.0.0.1:3107",
            "https://source.example.test/path",
            "https://user@source.example.test",
            "https://source.example.test?x=1",
        )) {
            assertFailsWith<IllegalArgumentException> {
                SiiiHttpAcademicAuthority.targetsFromJson(valid.replace("https://source.example.test", origin))
            }
        }
    }

    @Test
    fun `dual roles resolve valid teacher identity and combine both links without duplicate sections`() =
        testApplication {
            val f = Fixture(dualTeacher = true).also { it.initialize() }
            application {
                configurePlugins(f.settings, f.sessions, PlatformSessionAuthority(f.accounts, MockIdentityProvider()))
                routing { academicRoutes(f.settings, MockAcademicProvider(), institutionalAcademic = f.service) }
            }

            suspend fun read() =
                client.get("/academic/load") { header(HttpHeaders.Authorization, "Bearer ${f.tokens.getValue("teacher")}") }
            assertEquals(HttpStatusCode.OK, read().status)
            f.links.createIfAbsent(
                InstitutionalIdentityLink(
                    "tenant-a",
                    InstitutionalIdentityKind.STUDENT,
                    "STUDENT",
                    "teacher",
                    "qa-admin",
                    "dual-student-link",
                    "a".repeat(64),
                    f.now,
                ),
            )
            val load = Json.decodeFromString<ApiResponse<AcademicLoadResponse>>(read().bodyAsText()).data
            assertEquals(1, load.institutionalSections.size)
            assertEquals(1, load.schedule.entries.size)
        }

    private class Fixture(
        private val dualTeacher: Boolean = false,
    ) {
        val settings = TestFixtures.settings().copy(jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray())
        val accounts = InMemoryPlatformAccountRepository()
        val sessions = InMemoryRefreshSessionRepository()
        val links = InMemoryInstitutionalIdentityLinkRepository()
        val directory =
            InMemoryInstitutionRepository(
                listOf(InstitutionSummary("tenant-a", "Institution A"), InstitutionSummary("tenant-b", "Institution B")),
            )
        val sectionId = UUID.randomUUID().toString()
        val now = Instant.now()
        val snapshot =
            SiiiSectionSnapshot(
                "siii.academic_section_snapshot.v1",
                sectionId,
                "GROUP_A",
                "open",
                "UTC",
                SiiiTerm(UUID.randomUUID().toString(), "TERM_A", "2026-10-19", "2026-10-25"),
                SiiiSubject(UUID.randomUUID().toString(), "SUBJECT_A", "Official subject", 5),
                listOf(SiiiTeacher(UUID.randomUUID().toString(), "TEACHER", "Official teacher")),
                listOf(
                    SiiiEnrollment(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "STUDENT",
                        "Private student",
                        "enrolled",
                        "active",
                    ),
                ),
                listOf(
                    SiiiOccurrence(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "2026-10-22",
                        "2026-10-22T09:00:00Z",
                        "2026-10-22T10:00:00Z",
                        "SCHEDULED",
                        SiiiRoom(UUID.randomUUID().toString(), "ROOM_A", "Building A", UUID.randomUUID().toString(), "Campus A"),
                    ),
                ),
            )
        var stored = StoredSiiiSection("tenant-a", sectionId, 1, UUID.randomUUID().toString(), "a".repeat(64), snapshot, now)
        var sections = listOf(SiiiAuthoritySection(sectionId, 1))
        var missing = false
        var revoked = false
        val tokens = mutableMapOf<String, String>()
        private val repository =
            object : SiiiSectionSnapshotRepository {
                override suspend fun find(
                    institutionId: String,
                    sectionId: String,
                ) = stored.takeIf {
                    !missing &&
                        it.institutionId == institutionId &&
                        it.sectionId == sectionId
                }

                override suspend fun apply(value: StoredSiiiSection) {
                    stored = value
                }
            }
        private val identities =
            InstitutionalIdentityLinkService(
                accounts,
                directory,
                links,
                MfaService(
                    accounts,
                    InMemoryMfaRepository(),
                    MfaSecretCipher(
                        ByteArray(
                            32,
                        ) {
                            1
                        },
                    ),
                ),
            )
        val service =
            SiiiAcademicService(
                accounts,
                links,
                SiiiSectionReader(accounts, directory, identities, repository),
                SiiiAcademicAuthority { institution, kind, external ->
                    if (revoked || institution != "tenant-a") throw ApiException.Forbidden()
                    SiiiAuthorityManifest(
                        "siii.academic_authority.v1",
                        institution,
                        kind.name.lowercase(),
                        external,
                        Instant.now().toString(),
                        sections,
                    )
                },
                setOf("tenant-a", "tenant-b"),
            )

        suspend fun initialize() {
            for ((id, role) in listOf(
                "student" to UserRole.STUDENT,
                "teacher" to UserRole.TEACHER,
                "admin" to UserRole.ADMIN,
                "unlinked" to UserRole.STUDENT,
                "outsider" to UserRole.STUDENT,
            )) {
                val account =
                    PlatformAccount(
                        id,
                        "QA $id",
                        "$id@example.test",
                        "fixture-hash",
                        if (dualTeacher && id == "teacher") setOf(UserRole.STUDENT, UserRole.TEACHER) else setOf(role),
                        institutionId =
                            if (id ==
                                "outsider"
                            ) {
                                "tenant-b"
                            } else {
                                "tenant-a"
                            },
                        emailVerifiedAt = now.minusSeconds(1),
                    )
                accounts.create(account)
                val user = account.toUserSummary()
                sessions.create(
                    RefreshSession(
                        "session-$id",
                        "fixture-hash",
                        user,
                        now,
                        now.plusSeconds(3600),
                        identitySource = SessionIdentitySource.NATIVE,
                    ),
                )
                tokens[id] = AuthTokenService(settings).issue(user, "session-$id").value
            }
            for ((kind, external, native) in listOf(
                Triple(InstitutionalIdentityKind.STUDENT, "STUDENT", "student"),
                Triple(InstitutionalIdentityKind.TEACHER, "TEACHER", "teacher"),
            )) {
                if (dualTeacher && kind == InstitutionalIdentityKind.STUDENT) continue
                links.createIfAbsent(
                    InstitutionalIdentityLink("tenant-a", kind, external, native, "qa-admin", "link-$native", "a".repeat(64), now),
                )
            }
            links.createIfAbsent(
                InstitutionalIdentityLink(
                    "tenant-b",
                    InstitutionalIdentityKind.STUDENT,
                    "STUDENT",
                    "outsider",
                    "qa-admin",
                    "link-outsider",
                    "a".repeat(64),
                    now,
                ),
            )
        }
    }
}
