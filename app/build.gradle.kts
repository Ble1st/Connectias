plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safe.args)
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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
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

    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Explicitly add JavaPoet 1.13.0 for Hilt compatibility
    implementation(libs.javapoet)

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