plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.companerodeescuela.core.navigation"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    api(composeBom)
    api(libs.androidx.navigation.compose)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(project(":apps:android:core:designsystem"))
    implementation(project(":apps:android:core:ui"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
}
