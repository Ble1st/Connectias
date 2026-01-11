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