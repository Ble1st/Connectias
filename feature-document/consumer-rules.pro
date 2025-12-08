## Keep Tesseract and OpenCV JNI entry points
-keep class com.googlecode.tesseract.android.** { *; }
-keep class org.opencv.** { *; }

## Prevent stripping native methods used via reflection
-keepclasseswithmembernames class * {
    native <methods>;
}
