plugins {
    id("connectias.android.library")
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)

    // Testing dependencies (version catalog)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.mockk)
    api(libs.mockk.android)
    api(libs.turbine)

    // AndroidX Test
    api(libs.androidx.test.core)
    api(libs.androidx.junit)
    api(libs.androidx.arch.core.testing)
}
