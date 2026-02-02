plugins {
    id("connectias.android.library")
    id("connectias.android.library.compose")
}

dependencies {
    api(projects.core.model)
    api(projects.core.designsystem)
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
