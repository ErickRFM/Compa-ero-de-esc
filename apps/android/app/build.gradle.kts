plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val debugApiBaseUrl = providers
    .environmentVariable("COMPANERO_API_BASE_URL")
    .orElse("https://compa-ero-de-esc.onrender.com/")
    .get()

val releaseApiBaseUrl = providers
    .environmentVariable("COMPANERO_RELEASE_API_BASE_URL")
    .orElse("https://invalid.invalid/")
    .get()

fun validateApiBaseUrl(name: String, value: String, requireHttps: Boolean = false) {
    require(value.startsWith(if (requireHttps) "https://" else "http")) {
        name + " must be an absolute " + (if (requireHttps) "HTTPS" else "HTTP(S)") + " URL"
    }
    require(value.endsWith("/")) {
        name + " must end with '/'"
    }
}

validateApiBaseUrl("COMPANERO_API_BASE_URL", debugApiBaseUrl)
validateApiBaseUrl("COMPANERO_RELEASE_API_BASE_URL", releaseApiBaseUrl, requireHttps = true)

android {
    namespace = "org.companerodeescuela"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.companerodeescuela"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            isMinifyEnabled = true
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(project(":shared:contracts"))
    implementation(project(":apps:android:core:academic"))
    implementation(project(":apps:android:core:attendance"))
    implementation(project(":apps:android:core:common"))
    implementation(project(":apps:android:core:database"))
    implementation(project(":apps:android:core:designsystem"))
    implementation(project(":apps:android:core:motion"))
    implementation(project(":apps:android:core:ui"))
    implementation(project(":apps:android:core:navigation"))
    implementation(project(":apps:android:core:network"))
    implementation(project(":apps:android:core:security"))
    implementation(project(":apps:android:feature:auth"))
    implementation(project(":apps:android:feature:home"))
    implementation(project(":apps:android:feature:schedule"))
    implementation(project(":apps:android:feature:attendance"))
    implementation(project(":apps:android:feature:channel"))
    implementation(project(":apps:android:feature:classroom"))
    implementation(project(":apps:android:feature:profile"))
    implementation(project(":apps:android:feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(project(":apps:android:core:testing"))

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
