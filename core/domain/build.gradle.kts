plugins {
    id("connectias.android.library")
    id("connectias.android.hilt")
    id("connectias.jacoco")
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.data)
    
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    
    // Testing
    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
