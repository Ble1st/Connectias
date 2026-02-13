# ==============================================================================
# Connectias ProGuard/R8 Rules
# ==============================================================================
# Comprehensive rules for code obfuscation and optimization
# ==============================================================================

# ------------------------------------------------------------------------------
# General Configuration
# ------------------------------------------------------------------------------

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Hide the original source file name
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*

# Keep signature for generics
-keepattributes Signature

# Keep exceptions
-keepattributes Exceptions

# Keep inner classes
-keepattributes InnerClasses

# Keep enclosing method for lambdas
-keepattributes EnclosingMethod

# ------------------------------------------------------------------------------
# Kotlin
# ------------------------------------------------------------------------------

# Keep Kotlin Metadata
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# Kotlin Coroutines - MUST be kept for plugin compatibility
# Plugins use coroutines extensively (launch, async, etc.)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
# Keep coroutines classes and interfaces
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
# Keep coroutines extension functions
-keep class kotlinx.coroutines.CoroutineScopeKt { *; }
-keepclassmembers class kotlinx.coroutines.CoroutineScopeKt {
    static *** launch(...);
    static *** async(...);
    static *** coroutineScope(...);
}
# Keep Dispatchers for plugins
-keep class kotlinx.coroutines.Dispatchers { *; }
-keepclassmembers class kotlinx.coroutines.Dispatchers {
    static *** getMain();
    static *** getIO();
    static *** getDefault();
}
-dontwarn kotlinx.coroutines.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ble1st.connectias.**$$serializer { *; }
-keepclassmembers class com.ble1st.connectias.** {
    *** Companion;
}
-keepclasseswithmembers class com.ble1st.connectias.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Kotlin Reflect
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Kotlin Standard Library - MUST be kept for plugin compatibility
# Plugins depend on kotlin-stdlib from the app's classloader
# This is critical: Plugins compiled with different Kotlin versions need access
# to kotlin-stdlib methods that may be obfuscated or removed by R8
-keep class kotlin.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-keep class kotlin.jvm.internal.Intrinsics { *; }
-keepclassmembers class kotlin.jvm.internal.Intrinsics {
    static *** checkNotNullParameter(...);
    static *** checkNotNull(...);
    static *** checkParameterIsNotNull(...);
    static *** checkExpressionValueIsNotNull(...);
    static *** checkReturnedValueIsNotNull(...);
    static *** checkNotNullExpressionValue(...);
    static *** checkFieldIsNotNull(...);
    static *** checkReturnedValueIsNotNull(...);
}
# Keep all Kotlin stdlib classes and methods
-keep class kotlin.jvm.functions.** { *; }
-keep class kotlin.collections.** { *; }
-keep class kotlin.sequences.** { *; }
-keep class kotlin.ranges.** { *; }
-dontwarn kotlin.**

# ------------------------------------------------------------------------------
# Hilt / Dagger
# ------------------------------------------------------------------------------

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep @Inject annotated constructors
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# Keep @Module annotated classes
-keep @dagger.Module class * { *; }

# Keep @InstallIn annotated classes
-keep @dagger.hilt.InstallIn class * { *; }

# Keep @EntryPoint annotated classes
-keep @dagger.hilt.android.EntryPoint class * { *; }

# Keep @HiltAndroidApp annotated classes
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# Keep @AndroidEntryPoint annotated classes
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Keep Hilt ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep @HiltViewModel annotated classes
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Dagger
-dontwarn dagger.**

# ------------------------------------------------------------------------------
# Room Database
# ------------------------------------------------------------------------------

# Keep Room entities
-keep @androidx.room.Entity class * { *; }

# Keep Room DAOs
-keep @androidx.room.Dao class * { *; }

# Keep Room Database
-keep @androidx.room.Database class * { *; }

# Keep TypeConverters
-keep @androidx.room.TypeConverters class * { *; }
-keep class * {
    @androidx.room.TypeConverter <methods>;
}

# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase { *; }

# ------------------------------------------------------------------------------
# SQLCipher
# ------------------------------------------------------------------------------

# Keep SQLCipher classes
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**
-dontwarn net.zetetic.database.sqlcipher.**

# ------------------------------------------------------------------------------
# Jetpack Compose - MUST be kept for plugin compatibility
# ------------------------------------------------------------------------------

# Keep Compose classes
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep @Composable functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Compose compiler generated code
-keep class **ComposableSingletons* { *; }

# Keep ComposeView - plugins use ComposeView to embed Compose UI
-keep class androidx.compose.ui.platform.ComposeView { *; }
-keepclassmembers class androidx.compose.ui.platform.ComposeView {
    *** setContent(...);
}

# Keep Compose Runtime State Management - plugins use mutableStateOf, remember, etc.
-keep class androidx.compose.runtime.** { *; }
-keep interface androidx.compose.runtime.** { *; }
# Keep State and MutableState for plugins
-keep interface androidx.compose.runtime.State { *; }
-keep interface androidx.compose.runtime.MutableState { *; }
# Keep remember and mutableStateOf extension functions
-keep class androidx.compose.runtime.SnapshotStateKt { *; }
-keepclassmembers class androidx.compose.runtime.SnapshotStateKt {
    static *** mutableStateOf(...);
    static *** remember(...);
    static *** remember(...);
}
# Keep remember extension functions
-keep class androidx.compose.runtime.ComposableKt { *; }
-keepclassmembers class androidx.compose.runtime.ComposableKt {
    static *** remember(...);
}
# Keep StateList and StateMap for plugins
-keep class androidx.compose.runtime.snapshots.SnapshotStateList { *; }
-keep class androidx.compose.runtime.snapshots.SnapshotStateMap { *; }

# Keep Compose Foundation - plugins use rememberScrollState, etc.
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.foundation.rememberScrollState { *; }

# Material3
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.** { *; }

# ------------------------------------------------------------------------------
# Navigation Component
# ------------------------------------------------------------------------------

# Keep navigation arguments
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable

# Keep Safe Args generated classes
-keep class * extends androidx.navigation.NavArgs { *; }
-keep class **Args { *; }
-keep class **Directions { *; }
-keep class **Directions$* { *; }

# ------------------------------------------------------------------------------
# AndroidX
# ------------------------------------------------------------------------------

# Lifecycle - MUST be kept for plugin compatibility
# Plugins use lifecycle-runtime-ktx extension functions like lifecycleScope
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.lifecycle.LifecycleOwnerKt { *; }
-keepclassmembers class androidx.lifecycle.LifecycleOwnerKt {
    static *** getLifecycleScope(...);
    static *** lifecycleScope(...);
}
# Keep LifecycleCoroutineScope for plugins
-keep class androidx.lifecycle.LifecycleCoroutineScope { *; }
-keep interface androidx.lifecycle.LifecycleCoroutineScope { *; }
# Keep LifecycleOwner for plugins
-keep interface androidx.lifecycle.LifecycleOwner { *; }
-keep class androidx.lifecycle.Lifecycle { *; }
-dontwarn androidx.lifecycle.**

# Fragment - MUST be kept for plugin compatibility
# Plugins extend Fragment and use Fragment-KTX extension functions
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class androidx.fragment.app.Fragment { *; }
-keep class androidx.fragment.** { *; }
# Keep Fragment-KTX extension functions
-keep class androidx.fragment.app.FragmentKt { *; }
-keepclassmembers class androidx.fragment.app.FragmentKt {
    static *** requireContext(...);
    static *** requireActivity(...);
    static *** requireView(...);
    static *** viewLifecycleOwner(...);
    static *** viewLifecycleOwnerLiveData(...);
}
# Keep ViewLifecycleOwner for plugins
-keep interface androidx.fragment.app.FragmentViewLifecycleOwner { *; }
-keep class androidx.fragment.app.FragmentViewLifecycleOwner { *; }
-dontwarn androidx.fragment.**

# Activity - MUST be kept for plugin compatibility
# Plugins may use Activity-Compose extension functions
-keep class * extends androidx.activity.ComponentActivity { *; }
-keep class androidx.activity.** { *; }
# Keep Activity-Compose extension functions
-keep class androidx.activity.ActivityKt { *; }
-keepclassmembers class androidx.activity.ActivityKt {
    static *** setContent(...);
    static *** findComponentActivity(...);
}
-dontwarn androidx.activity.**

# Core-KTX - MUST be kept for plugin compatibility
# Plugins use Core-KTX extension functions
-keep class androidx.core.** { *; }
-keep class androidx.core.content.ContextKt { *; }
-keepclassmembers class androidx.core.content.ContextKt {
    static *** getSystemService(...);
}
-dontwarn androidx.core.**

# DataStore
-keep class androidx.datastore.** { *; }

# ------------------------------------------------------------------------------
# Third-Party Libraries
# ------------------------------------------------------------------------------

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# OkHttp Android Platform - Uses reflection to access ViewTreeSavedStateRegistryOwner
# Keep all internal platform classes and methods that use reflection
-keep class okhttp3.internal.platform.android.** { *; }
-keepclassmembers class okhttp3.internal.platform.android.** {
    *;
}
# Keep the specific method that accesses ViewTreeSavedStateRegistryOwner
-keepclassmembers class okhttp3.internal.platform.android.AndroidPlatform {
    *** findPlatform(...);
    *** get(...);
}
# Keep all methods that might use reflection for ViewTree access
-keepclassmembers class okhttp3.internal.platform.Platform {
    *** findPlatform(...);
}

# Timber
-dontwarn org.jetbrains.annotations.**
-keep class timber.log.Timber { *; }

# SLF4J
-dontwarn org.slf4j.impl.**

# ------------------------------------------------------------------------------
# Android Framework
# ------------------------------------------------------------------------------

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ------------------------------------------------------------------------------
# Connectias App-Specific Rules
# ------------------------------------------------------------------------------

# Keep all model classes
-keep class com.ble1st.connectias.**.models.** { *; }
-keep class com.ble1st.connectias.**.entities.** { *; }

# Keep all Provider classes
-keep class com.ble1st.connectias.**.*Provider { *; }

# Keep all ViewModel classes
-keep class com.ble1st.connectias.**.*ViewModel { *; }

# Keep security classes
-keep class com.ble1st.connectias.core.security.** { *; }

# Keep RASP detection classes
-keep class com.ble1st.connectias.core.security.root.RootDetector { *; }
-keep class com.ble1st.connectias.core.security.debug.DebuggerDetector { *; }
-keep class com.ble1st.connectias.core.security.emulator.EmulatorDetector { *; }
-keep class com.ble1st.connectias.core.security.tamper.TamperDetector { *; }

# Keep Application class
-keep class com.ble1st.connectias.ConnectiasApplication { *; }

# Keep MainActivity
-keep class com.ble1st.connectias.MainActivity { *; }

# Keep SecurityBlockedActivity
-keep class com.ble1st.connectias.SecurityBlockedActivity { *; }

# ------------------------------------------------------------------------------
# Plugin System - MUST be kept for reflection-based loading
# ------------------------------------------------------------------------------

# Keep all plugin-related classes and interfaces
-keep class com.ble1st.connectias.plugin.** { *; }
-keep interface com.ble1st.connectias.plugin.** { *; }

# Keep plugin SDK classes (used by plugins at runtime)
-keep class com.ble1st.connectias.plugin.sdk.** { *; }
-keep interface com.ble1st.connectias.plugin.sdk.** { *; }

# Keep IPlugin interface - critical for plugin loading
-keep interface com.ble1st.connectias.plugin.sdk.IPlugin { *; }
-keep interface com.ble1st.connectias.plugin.sdk.IPlugin$* { *; }

# Keep PluginContext and related classes
-keep class com.ble1st.connectias.plugin.sdk.PluginContext { *; }
-keep interface com.ble1st.connectias.plugin.sdk.PluginContext { *; }
-keep class com.ble1st.connectias.plugin.PluginContextImpl { *; }

# Keep PluginMetadata - used for plugin discovery
-keep class com.ble1st.connectias.plugin.sdk.PluginMetadata { *; }
-keep class com.ble1st.connectias.plugin.sdk.PluginCategory { *; }

# Keep PluginManager - handles dynamic loading
-keep class com.ble1st.connectias.plugin.PluginManager { *; }
-keep class com.ble1st.connectias.plugin.PluginManager$* { *; }

# Keep NativeLibraryManager - loads plugin native libraries
-keep class com.ble1st.connectias.plugin.NativeLibraryManager { *; }
-keep class com.ble1st.connectias.plugin.NativeLibraryManager$* { *; }

# Keep PluginInfo data class
-keep class com.ble1st.connectias.plugin.PluginManager$PluginInfo { *; }
-keep class com.ble1st.connectias.plugin.PluginManager$PluginState { *; }

# Keep reflection-based class loading methods
-keepclassmembers class com.ble1st.connectias.plugin.PluginManager {
    *** loadClass(...);
    *** newInstance(...);
    *** getDeclaredConstructor(...);
    *** createPluginFragment(...);
}
# Keep all reflection methods used by PluginManager
-keepclassmembers class java.lang.Class {
    java.lang.reflect.Constructor getDeclaredConstructor(...);
    java.lang.Object newInstance(...);
    java.lang.Class loadClass(...);
}
# Keep Constructor class for reflection
-keep class java.lang.reflect.Constructor { *; }
-keepclassmembers class java.lang.reflect.Constructor {
    java.lang.Object newInstance(...);
}

# Keep DexClassLoader usage (needed for plugin loading)
-keep class dalvik.system.DexClassLoader { *; }
-keep class dalvik.system.BaseDexClassLoader { *; }

# Keep InMemoryDexClassLoader - CRITICAL for new plugin system
-keep class dalvik.system.InMemoryDexClassLoader { *; }
-keepclassmembers class dalvik.system.InMemoryDexClassLoader {
    <init>(java.nio.ByteBuffer[], java.lang.ClassLoader);
    <init>(java.nio.ByteBuffer, java.lang.ClassLoader);
}

# Keep DexFile for DEX inspection in sandbox
-keep class dalvik.system.DexFile { *; }
-keepclassmembers class dalvik.system.DexFile {
    java.util.Enumeration entries();
}

# Keep ByteBuffer for DEX loading
-keep class java.nio.ByteBuffer { *; }
-keepclassmembers class java.nio.ByteBuffer {
    static *** wrap(...);
    byte[] array();
    int remaining();
}

# Keep all classes that might be loaded dynamically by plugins
# This is important for reflection-based plugin instantiation
-keepclassmembers class * {
    <init>();
}

# Keep plugin fragment classes (if plugins use fragments)
-keep class * extends androidx.fragment.app.Fragment {
    <init>(...);
}

# Keep plugin classes that implement IPlugin (loaded via reflection)
-keep class * implements com.ble1st.connectias.plugin.sdk.IPlugin {
    <init>();
    *;
}

# Keep JSON parsing classes used for plugin manifest
-keep class org.json.** { *; }

# Keep plugin import handler
-keep class com.ble1st.connectias.plugin.PluginImportHandler { *; }

# Keep plugin notification manager
-keep class com.ble1st.connectias.plugin.PluginNotificationManager { *; }

# Keep PluginManifestParser - parses plugin manifests (uses reflection)
-keep class com.ble1st.connectias.plugin.PluginManifestParser { *; }
-keepclassmembers class com.ble1st.connectias.plugin.PluginManifestParser {
    *;
}

# Keep PluginPermissionManager - critical for permission enforcement
-keep class com.ble1st.connectias.plugin.PluginPermissionManager { *; }
-keepclassmembers class com.ble1st.connectias.plugin.PluginPermissionManager {
    *;
}

# Keep SecureContextWrapper - intercepts system service calls
-keep class com.ble1st.connectias.plugin.SecureContextWrapper { *; }
-keepclassmembers class com.ble1st.connectias.plugin.SecureContextWrapper {
    *;
}

# Keep PluginFragmentWrapper - wraps plugin fragments for exception handling
-keep class com.ble1st.connectias.plugin.PluginFragmentWrapper { *; }
-keepclassmembers class com.ble1st.connectias.plugin.PluginFragmentWrapper {
    *;
}

# Keep PluginManagerSandbox - manages plugins in sandbox process
-keep class com.ble1st.connectias.plugin.PluginManagerSandbox { *; }
-keep class com.ble1st.connectias.plugin.PluginManagerSandbox$* { *; }
-keepclassmembers class com.ble1st.connectias.plugin.PluginManagerSandbox {
    *;
}

# Keep PluginPermissionException - used for permission validation errors
-keep class com.ble1st.connectias.plugin.PluginPermissionException { *; }
-keepclassmembers class com.ble1st.connectias.plugin.PluginPermissionException {
    <init>(...);
}

# Keep PluginPermissionBroadcast - for inter-process permission synchronization
-keep class com.ble1st.connectias.plugin.PluginPermissionBroadcast { *; }
-keepclassmembers class com.ble1st.connectias.plugin.PluginPermissionBroadcast {
    *;
}

# Keep PluginSandboxService - AIDL service running in separate process
-keep class com.ble1st.connectias.core.plugin.PluginSandboxService { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.PluginSandboxService {
    *;
}

# Keep PluginSandboxProxy - proxy for IPC communication with sandbox
-keep class com.ble1st.connectias.core.plugin.PluginSandboxProxy { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.PluginSandboxProxy {
    *;
}

# Keep SandboxPluginContext - context wrapper for sandbox process
-keep class com.ble1st.connectias.core.plugin.SandboxPluginContext { *; }
-keep interface com.ble1st.connectias.core.plugin.SandboxPluginContext { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.SandboxPluginContext {
    *;
}

# Keep AIDL interfaces and Parcelables - CRITICAL for IPC
# AIDL generates stub classes that must not be obfuscated
-keep class com.ble1st.connectias.plugin.IPluginSandbox { *; }
-keep interface com.ble1st.connectias.plugin.IPluginSandbox { *; }
-keep class com.ble1st.connectias.plugin.IPluginSandbox$* { *; }
-keep class com.ble1st.connectias.plugin.IPluginSandbox$Stub { *; }
-keep class com.ble1st.connectias.plugin.IPluginSandbox$Stub$Proxy { *; }

# Keep AIDL Binder interface methods - required for IPC
-keepclassmembers interface com.ble1st.connectias.plugin.IPluginSandbox {
    *;
}

# Keep Android Binder classes used by AIDL
-keep class android.os.Binder { *; }
-keep class android.os.IBinder { *; }
-keep interface android.os.IInterface { *; }
-keepclassmembers class android.os.Binder {
    boolean onTransact(int, android.os.Parcel, android.os.Parcel, int);
}

# Keep AIDL Parcelable classes
-keep class com.ble1st.connectias.plugin.PluginMetadataParcel { *; }
-keepclassmembers class com.ble1st.connectias.plugin.PluginMetadataParcel {
    <init>(...);
    void writeToParcel(...);
    *** createFromParcel(...);
}

# Keep additional AIDL classes for new features
-keep class com.ble1st.connectias.plugin.PluginResultParcel { *; }
-keepclassmembers class com.ble1st.connectias.plugin.PluginResultParcel {
    <init>(...);
    void writeToParcel(...);
    *** createFromParcel(...);
}

# Keep PermissionResult for async permission callbacks
-keep class com.ble1st.connectias.plugin.PermissionResult { *; }
-keepclassmembers class com.ble1st.connectias.plugin.PermissionResult {
    <init>(...);
    void writeToParcel(...);
    *** createFromParcel(...);
}

# Keep all AIDL interfaces with their inner classes
-keep class com.ble1st.connectias.plugin.IFileSystemBridge { *; }
-keep class com.ble1st.connectias.plugin.IFileSystemBridge$* { *; }
-keep class com.ble1st.connectias.plugin.IPermissionCallback { *; }
-keep class com.ble1st.connectias.plugin.IPermissionCallback$* { *; }

# Keep Parcelable.Creator for AIDL Parcelables
-keepclassmembers class com.ble1st.connectias.plugin.PluginMetadataParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class com.ble1st.connectias.plugin.PluginResultParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep ContextWrapper methods used by SecureContextWrapper
-keep class android.content.ContextWrapper { *; }
-keepclassmembers class android.content.ContextWrapper {
    android.content.Context getBaseContext();
    android.content.Context getApplicationContext();
    java.lang.Object getSystemService(java.lang.String);
    int checkSelfPermission(java.lang.String);
}

# Keep BroadcastReceiver for PluginPermissionBroadcast
-keep class android.content.BroadcastReceiver { *; }
-keepclassmembers class android.content.BroadcastReceiver {
    void onReceive(android.content.Context, android.content.Intent);
}

# Keep Hardware Bridge interfaces - CRITICAL for plugin hardware access
-keep class com.ble1st.connectias.hardware.IHardwareBridge { *; }
-keep interface com.ble1st.connectias.hardware.IHardwareBridge { *; }
-keep class com.ble1st.connectias.hardware.IHardwareBridge$* { *; }
-keepclassmembers interface com.ble1st.connectias.hardware.IHardwareBridge {
    *;
}

# Keep Hardware Bridge implementations
-keep class com.ble1st.connectias.hardware.HardwareBridgeService { *; }
-keep class com.ble1st.connectias.hardware.CameraBridge { *; }
-keep class com.ble1st.connectias.hardware.NetworkBridge { *; }
-keep class com.ble1st.connectias.hardware.PrinterBridge { *; }
-keep class com.ble1st.connectias.hardware.BluetoothBridge { *; }

# Keep ZIP handling for APK extraction
-keep class java.util.zip.ZipFile { *; }
-keep class java.util.zip.ZipInputStream { *; }
-keep class java.util.zip.ZipEntry { *; }
-keepclassmembers class java.util.zip.ZipFile {
    java.util.zip.ZipEntry getEntry(java.lang.String);
    java.io.InputStream getInputStream(java.util.zip.ZipEntry);
}
-keepclassmembers class java.util.zip.ZipInputStream {
    java.util.zip.ZipEntry nextEntry();
}

# Keep ParcelFileDescriptor for IPC
-keep class android.os.ParcelFileDescriptor { *; }
-keepclassmembers class android.os.ParcelFileDescriptor {
    android.os.ParcelFileDescriptor$Dup dup();
    java.io.FileInputStream getFileInputStream();
    java.io.FileOutputStream getFileOutputStream();
    static *** fromFd(...);
    static *** adoptFd(...);
    static *** dup(...);
    java.io.FileDescriptor getFileDescriptor();
    boolean canDetectErrors();
    void close();
}

# Keep FileDescriptor - Used in BluetoothBridge and CameraBridge
-keep class java.io.FileDescriptor { *; }
-keepclassmembers class java.io.FileDescriptor {
    int getInt$();
    void setInt$(int);
    boolean valid();
}

# Keep InputStream/ByteArray conversions for plugin loading
-keep class java.io.ByteArrayInputStream { *; }
-keep class java.io.ByteArrayOutputStream { *; }
-keepclassmembers class java.io.ByteArrayInputStream {
    byte[] readBytes();
}
-keepclassmembers class java.io.ByteArrayOutputStream {
    byte[] toByteArray();
}
-keep class android.content.SharedPreferences { *; }
-keep interface android.content.SharedPreferences { *; }
-keep class android.content.SharedPreferences$Editor { *; }
-keep interface android.content.SharedPreferences$Editor { *; }

# ------------------------------------------------------------------------------
# Plugin Exception Handler - MUST be kept for runtime exception handling
# ------------------------------------------------------------------------------

# Keep PluginExceptionHandler class and all its methods
-keep class com.ble1st.connectias.plugin.PluginExceptionHandler { *; }
-keepclassmembers class com.ble1st.connectias.plugin.PluginExceptionHandler {
    *;
}

# Keep all methods of PluginExceptionHandler (including inline functions)
# Inline functions are inlined at compile time, but the class must still be kept
# for reflection-based access and debugging
-keepclassmembers class com.ble1st.connectias.plugin.PluginExceptionHandler {
    public static *** safePluginCall(...);
    public static *** safePluginBooleanCall(...);
    public static *** safePluginFragmentCall(...);
}

# Keep exception types that PluginExceptionHandler handles
# These are needed for proper exception type checking and logging
-keep class java.lang.Exception { *; }
-keep class java.lang.Error { *; }
-keep class java.lang.OutOfMemoryError { *; }
-keep class java.lang.LinkageError { *; }
-keep class java.lang.ClassCastException { *; }
-keep class java.lang.NullPointerException { *; }
-keep class java.lang.RuntimeException { *; }
-keep class java.lang.IllegalArgumentException { *; }
-keep class java.lang.IllegalStateException { *; }

# Keep exception constructors and methods for proper exception handling
-keepclassmembers class java.lang.Exception {
    <init>(...);
    <init>(java.lang.String);
    <init>(java.lang.String, java.lang.Throwable);
    <init>(java.lang.Throwable);
}
-keepclassmembers class java.lang.Error {
    <init>(...);
    <init>(java.lang.String);
    <init>(java.lang.String, java.lang.Throwable);
    <init>(java.lang.Throwable);
}

# Keep Throwable class and methods (base class for all exceptions)
-keep class java.lang.Throwable { *; }
-keepclassmembers class java.lang.Throwable {
    java.lang.String getMessage();
    java.lang.String getLocalizedMessage();
    java.lang.Throwable getCause();
    java.lang.StackTraceElement[] getStackTrace();
    void printStackTrace();
    void printStackTrace(java.io.PrintStream);
    void printStackTrace(java.io.PrintWriter);
}

# Keep StackTraceElement for proper stack trace logging
-keep class java.lang.StackTraceElement { *; }
-keepclassmembers class java.lang.StackTraceElement {
    java.lang.String getClassName();
    java.lang.String getMethodName();
    java.lang.String getFileName();
    int getLineNumber();
}

# Keep Timber logging methods used by PluginExceptionHandler
# (Timber is already kept in the general rules, but ensure it's not obfuscated)
-keep class timber.log.Timber { *; }
-keepclassmembers class timber.log.Timber {
    public static void e(...);
    public static void e(java.lang.Throwable, ...);
    public static void w(...);
    public static void w(java.lang.Throwable, ...);
    public static void i(...);
    public static void d(...);
}

# ------------------------------------------------------------------------------
# Suppress Warnings
# ------------------------------------------------------------------------------

-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Google Tink / Google HTTP client (pulled via security-crypto)
-dontwarn com.google.crypto.tink.util.KeysDownloader
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# JavaPoet tooling classes (not present on Android at runtime)
-dontwarn com.squareup.javapoet.**
-dontwarn javax.lang.model.**
-dontwarn javax.tools.**

# SSHJ optional GSSAPI / JAAS
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.login.**

# JDK-internal X509 classes referenced by EdDSA
-dontwarn sun.security.x509.**

# ------------------------------------------------------------------------------
# Isolated Process Support - CRITICAL for new sandbox system
# ------------------------------------------------------------------------------

# Keep Handler for memory monitoring in isolated process
-keep class android.os.Handler { *; }
-keepclassmembers class android.os.Handler {
    boolean post(java.lang.Runnable);
    boolean postDelayed(java.lang.Runnable, long);
    void removeCallbacks(java.lang.Runnable);
}

# Keep Runnable for memory monitoring tasks
-keep interface java.lang.Runnable { *; }
-keepclassmembers class java.lang.Runnable {
    void run();
}

# Keep MemoryInfo for memory monitoring
-keep class android.app.ActivityManager$MemoryInfo { *; }
-keepclassmembers class android.app.ActivityManager$MemoryInfo {
    long availMem;
    long totalMem;
    long threshold;
    boolean lowMemory;
}

# Keep Debug.MemoryInfo for detailed memory info
-keep class android.os.Debug$MemoryInfo { *; }
-keepclassmembers class android.os.Debug$MemoryInfo {
    long getTotalPss();
    long getTotalPrivateDirty();
    long getTotalSharedDirty();
}

# Keep Process class for process status
-keep class android.os.Process { *; }
-keepclassmembers class android.os.Process {
    int myPid();
    int getUidForPid(int);
    int getThreadPriority(int);
}

# Keep isolated process service components
-keep class * extends android.app.Service {
    <init>();
    void onCreate();
    void onDestroy();
    android.os.IBinder onBind(android.content.Intent);
}

# Keep all methods in PluginSandboxService (isolated process)
-keepclassmembers class com.ble1st.connectias.core.plugin.PluginSandboxService {
    *;
}

# Keep memory monitoring classes
-keep class com.ble1st.connectias.core.plugin.PluginSandboxService$PluginMemoryMonitor { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.PluginSandboxService$PluginMemoryMonitor {
    *;
}

# Keep SandboxPluginInfo for plugin tracking
-keep class com.ble1st.connectias.core.plugin.PluginSandboxService$SandboxPluginInfo { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.PluginSandboxService$SandboxPluginInfo {
    *;
}

# ------------------------------------------------------------------------------
# File System Bridge - NEW for secure file access
# ------------------------------------------------------------------------------

# Keep FileSystemBridge service and interface
-keep class com.ble1st.connectias.core.plugin.FileSystemBridgeService { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.FileSystemBridgeService {
    *;
}

-keep class com.ble1st.connectias.plugin.IFileSystemBridge { *; }
-keep interface com.ble1st.connectias.plugin.IFileSystemBridge { *; }
-keepclassmembers interface com.ble1st.connectias.plugin.IFileSystemBridge {
    *;
}

# Keep file system methods in SandboxPluginContext
-keepclassmembers class com.ble1st.connectias.core.plugin.SandboxPluginContext {
    *** createFile(...);
    *** openFile(...);
    *** openFileForWrite(...);
    *** deleteFile(...);
    *** fileExists(...);
    *** listFiles(...);
    *** getFileSize(...);
}

# ------------------------------------------------------------------------------
# Async Permission Requests - NEW for non-blocking permission handling
# ------------------------------------------------------------------------------

# Keep permission callback interface
-keep class com.ble1st.connectias.plugin.IPermissionCallback { *; }
-keep interface com.ble1st.connectias.plugin.IPermissionCallback { *; }
-keepclassmembers interface com.ble1st.connectias.plugin.IPermissionCallback {
    *;
}

# Keep async permission methods in PluginSandboxService
-keepclassmembers class com.ble1st.connectias.core.plugin.PluginSandboxService {
    *** requestPermissionAsync(...);
    *** requestPermissionsAsync(...);
    *** setPermissionCallback(...);
}

# Keep permission broadcast methods
-keepclassmembers class com.ble1st.connectias.plugin.PluginPermissionBroadcast {
    *** createPermissionRequestIntent(...);
    *** createMultiplePermissionRequestIntent(...);
}

# Keep List and Boolean for permission results
-keep class java.util.List { *; }
-keep class java.lang.Boolean { *; }

# ------------------------------------------------------------------------------
# Memory Profiling - Enhanced for precise memory tracking
# ------------------------------------------------------------------------------

# Keep Debug.MemoryInfo access
-keep class android.os.Debug$MemoryInfo { *; }
-keepclassmembers class android.os.Debug$MemoryInfo {
    *** getTotalPss();
    *** getTotalPrivateDirty();
    *** getTotalSharedDirty();
}

# Keep Process class for PID access
-keepclassmembers class android.os.Process {
    *** myPid();
}

# Suppress warnings about isolated process limitations
-dontwarn android.content.ContextImpl
-dontwarn android.app.ContextImpl

# ------------------------------------------------------------------------------
# NEW SECURITY FEATURES - CRITICAL for Release Builds
# ------------------------------------------------------------------------------

# SecurityAuditManager - MUST be kept for audit logging and event collection
-keep class com.ble1st.connectias.plugin.security.SecurityAuditManager { *; }
-keepclassmembers class com.ble1st.connectias.plugin.security.SecurityAuditManager {
    *;
}

# Keep SecurityAuditEvent and related data classes - used for JSON serialization
-keep class com.ble1st.connectias.plugin.security.SecurityAuditManager$SecurityAuditEvent { *; }
-keep class com.ble1st.connectias.plugin.security.SecurityAuditManager$SecurityEventType { *; }
-keep class com.ble1st.connectias.plugin.security.SecurityAuditManager$SecuritySeverity { *; }
-keep class com.ble1st.connectias.plugin.security.SecurityAuditManager$DeviceInfo { *; }
-keep class com.ble1st.connectias.plugin.security.SecurityAuditManager$SecurityStatistics { *; }
-keep class com.ble1st.connectias.plugin.security.SecurityAuditManager$PluginSecuritySummary { *; }
-keep class com.ble1st.connectias.plugin.security.SecurityAuditManager$RiskLevel { *; }

# Keep all SecurityAuditManager methods - used by reflection and coroutines
-keepclassmembers class com.ble1st.connectias.plugin.security.SecurityAuditManager {
    *** logSecurityEvent(...);
    *** logPluginSpoofingAttempt(...);
    *** logNetworkPolicyViolation(...);
    *** logResourceLimitViolation(...);
    *** logPluginVerificationFailure(...);
    *** logMaliciousPluginDetected(...);
    *** logPermissionViolation(...);
    *** logRootDetection(...);
    *** logDebuggerDetection(...);
    *** getEventsForPlugin(...);
    *** getEventsBySeverity(...);
    *** getEventsByType(...);
    *** getPluginSecuritySummary(...);
}

# EnhancedPluginResourceLimiter - MUST be kept for resource enforcement
-keep class com.ble1st.connectias.plugin.security.EnhancedPluginResourceLimiter { *; }
-keepclassmembers class com.ble1st.connectias.plugin.security.EnhancedPluginResourceLimiter {
    *;
}

# Keep ResourceLimits and ResourceUsage data classes
-keep class com.ble1st.connectias.plugin.security.EnhancedPluginResourceLimiter$ResourceLimits { *; }
-keep class com.ble1st.connectias.plugin.security.EnhancedPluginResourceLimiter$ResourceUsage { *; }
-keep class com.ble1st.connectias.plugin.security.EnhancedPluginResourceLimiter$EnforcementResult { *; }

# Keep all EnhancedPluginResourceLimiter methods - used for CPU/Memory/Disk enforcement
-keepclassmembers class com.ble1st.connectias.plugin.security.EnhancedPluginResourceLimiter {
    *** registerPlugin(...);
    *** unregisterPlugin(...);
    *** enforceMemoryLimits(...);
    *** enforceCpuLimits(...);
    *** enforceDiskLimits(...);
    *** monitorResourceUsage(...);
    *** getPluginsExceedingLimits();
}

# Enhanced Network Policy - MUST be kept for network security
-keep class com.ble1st.connectias.plugin.security.EnhancedPluginNetworkPolicy { *; }
-keepclassmembers class com.ble1st.connectias.plugin.security.EnhancedPluginNetworkPolicy {
    *;
}

# Keep NetworkPolicyConfig and related classes
-keep class com.ble1st.connectias.plugin.security.EnhancedPluginNetworkPolicy$NetworkPolicyConfig { *; }
-keep class com.ble1st.connectias.plugin.security.EnhancedPluginNetworkPolicy$PolicyResult { *; }
-keep class com.ble1st.connectias.plugin.security.EnhancedPluginNetworkPolicy$PolicyReport { *; }

# Keep PluginNetworkTracker - used for network monitoring
-keep class com.ble1st.connectias.plugin.security.PluginNetworkTracker { *; }
-keepclassmembers class com.ble1st.connectias.plugin.security.PluginNetworkTracker {
    *;
}

# Keep NetworkUsageAggregator - used for bandwidth tracking
-keep class com.ble1st.connectias.plugin.security.NetworkUsageAggregator { *; }
-keepclassmembers class com.ble1st.connectias.plugin.security.NetworkUsageAggregator {
    *;
}

# Security UI Components - MUST be kept for Compose UI
-keep class com.ble1st.connectias.ui.plugin.security.SecurityAuditDashboardScreen { *; }
-keep class com.ble1st.connectias.ui.plugin.security.SecurityAuditDashboardFragment { *; }
-keep class com.ble1st.connectias.ui.plugin.security.NetworkPolicyConfigurationScreen { *; }
-keep class com.ble1st.connectias.ui.plugin.security.NetworkPolicyConfigurationFragment { *; }
-keep class com.ble1st.connectias.ui.plugin.security.NetworkPolicyDialogs { *; }

# Keep all @Composable functions in security UI
-keepclassmembers class com.ble1st.connectias.ui.plugin.security.** {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep SecureHardwareBridgeWrapper - MUST be kept for security enforcement
-keep class com.ble1st.connectias.plugin.security.SecureHardwareBridgeWrapper { *; }
-keepclassmembers class com.ble1st.connectias.plugin.security.SecureHardwareBridgeWrapper {
    *;
}

# Keep PluginIdentitySession - MUST be kept for identity verification
-keep class com.ble1st.connectias.plugin.security.PluginIdentitySession { *; }
-keepclassmembers class com.ble1st.connectias.plugin.security.PluginIdentitySession {
    *;
}

# Keep PluginThreadMonitor - MUST be kept for thread monitoring
-keep class com.ble1st.connectias.plugin.security.PluginThreadMonitor { *; }
-keepclassmembers class com.ble1st.connectias.plugin.security.PluginThreadMonitor {
    *;
}

# Enhanced Hardware Bridge Service - MUST be kept for extended functionality
-keepclassmembers class com.ble1st.connectias.hardware.HardwareBridgeService {
    *** registerPluginNetworking(...);
    *** unregisterPluginNetworking(...);
}

# Updated AIDL interfaces with new methods
-keepclassmembers interface com.ble1st.connectias.hardware.IHardwareBridge {
    *** registerPluginNetworking(...);
    *** unregisterPluginNetworking(...);
}

-keepclassmembers interface com.ble1st.connectias.plugin.IPluginSandbox {
    *** getSandboxPid();
    *** getSandboxMemoryUsage();
    *** getSandboxMaxMemory();
}

# Keep HardwareResponseParcel - MUST be kept for AIDL communication
-keep class com.ble1st.connectias.hardware.HardwareResponseParcel { *; }
-keepclassmembers class com.ble1st.connectias.hardware.HardwareResponseParcel {
    <init>(...);
    void writeToParcel(...);
    *** createFromParcel(...);
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep ActivityManager.RunningAppProcessInfo for memory monitoring
-keep class android.app.ActivityManager$RunningAppProcessInfo { *; }
-keepclassmembers class android.app.ActivityManager$RunningAppProcessInfo {
    *** pid;
    *** processName;
    *** importance;
}

# Keep ActivityManager.MemoryInfo for memory limits
-keepclassmembers class android.app.ActivityManager {
    *** getProcessMemoryInfo(...);
    *** getRunningAppProcesses();
    *** killBackgroundProcesses(...);
}

# Kotlinx Serialization for SecurityAuditManager JSON logging
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.ble1st.connectias.plugin.security.SecurityAuditManager$** {
    *** Companion;
}
-keepclasseswithmembers class com.ble1st.connectias.plugin.security.SecurityAuditManager$** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ble1st.connectias.plugin.security.SecurityAuditManager$**$$serializer { *; }

# Keep Flow and StateFlow for reactive UI updates
-keep class kotlinx.coroutines.flow.Flow { *; }
-keep class kotlinx.coroutines.flow.StateFlow { *; }
-keep class kotlinx.coroutines.flow.MutableStateFlow { *; }
-keep class kotlinx.coroutines.flow.FlowKt { *; }
-keepclassmembers class kotlinx.coroutines.flow.FlowKt {
    static *** collectAsStateWithLifecycle(...);
}

# Keep Compose lifecycle integration for security UI
-keep class androidx.lifecycle.compose.** { *; }
-keepclassmembers class androidx.lifecycle.compose.** {
    static *** collectAsStateWithLifecycle(...);
}

# Keep File I/O classes for security audit logging
-keep class java.io.File { *; }
-keepclassmembers class java.io.File {
    *** exists();
    *** createNewFile();
    *** mkdirs();
    *** getParentFile();
    *** appendText(...);
    *** length();
    *** renameTo(...);
    *** walkTopDown();
    *** walkBottomUp();
    *** lastModified();
    *** delete();
}

# Keep Thread and ThreadGroup for plugin isolation
-keep class java.lang.Thread { *; }
-keep class java.lang.ThreadGroup { *; }
-keepclassmembers class java.lang.Thread {
    *** getId();
    *** getPriority();
    *** setPriority(...);
}
-keepclassmembers class java.lang.ThreadGroup {
    *** activeCount();
    *** enumerate(...);
    *** interrupt();
}

# Keep ConcurrentHashMap and atomic classes for thread-safe operations
-keep class java.util.concurrent.ConcurrentHashMap { *; }
-keep class java.util.concurrent.atomic.AtomicBoolean { *; }
-keep class java.util.concurrent.atomic.AtomicLong { *; }
-keep class java.util.concurrent.atomic.AtomicInteger { *; }
-keep class java.util.concurrent.ConcurrentLinkedQueue { *; }

# Keep UUID for audit event IDs
-keep class java.util.UUID { *; }
-keepclassmembers class java.util.UUID {
    static *** randomUUID();
    *** toString();
}

# Keep SimpleDateFormat for audit timestamps
-keep class java.text.SimpleDateFormat { *; }
-keepclassmembers class java.text.SimpleDateFormat {
    *** format(...);
}

# Keep Date for timestamps
-keep class java.util.Date { *; }
-keepclassmembers class java.util.Date {
    <init>(long);
}

# Keep StatFs for disk usage monitoring
-keep class android.os.StatFs { *; }
-keepclassmembers class android.os.StatFs {
    *** getAvailableBytes();
    *** getTotalBytes();
}

# Keep RandomAccessFile for advanced file operations
-keep class java.io.RandomAccessFile { *; }
-keepclassmembers class java.io.RandomAccessFile {
    *** length();
    *** seek(...);
    *** read(...);
    *** write(...);
}

# Keep SupervisorJob for coroutine error handling
-keep class kotlinx.coroutines.SupervisorJob { *; }
-keepclassmembers class kotlinx.coroutines.SupervisorJob {
    <init>(...);
}

# Keep CoroutineScope for background tasks
-keep class kotlinx.coroutines.CoroutineScope { *; }
-keep interface kotlinx.coroutines.CoroutineScope { *; }
-keepclassmembers class kotlinx.coroutines.CoroutineScopeKt {
    static *** CoroutineScope(...);
}

# Keep delay function for monitoring intervals
-keepclassmembers class kotlinx.coroutines.DelayKt {
    static *** delay(...);
}

# Keep withContext for dispatcher switching
-keepclassmembers class kotlinx.coroutines.BuildersKt {
    static *** withContext(...);
    static *** runBlocking(...);
}

# Keep Dispatchers.IO for file operations
-keepclassmembers class kotlinx.coroutines.Dispatchers {
    static *** getIO();
}

# Keep Json serialization for audit logging
-keep class kotlinx.serialization.json.Json { *; }
-keep class kotlinx.serialization.json.JsonBuilder { *; }
-keepclassmembers class kotlinx.serialization.json.Json {
    *** encodeToString(...);
    *** decodeFromString(...);
}

# Keep Hilt modules for security features
-keep @dagger.Module class com.ble1st.connectias.di.SecurityPolicyModule { *; }
-keep @dagger.Module class com.ble1st.connectias.di.NetworkPolicyModule { *; }

# ------------------------------------------------------------------------------
# Three-Process UI Architecture - CRITICAL for Release Builds
# ------------------------------------------------------------------------------
# These rules ensure IPC communication between Main, Sandbox, and UI processes
# works correctly after R8 obfuscation.

# ------------------------------------------------------------------------------
# AIDL Interfaces - Three-Process UI Communication
# ------------------------------------------------------------------------------

# Keep IPluginUIController - Sandbox → UI Process communication
-keep class com.ble1st.connectias.plugin.ui.IPluginUIController { *; }
-keep interface com.ble1st.connectias.plugin.ui.IPluginUIController { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIController$* { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIController$Stub { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIController$Stub$Proxy { *; }
-keepclassmembers interface com.ble1st.connectias.plugin.ui.IPluginUIController {
    *;
}

# Keep IPluginUIBridge - UI → Sandbox Process communication
-keep class com.ble1st.connectias.plugin.ui.IPluginUIBridge { *; }
-keep interface com.ble1st.connectias.plugin.ui.IPluginUIBridge { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIBridge$* { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIBridge$Stub { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIBridge$Stub$Proxy { *; }
-keepclassmembers interface com.ble1st.connectias.plugin.ui.IPluginUIBridge {
    *;
}

# Keep IPluginUIHost - Main → UI Process communication
-keep class com.ble1st.connectias.plugin.ui.IPluginUIHost { *; }
-keep interface com.ble1st.connectias.plugin.ui.IPluginUIHost { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIHost$* { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIHost$Stub { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIHost$Stub$Proxy { *; }
-keepclassmembers interface com.ble1st.connectias.plugin.ui.IPluginUIHost {
    *;
}

# Keep IPluginUIMainCallback - IME/Tastatur-Proxy (UI Process → Main Process)
# Required for keyboard overlay when plugin TextField gains focus on VirtualDisplay
-keep class com.ble1st.connectias.plugin.ui.IPluginUIMainCallback { *; }
-keep interface com.ble1st.connectias.plugin.ui.IPluginUIMainCallback { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIMainCallback$* { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIMainCallback$Stub { *; }
-keep class com.ble1st.connectias.plugin.ui.IPluginUIMainCallback$Stub$Proxy { *; }
-keepclassmembers interface com.ble1st.connectias.plugin.ui.IPluginUIMainCallback {
    *;
}

# ------------------------------------------------------------------------------
# AIDL Parcelables - Three-Process UI Data Transfer
# ------------------------------------------------------------------------------

# Keep UIStateParcel - Complete UI state sent from Sandbox to UI Process
-keep class com.ble1st.connectias.plugin.ui.UIStateParcel { *; }
-keepclassmembers class com.ble1st.connectias.plugin.ui.UIStateParcel {
    <init>(...);
    void writeToParcel(...);
    *** createFromParcel(...);
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep UIComponentParcel - Individual UI component description
-keep class com.ble1st.connectias.plugin.ui.UIComponentParcel { *; }
-keepclassmembers class com.ble1st.connectias.plugin.ui.UIComponentParcel {
    <init>(...);
    void writeToParcel(...);
    *** createFromParcel(...);
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep UserActionParcel - User action from UI Process to Sandbox
-keep class com.ble1st.connectias.plugin.ui.UserActionParcel { *; }
-keepclassmembers class com.ble1st.connectias.plugin.ui.UserActionParcel {
    <init>(...);
    void writeToParcel(...);
    *** createFromParcel(...);
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep UIEventParcel - UI event from Sandbox to UI Process
-keep class com.ble1st.connectias.plugin.ui.UIEventParcel { *; }
-keepclassmembers class com.ble1st.connectias.plugin.ui.UIEventParcel {
    <init>(...);
    void writeToParcel(...);
    *** createFromParcel(...);
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep MotionEventParcel - Touch event data for UI Process
-keep class com.ble1st.connectias.plugin.ui.MotionEventParcel { *; }
-keepclassmembers class com.ble1st.connectias.plugin.ui.MotionEventParcel {
    <init>(...);
    void writeToParcel(...);
    *** createFromParcel(...);
    public static final android.os.Parcelable$Creator CREATOR;
}

# ------------------------------------------------------------------------------
# Three-Process UI Service Classes
# ------------------------------------------------------------------------------

# Keep PluginUIService - Service running in UI Process (:plugin_ui)
-keep class com.ble1st.connectias.core.plugin.ui.PluginUIService { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.PluginUIService {
    *;
}

# Keep PluginUIHostImpl - Implementation of IPluginUIHost in UI Process
-keep class com.ble1st.connectias.core.plugin.ui.PluginUIHostImpl { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.PluginUIHostImpl {
    *;
}

# Keep PluginUIContainerFragment - IME-Overlay (Tastatur im Main Process)
-keep class com.ble1st.connectias.core.plugin.ui.PluginUIContainerFragment { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.PluginUIContainerFragment {
    *;
}

# Keep PluginUIControllerImpl - Implementation in Sandbox Process
-keep class com.ble1st.connectias.core.plugin.PluginUIControllerImpl { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.PluginUIControllerImpl {
    *;
}

# Keep PluginUIControllerUIProcess - Implementation in UI Process
-keep class com.ble1st.connectias.core.plugin.ui.PluginUIControllerUIProcess { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.PluginUIControllerUIProcess {
    *;
}

# Keep PluginUIBridgeImpl - Bridge implementation in Sandbox Process
-keep class com.ble1st.connectias.core.plugin.PluginUIBridgeImpl { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.PluginUIBridgeImpl {
    *;
}

# Keep PluginUIProcessProxy - Proxy for Main Process → UI Process communication
-keep class com.ble1st.connectias.core.plugin.PluginUIProcessProxy { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.PluginUIProcessProxy {
    *;
}

# Keep VirtualDisplayManager - Manages VirtualDisplay for UI rendering
-keep class com.ble1st.connectias.core.plugin.ui.VirtualDisplayManager { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.VirtualDisplayManager {
    *;
}

# Keep UIStateDiffer - State diffing for performance optimization
-keep class com.ble1st.connectias.core.plugin.ui.UIStateDiffer { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.UIStateDiffer {
    *;
}

# ------------------------------------------------------------------------------
# Three-Process UI Fragment and Compose Components
# ------------------------------------------------------------------------------

# Keep PluginUIFragment - Fragment in UI Process for plugin UI rendering
-keep class com.ble1st.connectias.core.plugin.ui.PluginUIFragment { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.PluginUIFragment {
    *;
}

# Keep PluginUIComposable - Compose UI renderer in UI Process
-keep class com.ble1st.connectias.core.plugin.ui.PluginUIComposable { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.PluginUIComposable {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep all @Composable functions in plugin UI package
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.** {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep component renderers (RenderButton, RenderTextField, etc.)
-keep class com.ble1st.connectias.core.plugin.ui.Render* { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.Render* {
    @androidx.compose.runtime.Composable <methods>;
}

# ------------------------------------------------------------------------------
# Three-Process UI Enums and Data Classes
# ------------------------------------------------------------------------------

# Keep ComponentType enum - Used in UIComponentParcel
-keep enum com.ble1st.connectias.core.plugin.ui.ComponentType { *; }
-keepclassmembers enum com.ble1st.connectias.core.plugin.ui.ComponentType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep ActionType enum - Used in UserActionParcel
-keep enum com.ble1st.connectias.core.plugin.ui.ActionType { *; }
-keepclassmembers enum com.ble1st.connectias.core.plugin.ui.ActionType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep EventType enum - Used in UIEventParcel
-keep enum com.ble1st.connectias.core.plugin.ui.EventType { *; }
-keepclassmembers enum com.ble1st.connectias.core.plugin.ui.EventType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Kotlin Parcelize data classes for UI models
-keep class com.ble1st.connectias.core.plugin.ui.UIStateParcel { *; }
-keep class com.ble1st.connectias.core.plugin.ui.UIComponentParcel { *; }
-keep class com.ble1st.connectias.core.plugin.ui.UserActionParcel { *; }
-keep class com.ble1st.connectias.core.plugin.ui.UIEventParcel { *; }

# Keep Parcelize CREATOR for Kotlin Parcelable classes
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.UIStateParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.UIComponentParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.UserActionParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.UIEventParcel {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ------------------------------------------------------------------------------
# Three-Process UI Integration Classes
# ------------------------------------------------------------------------------

# Keep PluginManagerSandbox integration methods for Three-Process UI
-keepclassmembers class com.ble1st.connectias.plugin.PluginManagerSandbox {
    *** createPluginFragmentIsolated(...);
    *** setupUIBridge(...);
    *** connectUIProcess(...);
}

# Keep PluginSandboxProxy methods for UI controller setup
-keepclassmembers class com.ble1st.connectias.core.plugin.PluginSandboxProxy {
    *** setUIController(...);
    *** getUIController(...);
}

# Keep Surface classes for VirtualDisplay rendering
-keep class android.view.Surface { *; }
-keepclassmembers class android.view.Surface {
    *** isValid();
    *** release();
}

# Keep VirtualDisplay for UI Process rendering
-keep class android.hardware.display.VirtualDisplay { *; }
-keepclassmembers class android.hardware.display.VirtualDisplay {
    *** getSurface();
    *** release();
}

# Keep DisplayManager for VirtualDisplay creation
-keep class android.hardware.display.DisplayManager { *; }
-keepclassmembers class android.hardware.display.DisplayManager {
    *** createVirtualDisplay(...);
    android.view.Display[] getDisplays();
    android.view.Display getDisplay(int);
}

# Keep Display and DisplayMetrics - Used in VirtualDisplayManager
-keep class android.view.Display { *; }
-keepclassmembers class android.view.Display {
    void getMetrics(...);
    void getRealMetrics(...);
    int getDisplayId();
}

-keep class android.util.DisplayMetrics { *; }
-keepclassmembers class android.util.DisplayMetrics {
    int widthPixels;
    int heightPixels;
    float density;
    int densityDpi;
}

# Keep ImageReader - Used in VirtualDisplayManager for touch event capture
-keep class android.media.ImageReader { *; }
-keepclassmembers class android.media.ImageReader {
    static *** newInstance(...);
    android.view.Surface getSurface();
    android.media.Image acquireLatestImage();
    void close();
}

# Keep ImageFormat - Used in VirtualDisplayManager
-keep class android.graphics.ImageFormat { *; }
-keepclassmembers class android.graphics.ImageFormat {
    static int RGB_565;
    static int RGBA_8888;
}

# Keep MotionEvent - Used for touch event handling in Three-Process UI
-keep class android.view.MotionEvent { *; }
-keepclassmembers class android.view.MotionEvent {
    static *** obtain(...);
    static int ACTION_DOWN;
    static int ACTION_UP;
    static int ACTION_MOVE;
    static int ACTION_CANCEL;
    int getAction();
    int getActionMasked();
    float getX();
    float getY();
    long getEventTime();
    long getDownTime();
    float getPressure();
    float getSize();
    int getMetaState();
    int getButtonState();
    int getDeviceId();
    int getEdgeFlags();
    int getSource();
    int getFlags();
    void recycle();
}

# Keep InputDevice - Used for touch event source
-keep class android.view.InputDevice { *; }
-keepclassmembers class android.view.InputDevice {
    static int SOURCE_TOUCHSCREEN;
    static int SOURCE_MOUSE;
    static int SOURCE_STYLUS;
}

# Keep ServiceConnection for UI Process binding
-keep interface android.content.ServiceConnection { *; }
-keepclassmembers interface android.content.ServiceConnection {
    void onServiceConnected(...);
    void onServiceDisconnected(...);
}

# ------------------------------------------------------------------------------
# ViewTree Reflection Classes - CRITICAL for PluginComposePresentation
# ------------------------------------------------------------------------------
# These classes are accessed via reflection and must not be obfuscated or removed

# Keep ViewTreeLifecycleOwner - Used via reflection in PluginComposePresentation
-keep class androidx.lifecycle.ViewTreeLifecycleOwner { *; }
-keepclassmembers class androidx.lifecycle.ViewTreeLifecycleOwner {
    static *** set(...);
    static *** get(...);
}

# Keep ViewTreeSavedStateRegistryOwner - Used via reflection (CRITICAL FIX)
-keep class androidx.savedstate.ViewTreeSavedStateRegistryOwner { *; }
-keepclassmembers class androidx.savedstate.ViewTreeSavedStateRegistryOwner {
    static *** set(...);
    static *** get(...);
}

# Keep ViewTreeViewModelStoreOwner - Used via reflection
-keep class androidx.lifecycle.ViewTreeViewModelStoreOwner { *; }
-keepclassmembers class androidx.lifecycle.ViewTreeViewModelStoreOwner {
    static *** set(...);
    static *** get(...);
}

# Keep SavedStateRegistryOwner interface - Required for ViewTreeSavedStateRegistryOwner
-keep interface androidx.savedstate.SavedStateRegistryOwner { *; }
-keep class androidx.savedstate.SavedStateRegistryOwner { *; }

# Keep SavedStateRegistry - Required for SavedStateRegistryOwner
-keep class androidx.savedstate.SavedStateRegistry { *; }
-keepclassmembers class androidx.savedstate.SavedStateRegistry {
    *;
}

# Keep SavedStateRegistryController - Used in PluginComposePresentation
-keep class androidx.savedstate.SavedStateRegistryController { *; }
-keepclassmembers class androidx.savedstate.SavedStateRegistryController {
    static *** create(...);
    *** performRestore(...);
    *** performSave(...);
}

# Keep ViewModelStore - Used in PluginComposePresentation
-keep class androidx.lifecycle.ViewModelStore { *; }
-keepclassmembers class androidx.lifecycle.ViewModelStore {
    <init>();
    *** clear();
}

# Keep ViewModelStoreOwner - Used in PluginComposePresentation
-keep interface androidx.lifecycle.ViewModelStoreOwner { *; }
-keep class androidx.lifecycle.ViewModelStoreOwner { *; }

# Keep LifecycleRegistry - Used in PluginComposePresentation
-keep class androidx.lifecycle.LifecycleRegistry { *; }
-keepclassmembers class androidx.lifecycle.LifecycleRegistry {
    <init>(...);
    *** setCurrentState(...);
    *** addObserver(...);
    *** removeObserver(...);
    *** getCurrentState();
}

# Keep PluginComposePresentation - Uses reflection for ViewTree classes
-keep class com.ble1st.connectias.core.plugin.ui.PluginComposePresentation { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.PluginComposePresentation {
    *** setViewTreeOwners(...);
    <init>(...);
}

# Keep PluginPresentation - Alternative Presentation implementation
-keep class com.ble1st.connectias.core.plugin.ui.PluginPresentation { *; }
-keepclassmembers class com.ble1st.connectias.core.plugin.ui.PluginPresentation {
    <init>(...);
}

# Keep Presentation base class - Used by PluginComposePresentation and PluginPresentation
-keep class android.app.Presentation { *; }
-keepclassmembers class android.app.Presentation {
    <init>(...);
    void show();
    void dismiss();
    android.view.Display getDisplay();
}

# Keep Fragment reflection fields - Used in PluginComposePresentation
-keepclassmembers class androidx.fragment.app.Fragment {
    android.fragment.app.FragmentHostCallback mHost;
    int mState;
}

# Keep FragmentHostCallback - Used via reflection
-keep class androidx.fragment.app.FragmentHostCallback { *; }
-keepclassmembers class androidx.fragment.app.FragmentHostCallback {
    <init>(...);
    *** onGetHost();
    *** onGetLayoutInflater();
}

# Keep Class.getDeclaredField and getMethod for reflection
-keepclassmembers class java.lang.Class {
    java.lang.reflect.Field getDeclaredField(java.lang.String);
    java.lang.reflect.Method getMethod(java.lang.String, java.lang.Class...);
    java.lang.reflect.Method getDeclaredMethod(java.lang.String, java.lang.Class...);
    java.lang.reflect.Constructor getConstructor(java.lang.Class...);
    java.lang.reflect.Constructor getDeclaredConstructor(java.lang.Class...);
}

# Keep Reflection classes used throughout the app
-keep class java.lang.reflect.Method { *; }
-keep class java.lang.reflect.Field { *; }
-keepclassmembers class java.lang.reflect.Method {
    java.lang.Object invoke(...);
    void setAccessible(boolean);
}
-keepclassmembers class java.lang.reflect.Field {
    java.lang.Object get(...);
    void set(...);
    void setAccessible(boolean);
}

# ------------------------------------------------------------------------------
# ActivityThread Reflection - Used in ConnectiasApplication
# ------------------------------------------------------------------------------
# Keep ActivityThread for reflection access (used to get current activity)
-keep class android.app.ActivityThread { *; }
-keepclassmembers class android.app.ActivityThread {
    static *** currentActivityThread();
    java.util.Map mActivities;
}

# Keep ActivityClientRecord for reflection access
-keep class android.app.ActivityThread$ActivityClientRecord { *; }
-keepclassmembers class android.app.ActivityThread$ActivityClientRecord {
    android.app.Activity activity;
}

# ------------------------------------------------------------------------------
# DexFile Reflection - Used in PluginSandboxService for DEX scanning
# ------------------------------------------------------------------------------
# Keep DexFile.openInMemory method (used via reflection)
-keepclassmembers class dalvik.system.DexFile {
    static *** openInMemory(...);
    java.util.Enumeration entries();
}

# Keep RestrictedClassLoader dexBuffers field (accessed via reflection)
-keepclassmembers class com.ble1st.connectias.core.plugin.security.RestrictedClassLoader {
    java.nio.ByteBuffer[] dexBuffers;
}

# ------------------------------------------------------------------------------
# SQLCipher Reflection - Used in DatabaseModule
# ------------------------------------------------------------------------------
# Keep SQLCipher SupportFactory classes (accessed via reflection)
-keep class net.zetetic.database.sqlcipher.SupportOpenHelperFactory { *; }
-keep class net.sqlcipher.database.SupportFactory { *; }
-keepclassmembers class net.zetetic.database.sqlcipher.SupportOpenHelperFactory {
    <init>(byte[]);
}
-keepclassmembers class net.sqlcipher.database.SupportFactory {
    <init>(byte[]);
}

# ------------------------------------------------------------------------------
# Bluetooth Reflection - Used in BluetoothBridge
# ------------------------------------------------------------------------------
# Keep BluetoothSocket for reflection access (getFileDescriptor method)
-keep class android.bluetooth.BluetoothSocket { *; }
-keepclassmembers class android.bluetooth.BluetoothSocket {
    java.io.FileDescriptor getFileDescriptor();
}

# ------------------------------------------------------------------------------
# SharedMemory Reflection - Used in CameraBridge
# ------------------------------------------------------------------------------
# Keep SharedMemory for reflection access (getFd method)
-keep class android.os.SharedMemory { *; }
-keepclassmembers class android.os.SharedMemory {
    int getFd();
    static *** create(...);
    *** mapReadOnly();
    *** mapReadWrite();
    boolean close();
}