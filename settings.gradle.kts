// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Connectias"

// Enable type-safe project accessors (e.g., projects.core.data instead of project(":core:data"))
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ============================================================================
// Core Modules (always included)
// ============================================================================
include(":app")
include(":plugin")
include(":plugin-sdk")
include(":common")
include(":core")
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:ui")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:data")
include(":core:domain")
include(":core:testing")
include(":feature-settings")
include(":benchmark")

// ============================================================================ 
// Optional Modules (included based on gradle.properties)
// ============================================================================ 
// Note: All optional feature modules have been migrated to the plugin system.
// Features are now loaded dynamically via plugin packages.
// The following conditional includes are kept for reference but disabled:

// val featureDvdEnabled = providers.gradleProperty("feature.dvd.enabled").orNull == "true"
// if (featureDvdEnabled) {
//     include(":feature-dvd")
// }

// All other feature modules have been removed and migrated to plugins.

// ============================================================================
// Test Plugin (eigenständiges APK für Plugin-System)
// ============================================================================
// Note: The test plugin has been replaced by the integrated :plugin-sdk module.
