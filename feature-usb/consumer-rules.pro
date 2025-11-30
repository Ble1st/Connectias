# ProGuard rules for USB Feature Module

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI classes
-keep class com.ble1st.connectias.feature.usb.native.** { *; }

# Keep models for Parcelable
-keep class com.ble1st.connectias.feature.usb.models.** implements android.os.Parcelable { *; }

# Keep Hilt modules
-keep class com.ble1st.connectias.feature.usb.di.** { *; }

# Keep providers
-keep class com.ble1st.connectias.feature.usb.provider.** { *; }
-keep class com.ble1st.connectias.feature.usb.storage.** { *; }
-keep class com.ble1st.connectias.feature.usb.media.** { *; }
-keep class com.ble1st.connectias.feature.usb.permission.** { *; }
-keep class com.ble1st.connectias.feature.usb.detection.** { *; }
-keep class com.ble1st.connectias.feature.usb.settings.** { *; }

# Keep UI components
-keep class com.ble1st.connectias.feature.usb.ui.** { *; }

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
