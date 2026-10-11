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
            // Do not expand this list without compiling the Apple targets.
            kotlin.srcDir("../../shared/contracts/src/main/kotlin")
            kotlin.include("org/companerodeescuela/mobile/**")
            listOf("AuthContracts", "ApiResponse", "ApiError", "UserSummary", "UserRole").forEach {
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
