plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safe.args)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ble1st.connectias"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ble1st.connectias"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}



dependencies {
    // Core Modules (always included)
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":feature-security"))
    implementation(project(":feature-settings"))

    // Optional Modules (included based on gradle.properties)
    val featureDeviceInfoEnabled = project.findProperty("feature.device.info.enabled") == "true"
    if (featureDeviceInfoEnabled) {
        implementation(project(":feature-device-info"))
    }

    val featurePrivacyEnabled = project.findProperty("feature.privacy.enabled") == "true"
    if (featurePrivacyEnabled) {
        implementation(project(":feature-privacy"))
    }

    val featureNetworkEnabled = project.findProperty("feature.network.enabled") == "true"
    if (featureNetworkEnabled) {
        implementation(project(":feature-network"))
    }

    val featureNetworkAnalysisEnabled = project.findProperty("feature.network.analysis.enabled") == "true"
    if (featureNetworkAnalysisEnabled) {
        implementation(project(":feature-network-analysis"))
    }

    val featureNetworkTopologyEnabled = project.findProperty("feature.network.topology.enabled") == "true"
    if (featureNetworkTopologyEnabled) {
        implementation(project(":feature-network-topology"))
    }

    val featureUtilitiesEnabled = project.findProperty("feature.utilities.enabled") == "true"
    if (featureUtilitiesEnabled) {
        implementation(project(":feature-utilities"))
    }

    val featureBackupEnabled = project.findProperty("feature.backup.enabled") == "true"
    if (featureBackupEnabled) {
        
    }

    val featureWasmEnabled = project.findProperty("feature.wasm.enabled") == "true"
    if (featureWasmEnabled) {
        implementation(project(":feature-wasm"))
    }

    val featureUsbEnabled = project.findProperty("feature.usb.enabled") == "true"
    if (featureUsbEnabled) {
        implementation(project(":feature-usb"))
        implementation(project(":feature-dvd"))
    }

    val featureReportingEnabled = project.findProperty("feature.reporting.enabled") == "true"
    if (featureReportingEnabled) {
        implementation(project(":feature-reporting"))
    }

    val featureVulnerabilityScannerEnabled = project.findProperty("feature.vulnerability.scanner.enabled") == "true"
    if (featureVulnerabilityScannerEnabled) {
        implementation(project(":feature-vulnerability-scanner"))
    }

    val featureSecureNotesEnabled = project.findProperty("feature.secure.notes.enabled") == "true"
    if (featureSecureNotesEnabled) {
        implementation(project(":feature-secure-notes"))
    }

    val featureHardwareEnabled = project.findProperty("feature.hardware.enabled") == "true"
    if (featureHardwareEnabled) {
        implementation(project(":feature-hardware"))
    }

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
    // Explicitly add JavaPoet 1.13.0 for Hilt compatibility
    implementation(libs.javapoet)
    
    // Kotlin Reflect (required by KSP)
    implementation(libs.kotlin.reflect)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Fragment
    implementation(libs.androidx.fragment.ktx)

    // Logging
    // Note: Timber is used directly in app module (ConnectiasApplication, MainActivity)
    // so explicit dependency is needed even though :core also provides it
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}