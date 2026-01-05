# Keep model classes used by serialization or reflection (placeholder).
-keep class com.ble1st.connectias.feature.dnstools.** { *; }

# dnsjava
-keep class org.xbill.DNS.** { *; }
-dontwarn org.xbill.DNS.**