# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Hilt/Dagger keep rules
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class * extends dagger.hilt.internal.GeneratedComponentManager
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

# AIDL keep rules
-keep interface com.ble1st.connectias.plugin.IPluginSandbox { *; }
-keep interface com.ble1st.connectias.hardware.IHardwareBridge { *; }
-keep interface com.ble1st.connectias.plugin.IFileSystemBridge { *; }
-keep interface com.ble1st.connectias.plugin.IPermissionCallback { *; }
-keep interface com.ble1st.connectias.plugin.messaging.** { *; }
-keep interface com.ble1st.connectias.plugin.ui.** { *; }
-keep class **.*$Stub { *; }
-keep class **.*$Stub$Proxy { *; }

# Parcelable keep rules
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements android.os.Parcelable {
  public <fields>;
  private <fields>;
}

# Plugin Services keep rules
-keep class com.ble1st.connectias.core.plugin.PluginSandboxService { *; }
-keep class com.ble1st.connectias.core.plugin.ui.PluginUIService { *; }
-keep class com.ble1st.connectias.hardware.HardwareBridgeService { *; }
-keep class com.ble1st.connectias.core.plugin.FileSystemBridgeService { *; }
-keep class com.ble1st.connectias.plugin.messaging.PluginMessagingService { *; }

# Plugin Manager keep rules
-keep class com.ble1st.connectias.plugin.PluginManagerSandbox { *; }
-keep class com.ble1st.connectias.plugin.PluginManager { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes
-keep,includedescriptorclasses class com.ble1st.connectias.plugin.**$$serializer { *; }
-keepclassmembers class com.ble1st.connectias.plugin.** {
    *** Companion;
}
-keepclasseswithmembers class com.ble1st.connectias.plugin.** {
    kotlinx.serialization.KSerializer serializer(...);
}
