package org.companerodeescuela.api.grading

import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.classroom.NativeClassroom
import org.companerodeescuela.api.classroom.NativeClassroomMembership
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.ClassroomMemberRole
import org.companerodeescuela.shared.contracts.ClassroomStatus
import org.companerodeescuela.shared.contracts.GradebookCategory
import org.companerodeescuela.shared.contracts.GradebookDraftRequest
import org.companerodeescuela.shared.contracts.GradebookRow
import org.companerodeescuela.shared.contracts.GradebookStatus
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GradebookServiceTest {
    @Test
    fun `teacher saves and publishes only valid current roster grades`() =
        runTest {
            val (service, teacher) = fixture()
            val draft = service.saveDraft(teacher.toUserSummary(), request())

            assertEquals(GradebookStatus.DRAFT, draft.status)
            assertEquals(1, draft.revision)
            assertEquals("student-a", draft.rows.single().studentId)
            assertEquals(GradebookStatus.PUBLISHED, service.publish(teacher.toUserSummary(), "class-a", "Parcial 1", 1).status)
        }

    @Test
    fun `teacher cannot save grades for a student outside the live roster`() =
        runTest {
            val (service, teacher) = fixture()

            assertFailsWith<ApiException.Forbidden> {
                service.saveDraft(teacher.toUserSummary(), request(studentId = "outsider"))
            }
        }

    @Test
    fun `invalid weighting and stale revisions are rejected`() =
        runTest {
            val (service, teacher) = fixture()
            assertFailsWith<ApiException.Validation> {
                service.saveDraft(teacher.toUserSummary(), request(weight = 80.0))
            }
            service.saveDraft(teacher.toUserSummary(), request())
            assertFailsWith<ApiException.Conflict> {
                service.saveDraft(teacher.toUserSummary(), request(revision = 2))
            }
        }

    @Test
    fun `published grades are not overwritten through draft save`() =
        runTest {
            val (service, teacher) = fixture()
            service.saveDraft(teacher.toUserSummary(), request())
            service.publish(teacher.toUserSummary(), "class-a", "Parcial 1", 1)

            assertFailsWith<ApiException.Conflict> {
                service.saveDraft(teacher.toUserSummary(), request(revision = 2))
            }
        }

    @Test
    fun `student cannot see drafts and receives only their own published row`() =
        runTest {
            val (service, teacher) = fixture()
            service.saveDraft(teacher.toUserSummary(), request())
            val student =
                PlatformAccount(
                    id = "student-a",
                    displayName = "Student",
                    email = "student@example.test",
                    passwordHash = "hash",
                    roles = setOf(UserRole.STUDENT),
                    institutionId = "campus-a",
                    emailVerifiedAt = Instant.parse("2026-10-13T12:00:00Z"),
                ).toUserSummary()

            assertEquals(null, service.studentView(student, "class-a", "Parcial 1"))
            service.publish(teacher.toUserSummary(), "class-a", "Parcial 1", 1)
            assertEquals(
                "student-a",
                service
                    .studentView(student, "class-a", "Parcial 1")
                    ?.rows
                    ?.single()
                    ?.studentId,
            )
        }

    @Test
    fun `tutor cannot use the student grade endpoint without student role`() =
        runTest {
            val (service, _) = fixture()
            val tutor =
                PlatformAccount(
                    id = "tutor-a",
                    displayName = "Tutor",
                    email = "tutor@example.test",
                    passwordHash = "hash",
                    roles = setOf(UserRole.TUTOR),
                    institutionId = "campus-a",
                    emailVerifiedAt = Instant.parse("2026-10-13T12:00:00Z"),
                ).toUserSummary()

            assertFailsWith<ApiException.Forbidden> {
                service.studentView(tutor, "class-a", "Parcial 1")
            }
        }

    private suspend fun fixture(): Pair<GradebookService, PlatformAccount> {
        val now = Instant.parse("2026-10-13T12:00:00Z")
        val accounts = InMemoryPlatformAccountRepository()
        val teacher =
            PlatformAccount(
                id = "teacher-a",
                displayName = "Teacher",
                email = "teacher@example.test",
                passwordHash = "hash",
                roles = setOf(UserRole.TEACHER),
                institutionId = "campus-a",
                emailVerifiedAt = now,
                roleGenerations = mapOf(UserRole.TEACHER to 1),
            )
        accounts.create(teacher)
        accounts.create(
            PlatformAccount(
                id = "student-a",
                displayName = "Student",
                email = "student@example.test",
                passwordHash = "hash",
                roles = setOf(UserRole.STUDENT),
                institutionId = "campus-a",
                emailVerifiedAt = now,
            ),
        )
        val groups = InMemoryAcademicGroupRepository()
        groups.create(
            AcademicGroupRecord(
                id = "group-a",
                name = "9A",
                active = true,
                createdAt = now,
                institutionId = "campus-a",
                periodId = "2026-2",
                startsAt = now.minusSeconds(60),
                endsAt = now.plusSeconds(3600),
            ),
        )
        val repository = InMemoryClassroomRepository()
        repository.createClassroom(
            NativeClassroom(
                id = "class-a",
                name = "Math",
                description = null,
                room = null,
                groupId = "group-a",
                groupName = "9A",
                teacherId = teacher.id,
                teacherDisplayName = teacher.displayName,
                status = ClassroomStatus.ACTIVE,
                createdAt = now,
                groupScopeRevision = 0,
                teacherRoleGeneration = 1,
            ),
        )
        repository.addMembershipIfAbsent(NativeClassroomMembership("class-a", "student-a", ClassroomMemberRole.STUDENT, now))
        val classroomService = ClassroomService(repository, groups, Clock.fixed(now, ZoneOffset.UTC), accounts = accounts)
        return GradebookService(classroomService, InMemoryGradebookRepository(), Clock.fixed(now, ZoneOffset.UTC)) to teacher
    }

    private fun request(
        studentId: String = "student-a",
        weight: Double = 100.0,
        revision: Long? = null,
    ) = GradebookDraftRequest(
        classroomId = "class-a",
        gradingPeriod = "Parcial 1",
        expectedRevision = revision,
        categories = listOf(GradebookCategory("exam", "Examen", weight)),
        rows = listOf(GradebookRow(studentId, mapOf("exam" to 9.0))),
    )
}
