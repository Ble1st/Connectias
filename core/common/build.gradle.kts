plugins {
    id("connectias.android.library")
}

dependencies {
    api(projects.core.model)
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
