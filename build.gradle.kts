// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // JavaPoet is now managed via version catalog in subprojects
        // No need for classpath dependency here
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Note: Jacoco is a core Gradle plugin, no need to declare it here
}

// ============================================================================
// Common configuration for all sub-projects
// ============================================================================
// Note: Repositories are defined in settings.gradle.kts via dependencyResolutionManagement
// Do not add repositories here when RepositoriesMode.FAIL_ON_PROJECT_REPOS is set

subprojects {
    val libs = rootProject.the<org.gradle.accessors.dm.LibrariesForLibs>()
    configurations.all {
        resolutionStrategy.eachDependency {
            // Force Kotlin stdlib version to match project Kotlin version
            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
                useVersion(libs.versions.kotlin.get())
                because("Kotlin stdlib version must match Kotlin compiler version")
            }
            // Force kotlinx-serialization to compatible version
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization")) {
                useVersion(libs.versions.kotlinxSerialization.get())
                because("kotlinx-serialization version must be compatible with Kotlin ${libs.versions.kotlin.get()}")
            }
            // Force JavaPoet version compatible with Hilt
            // canonicalName() method was added in JavaPoet 1.13.0
            if (requested.group == "com.squareup" && requested.name == "javapoet") {
                useVersion(libs.versions.javapoet.get())
                because("Hilt requires JavaPoet 1.13.0")
            }
        }
    }
}