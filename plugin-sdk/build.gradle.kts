plugins {
    alias(libs.plugins.android.library)
    // Note: kotlin.android plugin removed - using built-in Kotlin support in AGP 9.0+
    // Note: kotlin-parcelize is built-in with AGP 9.0, must use id() without version
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.ble1st.connectias.plugin"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // kotlin-parcelize-runtime required for @Parcelize annotation support
    implementation(libs.kotlin.parcelize.runtime)
}

