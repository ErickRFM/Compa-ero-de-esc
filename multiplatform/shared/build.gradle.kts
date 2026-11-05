plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(17)
    jvm()
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "CompaneroShared"
            isStatic = true
        }
    }
    sourceSets {
        commonMain {
            // These are the SAME source files compiled by Android and the JVM API.
            // Files that use java.time, java.security, or java.io.Serializable are
            // excluded from iOS targets and replaced by multiplatform adapters
            // under org/companerodeescuela/mobile/ (e.g. OperationalContracts.kt).
            kotlin.srcDir("../../shared/contracts/src/main/kotlin")
            kotlin.include("org/companerodeescuela/mobile/**")
            listOf(
                // Original 7 contracts — pure Kotlin, no java.time.
                "AuthContracts",
                "ApiResponse",
                "ApiError",
                "UserSummary",
                "UserRole",
                "AccountStatus",
                "RegistrationContracts",
                // Academic domain contracts — pure Kotlin serializable types.
                "AcademicContracts",
                "AcademicEventContracts",
                "AcademicGroupContracts",
                "AcademicProvenanceContracts",
                "AcademicReconciliationContracts",
                "AcademicScheduleContracts",
                "AcademicScheduleManagementContracts",
                "AcademicScheduleV2Contracts",
                "AcademicChangePushContract",
                // Attendance and presence contracts.
                "AttendanceContracts",
                "SchoolEntryQrContracts",
                "SchoolPresenceContracts",
                // Channel and classroom contracts.
                "ChannelContracts",
                "ClassroomContracts",
                // Coordination and tutoring contracts.
                "CoordinationOverview",
                "TutorCaseContracts",
                "TutoringContracts",
                // Grading and excuse contracts.
                "GradingContracts",
                "ExcuseContracts",
                // Group and representative contracts.
                "GroupRepresentativeContracts",
                "TeacherCampusRosterContracts",
                // Institution-related contracts.
                "InstitutionalAttendanceHistoryContracts",
                "InstitutionalIdentityLinkContracts",
                "InstitutionalPushContracts",
                // Account administration and privilege security.
                "AccountAdministrationContracts",
                "PrivilegeSecurityContracts",
                // Invitation contracts — needed for iOS invitation acceptance flow.
                "RegistrationInvitationContracts",
            ).forEach {
                kotlin.include("org/companerodeescuela/shared/contracts/$it.kt")
            }
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("io.ktor:ktor-client-core:3.3.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.3.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation("io.ktor:ktor-client-mock:3.3.3")
        }
        jvmMain.dependencies { implementation("io.ktor:ktor-client-cio:3.3.3") }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.3.3") }
    }
}
