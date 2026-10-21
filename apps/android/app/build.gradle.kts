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
    .orElse(debugApiBaseUrl)
    .get()
 
// Debug-signed internal test builds remain explicit. Only an opt-in
// distribution build may use a persistent private signing identity.
val signingMode = providers.environmentVariable("COMPANERO_SIGNING_MODE").orElse("internal").get()
require(signingMode in setOf("internal", "distribution")) {
    "COMPANERO_SIGNING_MODE must be internal or distribution"
}
val distributionStorePath = providers.environmentVariable("COMPANERO_RELEASE_KEYSTORE_PATH").orNull
val distributionStorePassword = providers.environmentVariable("COMPANERO_RELEASE_STORE_PASSWORD").orNull
val distributionKeyAlias = providers.environmentVariable("COMPANERO_RELEASE_KEY_ALIAS").orNull
val distributionKeyPassword = providers.environmentVariable("COMPANERO_RELEASE_KEY_PASSWORD").orNull
val signingInputs =
    listOf(distributionStorePath, distributionStorePassword, distributionKeyAlias, distributionKeyPassword)
require(
    signingInputs.all { it.isNullOrBlank() } ||
        (signingMode == "distribution" && signingInputs.all { !it.isNullOrBlank() }),
) {
    "Distribution signing requires all four environment variables; never supply partial signing secrets"
}
if (signingMode == "distribution") {
    require(signingInputs.all { !it.isNullOrBlank() }) {
        "Production distribution cannot use the Android debug certificate"
    }
    require(file(distributionStorePath!!).isFile) {
        "Distribution signing keystore file does not exist"
    }
}
val appVersionCode = providers.environmentVariable("COMPANERO_VERSION_CODE").orElse("1").get().toInt()
require(appVersionCode > 0) { "COMPANERO_VERSION_CODE must be positive" }
val appVersionName = providers.environmentVariable("COMPANERO_VERSION_NAME").orElse("0.1.0").get()
require(appVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-zA-Z0-9.-]+)?"))) {
    "COMPANERO_VERSION_NAME must be a valid version name"
}


fun validateApiBaseUrl(name: String, value: String, requireHttps: Boolean = false) {
    require(value.startsWith(if (requireHttps) "https://" else "http")) {
        name + " must be an absolute " + (if (requireHttps) "HTTPS" else "HTTP(S)") + " URL"
    }
    require(value.endsWith("/")) {
        name + " must end with '/'"
    }
}

// Firebase options are public project identifiers, never service-account credentials.
// All four fields must be supplied as a set or FCM stays completely disabled.
val firebaseAppId = providers.environmentVariable("COMPANERO_FIREBASE_APP_ID").orElse("").get()
val firebaseProjectId = providers.environmentVariable("COMPANERO_FIREBASE_PROJECT_ID").orElse("").get()
val firebaseSenderId = providers.environmentVariable("COMPANERO_FIREBASE_SENDER_ID").orElse("").get()
val firebaseApiKey = providers.environmentVariable("COMPANERO_FIREBASE_API_KEY").orElse("").get()
val firebaseValues = listOf(firebaseAppId, firebaseProjectId, firebaseSenderId, firebaseApiKey)
val firebaseConfigured = firebaseValues.all(String::isNotBlank)
require(firebaseValues.all(String::isBlank) || firebaseConfigured) {
    "Firebase Android requires all four public project settings"
}
if (firebaseConfigured) {
    require(firebaseAppId.matches(Regex("1:[0-9]{4,25}:android:[A-Za-z0-9]+")) &&
        firebaseProjectId.matches(Regex("[a-z][a-z0-9-]{3,99}")) &&
        firebaseSenderId.matches(Regex("[0-9]{4,25}")) &&
        firebaseApiKey.matches(Regex("[A-Za-z0-9_-]{20,100}"))) {
        "Invalid public Firebase Android project settings"
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
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$firebaseSenderId\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    signingConfigs {
        if (signingMode == "distribution") {
            create("distribution") {
                storeFile = file(distributionStorePath!!)
                storePassword = distributionStorePassword
                keyAlias = distributionKeyAlias
                keyPassword = distributionKeyPassword
            }
        }
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
            signingConfig =
                if (signingMode == "distribution") {
                    signingConfigs.getByName("distribution")
                } else {
                    // Only for labeled internal test artifacts. Never publish to app stores.
                    signingConfigs.getByName("debug")
                }
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
    implementation(project(":apps:android:feature:grading"))
    implementation(project(":apps:android:feature:tutoring"))

    implementation("com.google.firebase:firebase-messaging:24.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
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

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(platform(libs.ktor.bom))
    androidTestImplementation(libs.ktor.client.mock)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
