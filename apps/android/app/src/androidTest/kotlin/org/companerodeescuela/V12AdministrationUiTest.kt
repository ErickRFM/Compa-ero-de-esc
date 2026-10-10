package org.companerodeescuela

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import java.io.File
import java.time.Clock
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.database.*
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.core.network.*
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.admin.*
import org.companerodeescuela.feature.auth.*
import org.companerodeescuela.feature.classroom.*
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.shared.contracts.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Compose/ViewModels/repositories with an explicitly test-only HTTP transport.
 * Crypto, live authority, tenant and Mongo correctness are separately exercised by actual Ktor tests.
 * Capture only safe completed UI; enrollment/password/OTP/proof are never captured.
 */
class V12AdministrationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private class Store(role:UserRole):SessionTokenStore {
        val token=MutableStateFlow<String?>("e30."+Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"qa-operator","session_id":"qa-session","roles":["${role.name}"],"exp":4102444800}""".toByteArray())+".test")
        override suspend fun readAccessToken()=token.value
        override suspend fun writeAccessToken(token:String) { this.token.value=token }
        override suspend fun clear() { token.value=null }
        override fun observeAccessToken():Flow<String?> = token
    }
    private class Cache:AcademicSnapshotCache {
        override suspend fun read(ownerId:String):CachedAcademicLoad?=null
        override suspend fun write(value:AcademicLoadResponse) {}
        override suspend fun clear(ownerId:String) {}
    }
    private class Fixture(role:UserRole=UserRole.ADMIN) {
        val store=Store(role);val now=Clock.systemUTC().instant().epochSecond
        val user=UserSummary("qa-operator","QA HTTP fixture",roles=setOf(role),institutionId="qa-campus",emailVerified=true,authRevision=7)
        val group=AcademicGroupSummary("QA-GROUP","QA Group",institutionId="qa-campus",periodId="qa-period",startsAtEpochSeconds=now-3600,endsAtEpochSeconds=now+7*86400,scopeRevision=4)
        val application=StaffApplication("qa-application",StaffRole.TUTOR,"qa-campus","TEST-REFERENCE",requestedAtEpochSeconds=now)
        val target=AdminUserSummary(UserSummary("qa-target","QA Tutor request",roles=setOf(UserRole.TEACHER),institutionId="qa-campus",emailVerified=true,authRevision=3),listOf(application))
        val student=UserSummary("qa-student","QA Student",roles=setOf(UserRole.STUDENT),institutionId="qa-campus",emailVerified=true)
        val proofs=CopyOnWriteArrayList<MfaStepUpRequest>();val approvals=CopyOnWriteArrayList<ApproveStaffRequest>();val tutorRequests=CopyOnWriteArrayList<TutorApplicationRequest>();val memberships=CopyOnWriteArrayList<AssignAcademicGroupMemberRequest>()
        var deny=false;var enabled=false;var approved=false;var submitted=false
        val client=createApiClient(ApiEnvironment("https://qa-transport.test/","instrumented-test-fixture"),MockEngine { request ->
            assertNotNull(request.headers[HttpHeaders.Authorization])
            val body=when(request.url.encodedPath) {
                "/auth/me" -> Json.encodeToString(ApiResponse(user))
                "/auth/mfa/status" -> Json.encodeToString(ApiResponse(MfaStatus(enabled,true)))
                "/auth/mfa/enrollment" -> {
                    val payload=Json.decodeFromString<MfaEnrollmentRequest>((request.body as TextContent).text);assertEquals("test-only-password",payload.password)
                    Json.encodeToString(ApiResponse(MfaEnrollment("qa-enrollment","TESTONLYSETUP","otpauth://totp/QA",now+600)))
                }
                "/auth/mfa/confirmation" -> {
                    val payload=Json.decodeFromString<MfaConfirmationRequest>((request.body as TextContent).text);assertEquals("qa-enrollment",payload.enrollmentId);assertEquals("123456",payload.otp);enabled=true
                    Json.encodeToString(ApiResponse(MfaStatus(true,true)))
                }
                "/auth/mfa/step-up" -> {
                    proofs+=Json.decodeFromString<MfaStepUpRequest>((request.body as TextContent).text)
                    Json.encodeToString(ApiResponse(StepUpProof("TESTONLY-PROOF",now+300)))
                }
                "/admin/accounts" -> Json.encodeToString(ApiResponse(AdministrationOverview(listOf(if(approved) target.copy(applications=listOf(application.copy(status=StaffApplicationStatus.APPROVED))) else target),AdministrationMetrics(1,if(approved) 0 else 1,1,if(approved) 1 else 0,0))))
                "/admin/accounts/approval-groups","/academic/groups" -> Json.encodeToString(ApiResponse(listOf(group)))
                "/admin/accounts/academic-directory" -> Json.encodeToString(ApiResponse(listOf(student,target.user)))
                "/tutoring/assignments" -> Json.encodeToString(ApiResponse(emptyList<TutorAssignmentSummary>()))
                "/admin/accounts/qa-target/staff-approval" -> { approvals+=Json.decodeFromString<ApproveStaffRequest>((request.body as TextContent).text);approved=true;Json.encodeToString(ApiResponse(target.copy(applications=listOf(application.copy(status=StaffApplicationStatus.APPROVED))))) }
                "/auth/staff-applications" -> if(request.method==HttpMethod.Get) Json.encodeToString(ApiResponse(if(submitted) listOf(application) else emptyList())) else {
                    tutorRequests+=Json.decodeFromString<TutorApplicationRequest>((request.body as TextContent).text);submitted=true;Json.encodeToString(ApiResponse(application))
                }
                "/institutions" -> Json.encodeToString(ApiResponse(listOf(InstitutionSummary("qa-campus","QA Campus"))))
                "/classrooms" -> Json.encodeToString(ApiResponse(emptyList<ClassroomSummary>()))
                "/academic/groups/QA-GROUP/members" -> { memberships+=Json.decodeFromString<AssignAcademicGroupMemberRequest>((request.body as TextContent).text);Json.encodeToString(ApiResponse(AcademicGroupMembership("QA-GROUP","QA Group","qa-student",now))) }
                else -> error("Unexpected fixture endpoint ${request.url.encodedPath}")
            }
            respond(if(deny) "{}" else body,if(deny) HttpStatusCode.Forbidden else HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        val mfaRepository=MfaRepository(client,store)
        val mfa=MfaViewModel(mfaRepository)
        val admin=AdminViewModel(AdminRepository(client,store),mfaRepository,Clock.systemUTC())
        val staff=StaffApplicationViewModel(AuthRepository(client,store),mfaRepository)
        val academic=ClassroomAdministrationViewModel(ClassroomRepository(client,store),AcademicGroupRepository(client,store),AdminRepository(client,store),AcademicRepository(client,store,Cache()),mfaRepository,Clock.systemUTC())
        fun close() { listOf(mfa,admin,staff,academic).forEach { it.viewModelScope.cancel() };client.close() }
    }
    private fun waitTag(tag:String) { compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun credentials() {
        compose.onNodeWithTag("admin_password").performTextInput("test-only-password")
        compose.onNodeWithTag("admin_otp").performTextInput("123456")
        compose.onNodeWithTag("admin_confirm").performClick()
    }
    @Test fun profileNavigatesToRealMfaRepositoryAndConfirmationClearsSetupMaterial() {
        val f=Fixture();val original=f.store.token.value
        try {
            compose.setContent { AuthTestLocale { CompaneroTheme {
                var destination by remember { mutableStateOf<Destination>(Destination.Profile) }
                if(destination==Destination.Mfa) MfaScreen(onBack={ destination=Destination.Profile },viewModel=f.mfa)
                else ProfileScreen(f.user.displayName,f.user.roles,onAppearance={},onLogout={},onSecurity={ destination=Destination.Mfa })
            } } }
            compose.onNodeWithText("Autenticación en dos pasos").performScrollTo().performClick()
            waitTag("mfa_password")
            compose.runOnIdle { assertTrue("Localized MFA screen must block screenshots",compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
            compose.onNodeWithTag("mfa_password").performScrollTo().performTextInput("test-only-password")
            compose.onNodeWithTag("mfa_enroll").performScrollTo().performClick();waitTag("mfa_setup_secret")
            compose.onNodeWithTag("mfa_otp").performScrollTo().performTextInput("123456")
            compose.onNodeWithTag("mfa_confirm").performScrollTo().performClick();waitTag("mfa_enabled")
            compose.onNodeWithTag("mfa_setup_secret").assertDoesNotExist();assertEquals(original,f.store.token.value)
            compose.onNodeWithTag("security_back").performScrollTo().performClick()
            compose.runOnIdle { assertFalse(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        } finally { compose.runOnIdle { f.close() } }
    }
    @Test fun administrativeTutorApprovalSelectsActualGroupAndUsesBoundProof() {
        val f=Fixture()
        try {
            compose.setContent { AuthTestLocale { CompaneroTheme { AdminHomeScreen({}, {}, {}, {},viewModel=f.admin) } } }
            waitTag("admin_metrics")
            compose.onNodeWithTag("admin_approve_qa-application").performScrollTo().performClick()
            compose.onNodeWithTag("admin_group_QA-GROUP").performClick()
            compose.onNodeWithTag("admin_validity_days").performTextInput("2")
            compose.onNodeWithTag("admin_groups_confirm").performClick()
            credentials();waitTag("admin_done")
            compose.waitUntil(10000) { !f.admin.state.value.loading }
            val request=f.approvals.single();val proof=f.proofs.single()
            assertEquals("TESTONLY-PROOF",request.proof);assertEquals(4,request.tutorScopes.single().groupScopeRevision)
            assertEquals(PrivilegedCommandDigests.staffApproval("qa-target",request),proof.payloadDigest);assertEquals(request.operationId,proof.operationId)
            assertEquals(2*86400L,request.tutorScopes.single().endsAtEpochSeconds-request.tutorScopes.single().startsAtEpochSeconds)
            assertEquals(0,f.admin.state.value.overview?.metrics?.pendingStaff)
            compose.onNodeWithTag("admin_done").performScrollTo();capture("administration-confirmed-qa-http-fixture")
        } finally { compose.runOnIdle { f.close() } }
    }
    @Test fun teacherProfileTutorRequestIsPendingAndDoesNotGrantTutorLocally() {
        val f=Fixture(UserRole.TEACHER);val original=f.store.token.value
        try {
            compose.setContent { AuthTestLocale { CompaneroTheme {
                var destination by remember { mutableStateOf<Destination>(Destination.Profile) }
                if(destination==Destination.StaffApplication) StaffApplicationScreen(onBack={ destination=Destination.Profile },viewModel=f.staff)
                else ProfileScreen(f.user.displayName,f.user.roles,onAppearance={},onLogout={},onTutorRequest={ destination=Destination.StaffApplication })
            } } }
            compose.onNodeWithText("Solicitar acceso de tutor").performScrollTo().performClick();waitTag("staff_identity")
            compose.onNodeWithTag("staff_identity").performScrollTo().performTextInput("TEST-REFERENCE")
            compose.onNodeWithTag("staff_preference").performScrollTo().performTextInput("QA Group preference")
            compose.onNodeWithTag("staff_submit").performScrollTo().performClick();waitTag("staff_application_status")
            assertEquals(7,f.tutorRequests.single().expectedAuthRevision);assertEquals(setOf(UserRole.TEACHER),f.staff.state.value.user?.roles)
            assertEquals(StaffApplicationStatus.PENDING,f.staff.state.value.applications.single().status);assertEquals(original,f.store.token.value)
            capture("tutor-pending-qa-http-fixture")
        } finally { compose.runOnIdle { f.close() } }
    }
    @Test fun academicEditorLinksActualStudentWithCurrentGroupRevisionAndScopedMfa() {
        val f=Fixture()
        try {
            compose.setContent { AuthTestLocale { CompaneroTheme { ClassroomAdministrationEditor({},viewModel=f.academic) } } }
            compose.waitUntil(10000) { !f.academic.state.value.loading }
            compose.onNodeWithTag("academic_group").performScrollTo().performClick();compose.onNodeWithTag("academic_group_QA-GROUP").performClick()
            compose.onNodeWithTag("academic_student").performScrollTo().performClick();compose.onNodeWithTag("academic_student_qa-student").performClick()
            compose.onNodeWithTag("academic_member_prepare").performScrollTo().performClick();credentials();waitTag("academic_admin_done")
            val request=f.memberships.single();val proof=f.proofs.single()
            assertEquals(4,request.expectedScopeRevision);assertEquals("qa-student",request.userId)
            assertEquals(PrivilegedCommandDigests.groupMembership("QA-GROUP",request),proof.payloadDigest);assertEquals(StepUpAction.MANAGE_ACADEMICS,proof.action)
        } finally { compose.runOnIdle { f.close() } }
    }
    @Test fun forbiddenAdministrationHasNoFakeMetricSuccess() {
        val f=Fixture();f.deny=true
        try {
            compose.setContent { AuthTestLocale("en") { CompaneroTheme { AdminHomeScreen({}, {}, {}, {},viewModel=f.admin) } } }
            waitTag("admin_error");compose.onNodeWithTag("admin_metrics").assertDoesNotExist();assertNull(f.admin.state.value.overview)
        } finally { compose.runOnIdle { f.close() } }
    }
    private fun capture(name:String) {
        val context=compose.activity;val directory=File(context.filesDir,"v10-evidence").apply { mkdirs() }
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull("Safe completed screen must be capturable",bitmap)
        File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        val config=context.resources.configuration;val display=context.resources.displayMetrics
        File(File(context.filesDir,"v12-qa-metadata").apply { mkdirs() },"$name-geometry.txt").writeText("source=instrumented-HTTP-fixture\nscreenWidthDp=${config.screenWidthDp}\nscreenHeightDp=${config.screenHeightDp}\ndensityDpi=${display.densityDpi}\nbitmap=${bitmap.width}x${bitmap.height}\n")
        bitmap.recycle()
    }
}
