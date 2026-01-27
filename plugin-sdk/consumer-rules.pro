# Keep SDK public API surface if needed by consumers.
# Most classes are referenced directly by plugin code, so shrinking typically keeps them.

# ------------------------------------------------------------------------------
# Three-Process UI Architecture - Plugin SDK Rules
# ------------------------------------------------------------------------------
# These rules ensure plugin code can use UI-related classes after R8 obfuscation.

# Keep PluginUIController interface - Used by plugins to update UI
-keep interface com.ble1st.connectias.plugin.sdk.PluginUIController { *; }

# Keep IPlugin UI methods - Used by plugins for Three-Process UI
-keepclassmembers interface com.ble1st.connectias.plugin.sdk.IPlugin {
    *** onRenderUI(...);
    *** onUserAction(...);
    *** onUILifecycle(...);
    *** getUIController(...);
}

# Keep PluginUIBuilder - DSL for building UI state
-keep class com.ble1st.connectias.plugin.ui.PluginUIBuilder { *; }
-keepclassmembers class com.ble1st.connectias.plugin.ui.PluginUIBuilder {
    *;
}

# Keep buildPluginUI helper function
-keepclassmembers class com.ble1st.connectias.plugin.ui.PluginUIBuilderKt {
    static *** buildPluginUI(...);
}

# Keep AIDL Parcelables - Used for IPC communication
-keep class com.ble1st.connectias.plugin.ui.UIStateParcel { *; }
-keep class com.ble1st.connectias.plugin.ui.UIComponentParcel { *; }
-keep class com.ble1st.connectias.plugin.ui.UserActionParcel { *; }
-keep class com.ble1st.connectias.plugin.ui.UIEventParcel { *; }
-keep class com.ble1st.connectias.plugin.ui.MotionEventParcel { *; }

# Keep Parcelable CREATOR for AIDL Parcelables
-keepclassmembers class com.ble1st.connectias.plugin.ui.UIStateParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class com.ble1st.connectias.plugin.ui.UIComponentParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class com.ble1st.connectias.plugin.ui.UserActionParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class com.ble1st.connectias.plugin.ui.UIEventParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class com.ble1st.connectias.plugin.ui.MotionEventParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep AIDL interfaces - Used for IPC (though plugins typically don't use them directly)
-keep interface com.ble1st.connectias.plugin.ui.IPluginUIController { *; }
-keep interface com.ble1st.connectias.plugin.ui.IPluginUIBridge { *; }
-keep interface com.ble1st.connectias.plugin.ui.IPluginUIHost { *; }

# Keep UILifecycleEvent enum
-keep enum com.ble1st.connectias.plugin.ui.UILifecycleEvent { *; }
-keepclassmembers enum com.ble1st.connectias.plugin.ui.UILifecycleEvent {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

