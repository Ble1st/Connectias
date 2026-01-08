# Plugin ProGuard Rules

# Keep plugin class and methods
-keep class com.ble1st.connectias.testplugin.TestPlugin {
    public *;
}

# Keep Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep Fragment
-keep class androidx.fragment.app.Fragment { *; }
