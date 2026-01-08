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
// include(":test-plugin") // Deaktiviert - nur für lokale Tests

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

val featureBluetoothEnabled = providers.gradleProperty("feature.bluetooth.enabled").orNull == "true"
if (featureBluetoothEnabled) {
    include(":feature-bluetooth")
}

val featureNetworkEnabled = providers.gradleProperty("feature.network.enabled").orNull == "true"
if (featureNetworkEnabled) {
    include(":feature-network")
}

val featureDnsToolsEnabled = providers.gradleProperty("feature.dnstools.enabled").orNull == "true"
if (featureDnsToolsEnabled) {
    include(":feature-dnstools")
}

val featureBarcodeEnabled = providers.gradleProperty("feature.barcode.enabled").orNull == "true"
if (featureBarcodeEnabled) {
    include(":feature-barcode")
}

val featureCalendarEnabled = providers.gradleProperty("feature.calendar.enabled").orNull == "true"
if (featureCalendarEnabled) {
    include(":feature-calendar")
}

val featureNtpEnabled = providers.gradleProperty("feature.ntp.enabled").orNull == "true"
if (featureNtpEnabled) {
    include(":feature-ntp")
}

val featureSshEnabled = providers.gradleProperty("feature.ssh.enabled").orNull == "true"
if (featureSshEnabled) {
    include(":feature-ssh")
}

val featurePasswordEnabled = providers.gradleProperty("feature.password.enabled").orNull == "true"
if (featurePasswordEnabled) {
    include(":feature-password")
}

val featureDeviceInfoEnabled = providers.gradleProperty("feature.device.info.enabled").orNull == "true"
if (featureDeviceInfoEnabled) {
    include(":feature-deviceinfo")
}

val featureScannerEnabled = providers.gradleProperty("feature.scanner.enabled").orNull == "true"
if (featureScannerEnabled) {
    include(":feature-scanner")
}

val featureGpsEnabled = providers.gradleProperty("feature.gps.enabled").orNull == "true"
if (featureGpsEnabled) {
    include(":feature-satellite")
}

// ============================================================================
// Test Plugin (eigenständiges APK für Plugin-System)
// ============================================================================
include(":test-plugin")
