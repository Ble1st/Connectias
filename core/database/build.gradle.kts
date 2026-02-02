plugins {
    id("connectias.android.library")
    id("connectias.android.room")
}

dependencies {
    api(projects.core.model)
    
    implementation(libs.kotlinx.coroutines.android)
    
    // SQLCipher for encrypted database
    implementation(libs.sqlcipher.android)
}
