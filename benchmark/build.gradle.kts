plugins {
    id("com.android.test")
    // Temporarily disabled - Baseline Profile Plugin 1.4.1 not compatible with AGP 9.0
    // Error: Extension of type 'TestExtension' does not exist (replaced by TestExtensionImpl in AGP 9.0)
    // TODO: Re-enable when androidx.baselineprofile plugin version with AGP 9.0 support is available
    // alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.ble1st.connectias.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Temporarily disabled - Baseline Profile Plugin not compatible with AGP 9.0
// baselineProfile {
//     useConnectedDevices = true
// }

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId ?: "" }
        )
    }
}
