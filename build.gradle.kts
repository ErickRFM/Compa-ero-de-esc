// Root build file for the Compañero de Escuela monorepo.
//
// This project is a convention-free, explicitly configured multi-module Gradle
// build. There is no `allprojects`/`subprojects` block on purpose: every module
// declares only the plugins it actually needs, which keeps the build fast and
// makes dependencies obvious.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktor) apply false
}

// Deletes every module's build directory, not just the root one.
//
// BUG-005: this used to delete only rootProject's build directory, so
// `gradlew clean build` left all eleven module build directories intact and
// Gradle reported most tasks as up-to-date. A clean build reported as a clean
// build was not one, which is the same failure as BUG-003 in a different place:
// the exit code was fine and the evidence behind it was not.
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
    subprojects.forEach { delete(it.layout.buildDirectory) }
}
