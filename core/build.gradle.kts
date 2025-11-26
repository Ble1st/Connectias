plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.ble1st.connectias.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    }
}

dependencies {
    // Common Module
    implementation(project(":common"))

    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Room Database + SQLCipher
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation("net.zetetic:sqlcipher-android:4.11.0")
    ksp(libs.androidx.room.compiler)

    // Security
    implementation(libs.security.crypto)
    
    // Root detection heuristics (non-security boundary - client-side only)
    // NOTE: RootBeer is a heuristic detection tool with known limitations and vulnerabilities.
    // This is NOT a security boundary. For production, consider:
    // - Google Play Integrity API or SafetyNet attestation with server-side verification
    // - Combining multiple signals server-side rather than relying on client-only heuristics
    // - Regular security audits and dependency monitoring (Dependabot/Snyk)
    // Version pinned to 0.1.1 - verify security posture before upgrading
    implementation(libs.rootbeer)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Explicitly add JavaPoet 1.13.0 for Hilt compatibility
    implementation(libs.javapoet)

    // Logging
    implementation(libs.timber)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

