package org.companerodeescuela

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.cancel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.CachedAcademicLoad
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.classroom.AcademicGroupRepository
import org.companerodeescuela.feature.classroom.ClassroomRepository
import org.companerodeescuela.feature.classroom.ClassroomScreen
import org.companerodeescuela.feature.classroom.ClassroomViewModel
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.InstitutionalAcademicSection
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic HTTP transport, real Android repositories/ViewModel/Compose. No live project mutation. */
class InstitutionalClassesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun studentSeesOfficialSubjectTeacherAndGroup() = catalog(UserRole.STUDENT, "student")

    @Test
    fun teacherSeesOfficialAssignmentsInManagementCompatibleScreen() = catalog(UserRole.TEACHER, "teacher")

    private fun catalog(
        role: UserRole,
        name: String,
    ) {
        val f = Fixture(role)
        try {
            show(f, role)
            compose.waitUntil(30000) {
                val state = f.model.state.value
                !state.institutionalLoading && !state.loading
            }
            compose.onNodeWithText("Materia oficial QA").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Docente oficial QA").assertIsDisplayed()
            compose.onNodeWithText("QA_SUB · Grupo QA_GROUP").assertIsDisplayed()
            if (role == UserRole.TEACHER) {
                compose.onNodeWithText("No tienes espacios colaborativos asignados").assertExists()
                compose.onNodeWithText("Todavía no tienes clases asignadas").assertDoesNotExist()
            } else {
                compose.onNodeWithText("No tienes espacios colaborativos vinculados a tu grupo.").assertExists()
                compose.onNodeWithText("Control escolar todavía no ha vinculado clases a tu grupo.").assertDoesNotExist()
            }
            capture("v19-institutional-$name")
        } finally {
            compose.runOnIdle { f.close() }
        }
    }

    @Test
    fun forbiddenRefreshRemovesPreviousOfficialCatalogAndShowsAuthorityError() {
        val f = Fixture(UserRole.STUDENT)
        try {
            show(f, UserRole.STUDENT)
            compose.waitUntil(30000) { !f.model.state.value.institutionalLoading }
            compose.onNodeWithText("Materia oficial QA").performScrollTo().assertIsDisplayed()
            f.status.set(403)
            compose.runOnIdle { f.model.refresh() }
            compose.waitUntil(30000) { !f.model.state.value.institutionalLoading && f.model.state.value.institutionalErrorMessage != null }
            assertTrue(compose.onAllNodesWithTag("institutional_sections").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithText("Datos institucionales sin actualizar").performScrollTo().assertIsDisplayed()
            capture("v19-institutional-revoked")
        } finally {
            compose.runOnIdle { f.close() }
        }
    }

    @Test
    fun emptyAuthorizedCatalogHasExplicitEmptyState() {
        val f = Fixture(UserRole.STUDENT, empty = true)
        try {
            show(f, UserRole.STUDENT)
            compose.waitUntil(30000) { !f.model.state.value.institutionalLoading }
            compose.onNodeWithText("No hay inscripciones o asignaciones institucionales vigentes.").performScrollTo().assertIsDisplayed()
            capture("v19-institutional-empty")
        } finally {
            compose.runOnIdle { f.close() }
        }
    }

    private fun show(
        f: Fixture,
        role: UserRole,
    ) {
        compose.setContent {
            AuthTestLocale {
                CompaneroTheme {
                    ClassroomScreen(
                        setOf(role),
                        teacherExperience = role == UserRole.TEACHER,
                        teacherUserId = "qa-user",
                        viewModel = f.model,
                    )
                }
            }
        }
    }

    private fun capture(name: String) {
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val output = File(compose.activity.filesDir, "v10-evidence").apply { mkdirs() }
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private class Fixture(
        role: UserRole,
        empty: Boolean = false,
    ) {
        val status = AtomicInteger(200)
        private val sections =
            if (empty) {
                emptyList()
            } else {
                listOf(
                    InstitutionalAcademicSection(
                        "qa-section",
                        "QA_GROUP",
                        "qa-subject",
                        "QA_SUB",
                        "Materia oficial QA",
                        listOf("Docente oficial QA"),
                        "UTC",
                        7,
                        "2026-10-25T12:00:00Z",
                        emptyList(),
                    ),
                )
            }
        private val load =
            AcademicLoadResponse(
                AcademicProfile("qa-user", "Synthetic QA"),
                AcademicScheduleResponse("qa-user", emptyList()),
                sections,
                "SIII",
            )
        private val client =
            createApiClient(
                ApiEnvironment("https://synthetic-qa.test/", "instrumented-fixture"),
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/academic/load" ->
                            respond(
                                if (status.get() ==
                                    200
                                ) {
                                    Json.encodeToString(ApiResponse(load))
                                } else {
                                    "{}"
                                },
                                HttpStatusCode.fromValue(status.get()),
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        "/classrooms", "/academic/groups" ->
                            respond(
                                """{"data":[]}""",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        else -> error("Unexpected fixture endpoint")
                    }
                },
            )
        private val store =
            object : SessionTokenStore {
                private var token: String? =
                    "e30." +
                        Base64.getUrlEncoder().withoutPadding().encodeToString(
                            """{"sub":"qa-user","roles":["${role.name}"],"session_id":"qa-session","exp":4102444800}""".toByteArray(),
                        ) +
                        ".test"

                override suspend fun readAccessToken() = token

                override suspend fun writeAccessToken(token: String) {
                    this.token = token
                }

                override suspend fun clear() {
                    token = null
                }
            }
        private val cache =
            object : AcademicSnapshotCache {
                private var current: AcademicLoadResponse? = null

                override suspend fun read(ownerId: String) = current?.let { CachedAcademicLoad(it, 1) }

                override suspend fun write(value: AcademicLoadResponse) {
                    current = value
                }

                override suspend fun clear(ownerId: String) {
                    current = null
                }
            }
        val model =
            ClassroomViewModel(
                ClassroomRepository(client, store),
                AcademicGroupRepository(client, store),
                AcademicRepository(client, store, cache),
            )

        fun close() {
            model.viewModelScope.cancel()
            client.close()
        }
    }
}
