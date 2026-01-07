// Root build file for Connectias Plugins
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// ============================================================================
// Common configuration for all plugin sub-projects
// ============================================================================
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
            if (requested.group == "com.squareup" && requested.name == "javapoet") {
                useVersion(libs.versions.javapoet.get())
                because("Hilt requires JavaPoet 1.13.0")
            }
        }
    }
}
