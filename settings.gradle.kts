rootProject.name = "companero-de-escuela"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// ---------------------------------------------------------------------------
// Shared Kotlin modules (JVM).
// Pure Kotlin: consumed by the Ktor API and safe to reuse elsewhere.
// ---------------------------------------------------------------------------
include(":shared:contracts")
include(":shared:models")
include(":shared:validation")

// ---------------------------------------------------------------------------
// Backend
// ---------------------------------------------------------------------------
include(":services:api")

// ---------------------------------------------------------------------------
// Android application
//
// Only modules with real content are included here. A module that Gradle
// knows about but nobody maintains is worse than a module that does not
// exist yet, because it looks finished in a code review.
//
// The target end state (core:database, core:datastore, core:security,
// core:location, core:notifications, and the feature modules) is documented
// in docs/architecture/module-structure.md. Each one is added at the point
// where it earns its first line of production code.
// ---------------------------------------------------------------------------
include(":apps:android:app")

include(":apps:android:core:common")
include(":apps:android:core:designsystem")
include(":apps:android:core:ui")
include(":apps:android:core:navigation")
include(":apps:android:core:network")
include(":apps:android:core:testing")
