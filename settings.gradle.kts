pluginManagement {
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

// ============================================================================
// Core Modules (always included)
// ============================================================================ 
include(":app")
include(":common")
include(":core")
include(":feature-settings")

// ============================================================================ 
// Optional Modules (included based on gradle.properties)
// ============================================================================ 
val featureDvdEnabled = providers.gradleProperty("feature.dvd.enabled").orNull == "true"
if (featureDvdEnabled) {
    include(":feature-dvd")
}

val featureSecureNotesEnabled = providers.gradleProperty("feature.secure.notes.enabled").orNull == "true"
if (featureSecureNotesEnabled) {
    include(":feature-secure-notes")
}

val featureUsbEnabled = providers.gradleProperty("feature.usb.enabled").orNull == "true"
if (featureUsbEnabled) {
    include(":feature-usb")
}

val featureDocumentEnabled = providers.gradleProperty("feature.document.enabled").orNull == "true"
if (featureDocumentEnabled) {
    include(":feature-document")
}

