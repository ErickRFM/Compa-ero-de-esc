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

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
