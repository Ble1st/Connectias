plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.connectias.connectias"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    signingConfigs {
        create("release") {
            // Keystore-Pfad (relativ zum android/app/ Verzeichnis)
            storeFile = file("../keystore/connectias-release-key.jks")
            
            // SICHERHEIT: Erfordere Umgebungsvariablen - keine Hardcoded Passwords
            val storePasswordEnv = System.getenv("CONNECTIAS_KEYSTORE_PASSWORD")
            val keyPasswordEnv = System.getenv("CONNECTIAS_KEY_PASSWORD")
            
            if (storePasswordEnv.isNullOrBlank()) {
                throw GradleException(
                    "CONNECTIAS_KEYSTORE_PASSWORD environment variable must be set for release builds. " +
                    "Never use hardcoded passwords in production."
                )
            }
            if (keyPasswordEnv.isNullOrBlank()) {
                throw GradleException(
                    "CONNECTIAS_KEY_PASSWORD environment variable must be set for release builds. " +
                    "Never use hardcoded passwords in production."
                )
            }
            
            storePassword = storePasswordEnv
            keyAlias = "connectias-release"
            keyPassword = keyPasswordEnv
        }
    }

    defaultConfig {
        // Application ID für Connectias Plugin-Plattform
        applicationId = "com.connectias.connectias"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            // Release-Signing mit eigenem Keystore
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("com.scottyab:rootbeer-lib:0.1.1")
}
