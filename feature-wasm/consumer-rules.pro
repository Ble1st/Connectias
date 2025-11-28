# ProGuard rules for feature-wasm module
# Keep WASM-related classes
-keep class com.ble1st.connectias.feature.wasm.** { *; }

# Keep plugin metadata classes
-keep class * implements kotlinx.serialization.KSerializable { *; }

