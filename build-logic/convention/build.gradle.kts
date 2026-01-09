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
    // Use direct versions instead of version catalog in build-logic
    compileOnly("com.android.tools.build:gradle:8.13.2")
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
