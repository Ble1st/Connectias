plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safe.args)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
    id("jacoco")
}

android {
    namespace = "com.ble1st.connectias"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ble1st.connectias"
        minSdk = 33
        targetSdk = 36
        // P1: Support version injection from CI/CD (fallback to defaults)
        versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
        versionName = project.findProperty("versionName") as? String ?: "1.0"

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
            
            // Phase 8: R8 Full Mode für maximale Optimierung
            // https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#r8-full-mode
            // Aktiviert aggressive Optimierungen wie Vertical Class Merging
            // WICHTIG: Kann Breaking Changes verursachen - gründlich testen!
            isMinifyEnabled = true
            
            // Benchmark Mode für Baseline Profiles
            // Deaktiviert während normaler Builds, aktiviert für Profiling
            // signingConfig = signingConfigs.getByName("debug") // Für Benchmarking
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            
            // Phase 8: Debug-spezifische Optimierungen
            // Schnellere Builds durch weniger Optimierung
            isDebuggable = true
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
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a") // Only build for arm64-v8a
            isUniversalApk = false // Do not build a universal APK
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    composeCompiler {
        includeSourceInformation = false
    }
    packaging {
        jniLibs {
            pickFirsts += listOf("**/libc++_shared.so")
        }
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

// Hilt configuration - disable aggregating task to avoid JavaPoet compatibility issues
hilt {
    enableAggregatingTask = false
}

// Disable Compose mapping file generation to avoid "Unsupported class file major version 69" error
// which occurs because the task's internal ASM library doesn't support Java 25 yet.
afterEvaluate {
    tasks.configureEach {
        if (name.contains("ComposeMapping", ignoreCase = true)) {
            enabled = false
        }
    }
}

// Jacoco configuration for code coverage
jacoco {
    toolVersion = "0.8.11"
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("test")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        $$"**/*$ViewInjector*.*",
        "**/*Dagger*.*",
        "**/*MembersInjector*.*",
        "**/*_Factory*.*",
        "**/*_Provide*Factory*.*",
        "**/*Extensions*.*",
        "**/com/google/**/*.*",
        "**/com/ble1st/connectias/databinding/**/*.*",
        "**/com/ble1st/connectias/generated/**/*.*",
        "**/dagger/**/*.*",
        "**/hilt/**/*.*"
    )
    
    // Support both debug and release build types for coverage
    // Try release first (for CI/CD), fallback to debug (for local development)
    val releaseTree = fileTree(layout.buildDirectory.dir("intermediates/javac/release")) {
        exclude(fileFilter)
    }
    val debugTree = fileTree(layout.buildDirectory.dir("intermediates/javac/debug")) {
        exclude(fileFilter)
    }
    
    // Use release if available, otherwise fallback to debug
    val classDirectoriesTree = if (releaseTree.files.isNotEmpty()) {
        releaseTree
    } else {
        debugTree
    }
    
    val mainSrc = "${project.projectDir}/src/main/java"
    
    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(classDirectoriesTree))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("**/*.exec", "**/*.ec")
    })
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")
    
    violationRules {
        rule {
            limit {
                minimum = "0.50".toBigDecimal() // Minimum 50% coverage
            }
        }
        
        rule {
            element = "CLASS"
            excludes = listOf(
                "*.BuildConfig",
                "*.R",
                "*.R$*",
                "*.Manifest*",
                "*.BR",
                "*.BR$*",
                "*.DataBinder*",
                "*.DataBinding*",
                "*.ViewInjector*",
                "*.Dagger*",
                "*.MembersInjector*",
                "*.Factory*",
                "*.Extensions*",
                "*.Companion*",
                "*.Kt*"
            )
            
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.30".toBigDecimal() // Minimum 30% branch coverage
            }
        }
    }
}

dependencies {
    // Core Modules (always included)
    // Note: Still using :core alongside new submodules during migration
    implementation(project(":core"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":common"))
    implementation(project(":feature-settings"))
    
    // Optional Modules (included based on gradle.properties)
    // Note: feature.backup was removed as it was not implemented

    val featureDvdEnabled = project.findProperty("feature.dvd.enabled") == "true"
    if (featureDvdEnabled) {
        implementation(project(":feature-dvd"))
    }

    val featureSecureNotesEnabled = project.findProperty("feature.secure.notes.enabled") == "true"
    if (featureSecureNotesEnabled) {
        implementation(project(":feature-secure-notes"))
    }

    val featureBluetoothEnabled = project.findProperty("feature.bluetooth.enabled") == "true"
    if (featureBluetoothEnabled) {
        implementation(project(":feature-bluetooth"))
    }

    val featureNetworkEnabled = project.findProperty("feature.network.enabled") == "true"
    if (featureNetworkEnabled) {
        implementation(project(":feature-network"))
    }

    val featureDnsToolsEnabled = project.findProperty("feature.dnstools.enabled") == "true"
    if (featureDnsToolsEnabled) {
        implementation(project(":feature-dnstools"))
    }

    val featureBarcodeEnabled = project.findProperty("feature.barcode.enabled") == "true"
    if (featureBarcodeEnabled) {
        implementation(project(":feature-barcode"))
    }

    val featureScannerEnabled = providers.gradleProperty("feature.scanner.enabled").orNull == "true"
    if (featureScannerEnabled) {
        implementation(project(":feature-scanner"))
    }


    val featureCalendarEnabled = project.findProperty("feature.calendar.enabled") == "true"
    if (featureCalendarEnabled) {
        implementation(project(":feature-calendar"))
    }

    val featureNtpEnabled = project.findProperty("feature.ntp.enabled") == "true"
    if (featureNtpEnabled) {
        implementation(project(":feature-ntp"))
    }

    val featureSshEnabled = project.findProperty("feature.ssh.enabled") == "true"
    if (featureSshEnabled) {
        implementation(project(":feature-ssh"))
    }

    val featurePasswordEnabled = project.findProperty("feature.password.enabled") == "true"
    if (featurePasswordEnabled) {
        implementation(project(":feature-password"))
    }

    val featureDeviceInfoEnabled = project.findProperty("feature.device.info.enabled") == "true"
    if (featureDeviceInfoEnabled) {
        implementation(project(":feature-deviceinfo"))
    }

    val featureGpsEnabled = project.findProperty("feature.gps.enabled") == "true"
    if (featureGpsEnabled) {
        implementation(project(":feature-satellite"))
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
    
    // Hilt WorkManager (required for @HiltWorker)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.work.compiler)
    
    // OkHttp (required for SSL Pinning in core module)
    implementation(libs.okhttp)
    
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

    // Phase 8: Performance & Monitoring
    // LeakCanary - Memory Leak Detection (nur Debug)
    debugImplementation(libs.leakcanary)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}