# Keep barcode related classes
-keep class com.ble1st.connectias.feature.barcode.** { *; }

# ZXing (QR Code)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**