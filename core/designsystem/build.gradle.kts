plugins {
    id("connectias.android.library")
    id("connectias.android.library.compose")
}

dependencies {
    api(projects.core.model)
    
    implementation("androidx.core:core-ktx:1.17.0")
}
