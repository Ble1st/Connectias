plugins {
    id("connectias.android.library")
    id("connectias.android.hilt")
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
