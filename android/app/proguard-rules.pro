# Keep Flutter & app classes
-keep class io.flutter.** { *; }
-keep class com.connectias.** { *; }
-dontwarn io.flutter.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Dart FFI symbols
-keep class dart.** { *; }

# Obfuscate remaining classes
-repackageclasses ''
-allowaccessmodification

