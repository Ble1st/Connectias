plugins {
    id("connectias.android.library")
    id("connectias.android.library.compose")
}

dependencies {
    api(projects.core.model)
    
    implementation(libs.androidx.core.ktx)
}
