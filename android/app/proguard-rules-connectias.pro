# Connectias ProGuard Rules – Production Build Optimization

# ============================================================================
# KEEP Connectias Native Code (FFI)
# ============================================================================

# Erhalte alle Connectias FFI Funktionen
-keep class com.connectias.** { *; }
-keepclassmembers class com.connectias.** { *; }

# Rust FFI kann nicht obfuskiert werden
-keepnames class com.connectias.connectias.** { *; }

# ============================================================================
# SECURITY: Entferne Debug Info in Production
# ============================================================================

# Entferne Logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ============================================================================
# FLUTTER & DART VM
# ============================================================================

-keep class io.flutter.** { *; }
-keep class com.google.android.material.** { *; }

# ============================================================================
# SECURITY LIBRARIES
# ============================================================================

# BoringSSL / TLS
-keep class com.android.org.conscrypt.** { *; }
-keepclassmembers class com.android.org.conscrypt.** { *; }

# Keystore
-keep class android.security.keystore.** { *; }
-keepclassmembers class android.security.keystore.** { *; }

# ============================================================================
# OPTIMIZATION
# ============================================================================

-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Optimiere String Konstanten
-optimizations !code/simplification/arithmetic,!code/simplification/cast

# ============================================================================
# OBFUSCATION
# ============================================================================

# Ändere Klassen/Methoden Namen (außer native)
-repackageclasses 'com.connectias.obf'

# Entferne Quellzeilen
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Keep Exceptions
-keepattributes Exceptions,Signature

# ============================================================================
# FINAL RULES
# ============================================================================

# Behalte alles von Android Framework
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.content.ContentProvider { *; }

# Generics
-keepattributes Signature
-keepattributes InnerClasses
