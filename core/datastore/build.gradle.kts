plugins {
    id("connectias.android.library")
}

dependencies {
    api(projects.core.model)
    
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
