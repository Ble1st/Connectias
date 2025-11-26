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

val featureNetworkEnabled = providers.gradleProperty("feature.network.enabled").orNull == "true"
if (featureNetworkEnabled) {
    include(":feature-network")
}

val featureUtilitiesEnabled = providers.gradleProperty("feature.utilities.enabled").orNull == "true"
if (featureUtilitiesEnabled) {
    include(":feature-utilities")
}

val featureBackupEnabled = providers.gradleProperty("feature.backup.enabled").orNull == "true"
if (featureBackupEnabled) {
    include(":feature-backup")
}

val featurePrivacyEnabled = providers.gradleProperty("feature.privacy.enabled").orNull == "true"
if (featurePrivacyEnabled) {
    include(":feature-privacy")
}