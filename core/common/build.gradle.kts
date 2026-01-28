plugins {
    id("connectias.android.library")
}

dependencies {
    api(projects.core.model)
    
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
}
