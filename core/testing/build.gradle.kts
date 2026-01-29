plugins {
    id("connectias.android.library")
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    
    // Testing dependencies
    api("junit:junit:4.13.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    api("io.mockk:mockk:1.14.9")
    api("io.mockk:mockk-android:1.14.9")
    api("app.cash.turbine:turbine:1.2.0")
    
    // AndroidX Test
    api("androidx.test:core:1.7.0")
    api("androidx.test.ext:junit:1.3.0")
    api("androidx.arch.core:core-testing:2.2.0")
}
