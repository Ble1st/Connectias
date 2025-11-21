// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// ============================================================================
// Common configuration for all sub-projects
// ============================================================================
// Note: Repositories are defined in settings.gradle.kts via dependencyResolutionManagement
// Do not add repositories here when RepositoriesMode.FAIL_ON_PROJECT_REPOS is set