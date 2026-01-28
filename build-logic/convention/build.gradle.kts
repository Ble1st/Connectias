plugins {
    `kotlin-dsl`
}

group = "com.ble1st.connectias.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Note: build-logic is a composite build that's evaluated before the main project's version catalog
    // Therefore, we use direct versions here, but they should match gradle/libs.versions.toml:
    // - AGP: agp = "9.0.0" → com.android.tools.build:gradle:9.0.0
    // - Kotlin: kotlin = "2.3.0" → org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0
    // - KSP: ksp = "2.3.4" → com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.4
    // - Room Plugin: roomGradlePlugin = "2.8.4" → androidx.room:room-gradle-plugin:2.8.4
    // When updating versions in libs.versions.toml, remember to update these as well!
    compileOnly("com.android.tools.build:gradle:9.0.0")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.4")
    implementation("androidx.room:room-gradle-plugin:2.8.4")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "connectias.android.application"
            implementationClass = "ConnectiasAndroidApplicationPlugin"
        }
        register("androidLibrary") {
            id = "connectias.android.library"
            implementationClass = "ConnectiasAndroidLibraryPlugin"
        }
        register("androidLibraryCompose") {
            id = "connectias.android.library.compose"
            implementationClass = "ConnectiasAndroidLibraryComposePlugin"
        }
        register("androidHilt") {
            id = "connectias.android.hilt"
            implementationClass = "ConnectiasAndroidHiltPlugin"
        }
        register("androidRoom") {
            id = "connectias.android.room"
            implementationClass = "ConnectiasAndroidRoomPlugin"
        }
        register("jvmLibrary") {
            id = "connectias.jvm.library"
            implementationClass = "ConnectiasJvmLibraryPlugin"
        }
        register("jacoco") {
            id = "connectias.jacoco"
            implementationClass = "ConnectiasJacocoPlugin"
        }
    }
}
