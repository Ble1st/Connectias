plugins {
    id("connectias.android.library")
    id("connectias.android.hilt")
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    
    implementation(libs.kotlinx.coroutines.android)
}
