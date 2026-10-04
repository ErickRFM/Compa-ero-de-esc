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
// ---------------------------------------------------------------------------
include(":shared:contracts")
include(":shared:models")
include(":shared:validation")

// ---------------------------------------------------------------------------
// Backend
// ---------------------------------------------------------------------------
include(":services:api")

// ---------------------------------------------------------------------------
// Android application.
// Only modules with real production responsibility are included.
// ---------------------------------------------------------------------------
include(":apps:android:app")
include(":apps:android:feature:auth")
include(":apps:android:feature:home")
include(":apps:android:feature:schedule")
include(":apps:android:feature:attendance")

include(":apps:android:core:academic")
include(":apps:android:core:attendance")
include(":apps:android:core:common")
include(":apps:android:core:database")
include(":apps:android:core:designsystem")
include(":apps:android:core:motion")
include(":apps:android:core:ui")
include(":apps:android:core:navigation")
include(":apps:android:core:network")
include(":apps:android:core:security")
include(":apps:android:core:testing")
