// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ble1st.connectias.service"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        consumerProguardFiles("consumer-rules.pro")
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

    buildFeatures {
        buildConfig = true
        aidl = true  // Enable AIDL for Service IPC
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

hilt {
    enableAggregatingTask = false
}

dependencies {
    // Core Module
    implementation(project(":core"))
    
    // Android Core
    implementation(libs.androidx.core.ktx)
    
    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.javapoet)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // SQLCipher for encrypted database
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Logging
    implementation(libs.timber)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
