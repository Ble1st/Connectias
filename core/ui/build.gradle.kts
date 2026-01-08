plugins {
    id("connectias.android.library")
    id("connectias.android.library.compose")
}

dependencies {
    api(projects.core.model)
    api(projects.core.designsystem)
    
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
}
