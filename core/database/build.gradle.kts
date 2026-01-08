plugins {
    id("connectias.android.library")
    id("connectias.android.room")
}

dependencies {
    api(projects.core.model)
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    
    // SQLCipher for encrypted database
    implementation("net.zetetic:sqlcipher-android:4.12.0")
}
