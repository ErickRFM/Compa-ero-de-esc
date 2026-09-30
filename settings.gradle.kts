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
// ---------------------------------------------------------------------------
include(":apps:android:app")

include(":apps:android:core:common")
include(":apps:android:core:designsystem")
include(":apps:android:core:ui")
include(":apps:android:core:navigation")
include(":apps:android:core:network")
include(":apps:android:core:database")
include(":apps:android:core:datastore")
include(":apps:android:core:security")
include(":apps:android:core:testing")

include(":apps:android:feature:auth")
include(":apps:android:feature:home")
include(":apps:android:feature:attendance")
include(":apps:android:feature:scanner")
include(":apps:android:feature:schedule")
include(":apps:android:feature:subjects")
include(":apps:android:feature:announcements")
include(":apps:android:feature:calendar")
include(":apps:android:feature:tasks")
include(":apps:android:feature:library")
include(":apps:android:feature:events")
include(":apps:android:feature:campus")
include(":apps:android:feature:profile")
include(":apps:android:feature:settings")
