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

// ============================================================================
// Optional Modules (included based on gradle.properties)
// ============================================================================
val featureDeviceInfoEnabled = providers.gradleProperty("feature.device.info.enabled").orNull == "true"
if (featureDeviceInfoEnabled) {
    include(":feature-device-info")
}

val featureNetworkEnabled = providers.gradleProperty("feature.network.enabled").orNull == "true"
if (featureNetworkEnabled) {
    include(":feature-network")
}

val featureUtilitiesEnabled = providers.gradleProperty("feature.utilities.enabled").orNull == "true"
if (featureUtilitiesEnabled) {
    include(":feature-utilities")
}

// Feature backup module not yet implemented
// val featureBackupEnabled = providers.gradleProperty("feature.backup.enabled").orNull == "true"
// if (featureBackupEnabled) {
//     include(":feature-backup")
// }

val featurePrivacyEnabled = providers.gradleProperty("feature.privacy.enabled").orNull == "true"
if (featurePrivacyEnabled) {
    include(":feature-privacy")
}

val featureWasmEnabled = providers.gradleProperty("feature.wasm.enabled").orNull == "true"
if (featureWasmEnabled) {
    include(":feature-wasm")
}

val featureNetworkAnalysisEnabled = providers.gradleProperty("feature.network.analysis.enabled").orNull == "true"
if (featureNetworkAnalysisEnabled) {
    include(":feature-network-analysis")
}

val featureNetworkTopologyEnabled = providers.gradleProperty("feature.network.topology.enabled").orNull == "true"
if (featureNetworkTopologyEnabled) {
    include(":feature-network-topology")
}

val featureUsbEnabled = providers.gradleProperty("feature.usb.enabled").orNull == "true"
if (featureUsbEnabled) {
    include(":feature-usb")
}

val featureReportingEnabled = providers.gradleProperty("feature.reporting.enabled").orNull == "true"
if (featureReportingEnabled) {
    include(":feature-reporting")
}

val featureVulnerabilityScannerEnabled = providers.gradleProperty("feature.vulnerability.scanner.enabled").orNull == "true"
if (featureVulnerabilityScannerEnabled) {
    include(":feature-vulnerability-scanner")
}

val featureSecureNotesEnabled = providers.gradleProperty("feature.secure.notes.enabled").orNull == "true"
if (featureSecureNotesEnabled) {
    include(":feature-secure-notes")
}

val featureHardwareEnabled = providers.gradleProperty("feature.hardware.enabled").orNull == "true"
if (featureHardwareEnabled) {
    include(":feature-hardware")
}

include(":feature-dvd")