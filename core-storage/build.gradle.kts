plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ble1st.connectias.storage"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

hilt {
    enableAggregatingTask = false
}

dependencies {
    implementation(project(":core-api"))
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.security.crypto)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.javapoet)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)
}
