# Consumer ProGuard rules for :plugin module
# These rules are automatically applied to apps that depend on this library

# AIDL Interfaces - must be kept for IPC communication
-keep interface com.ble1st.connectias.plugin.IPluginSandbox { *; }
-keep interface com.ble1st.connectias.hardware.IHardwareBridge { *; }
-keep interface com.ble1st.connectias.plugin.IFileSystemBridge { *; }
-keep interface com.ble1st.connectias.plugin.IPermissionCallback { *; }
-keep interface com.ble1st.connectias.plugin.messaging.** { *; }
-keep interface com.ble1st.connectias.plugin.ui.** { *; }
-keep class **.*$Stub { *; }
-keep class **.*$Stub$Proxy { *; }

# Plugin Services - must be kept for Android manifest references
-keep class com.ble1st.connectias.core.plugin.PluginSandboxService { *; }
-keep class com.ble1st.connectias.core.plugin.ui.PluginUIService { *; }
-keep class com.ble1st.connectias.hardware.HardwareBridgeService { *; }
-keep class com.ble1st.connectias.core.plugin.FileSystemBridgeService { *; }
-keep class com.ble1st.connectias.plugin.messaging.PluginMessagingService { *; }

# Parcelable classes
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Hilt/Dagger generated classes
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
