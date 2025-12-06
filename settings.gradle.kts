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
include(":feature-security")
include(":feature-settings")

// ============================================================================ 
// Optional Modules (included based on gradle.properties)
// ============================================================================ 
val featureDeviceInfoEnabled = providers.gradleProperty("feature.device.info.enabled").orNull == "true"
if (featureDeviceInfoEnabled) {
    include(":feature-device-info")
}

val featureDvdEnabled = providers.gradleProperty("feature.dvd.enabled").orNull == "true"
if (featureDvdEnabled) {
    include(":feature-dvd")
}

val featureNetworkEnabled = providers.gradleProperty("feature.network.enabled").orNull == "true"
if (featureNetworkEnabled) {
    include(":feature-network")
}

val featureReportingEnabled = providers.gradleProperty("feature.reporting.enabled").orNull == "true"
if (featureReportingEnabled) {
    include(":feature-reporting")
}

val featureSecureNotesEnabled = providers.gradleProperty("feature.secure.notes.enabled").orNull == "true"
if (featureSecureNotesEnabled) {
    include(":feature-secure-notes")
}

val featureUsbEnabled = providers.gradleProperty("feature.usb.enabled").orNull == "true"
if (featureUsbEnabled) {
    include(":feature-usb")
}

val featureWasmEnabled = providers.gradleProperty("feature.wasm.enabled").orNull == "true"
if (featureWasmEnabled) {
    include(":feature-wasm")
}
