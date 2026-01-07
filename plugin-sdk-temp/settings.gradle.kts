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

rootProject.name = "ConnectiasPlugins"

// ============================================================================
// Plugin SDK (always included - base for all plugins)
// ============================================================================
include(":connectias-plugin-sdk")

// ============================================================================
// Plugin Modules (migrated from feature-* modules)
// ============================================================================
include(":connectias-plugin-barcode")
include(":connectias-plugin-bluetooth")
include(":connectias-plugin-calendar")
include(":connectias-plugin-deviceinfo")
include(":connectias-plugin-dnstools")
include(":connectias-plugin-dvd")
include(":connectias-plugin-network")
include(":connectias-plugin-ntp")
include(":connectias-plugin-password")
include(":connectias-plugin-scanner")
include(":connectias-plugin-secure-notes")
