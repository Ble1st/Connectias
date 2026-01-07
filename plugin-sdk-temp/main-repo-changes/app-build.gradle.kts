// This is the updated app/build.gradle.kts with feature modules removed
// All feature modules (except feature-settings) are now loaded as plugins at runtime

dependencies {
    // Core Modules (always included)
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":feature-settings"))
    
    // ============================================================================
    // Feature Modules Migration Notice
    // ============================================================================
    // All feature modules (except feature-settings) have been migrated to
    // a separate Plugin Repository and are loaded at runtime via PluginService.
    //
    // Plugins are discovered and loaded automatically from:
    // - Local storage: /data/data/com.ble1st.connectias/files/plugins/
    // - GitHub Releases: Ble1st/Connectias-Plugins
    //
    // See: core/src/main/kotlin/com/ble1st/connectias/core/plugin/PluginService.kt
    //
    // Removed dependencies (now loaded as plugins):
    // - implementation(project(":feature-barcode"))
    // - implementation(project(":feature-bluetooth"))
    // - implementation(project(":feature-calendar"))
    // - implementation(project(":feature-deviceinfo"))
    // - implementation(project(":feature-dnstools"))
    // - implementation(project(":feature-dvd"))
    // - implementation(project(":feature-network"))
    // - implementation(project(":feature-ntp"))
    // - implementation(project(":feature-password"))
    // - implementation(project(":feature-scanner"))
    // - implementation(project(":feature-secure-notes"))
    // ============================================================================

    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.activity)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.javapoet)
    
    // Hilt WorkManager
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.work.compiler)
    
    // OkHttp (required for SSL Pinning and Plugin Downloads)
    implementation(libs.okhttp)
    
    // Kotlin Reflect (required by KSP)
    implementation(libs.kotlin.reflect)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Fragment
    implementation(libs.androidx.fragment.ktx)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
