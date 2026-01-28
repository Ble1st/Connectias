plugins {
    id("connectias.android.library")
}

dependencies {
    api(projects.core.model)
    
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
