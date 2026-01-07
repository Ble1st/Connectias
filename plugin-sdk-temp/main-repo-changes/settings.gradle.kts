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
// Feature Modules Migration Notice
// ============================================================================ 
// All feature modules (except feature-settings) have been migrated to
// a separate Plugin Repository: Connectias-Plugins
// 
// Plugins are now loaded at runtime via the PluginService in core.
// See: core/src/main/kotlin/com/ble1st/connectias/core/plugin/
//
// Migrated modules:
// - feature-barcode → connectias-plugin-barcode (in Connectias-Plugins repo)
// - feature-bluetooth → connectias-plugin-bluetooth
// - feature-calendar → connectias-plugin-calendar
// - feature-deviceinfo → connectias-plugin-deviceinfo
// - feature-dnstools → connectias-plugin-dnstools
// - feature-dvd → connectias-plugin-dvd
// - feature-network → connectias-plugin-network
// - feature-ntp → connectias-plugin-ntp
// - feature-password → connectias-plugin-password
// - feature-scanner → connectias-plugin-scanner
// - feature-secure-notes → connectias-plugin-secure-notes
//
// Old feature-* directories are kept as backup and can be removed after
// successful migration and testing.
