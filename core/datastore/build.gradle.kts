plugins {
    id("connectias.android.library")
}

dependencies {
    api(projects.core.model)
    
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
