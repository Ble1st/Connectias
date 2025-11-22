# Keep privacy feature module public API classes
# Fragment entry point
-keep,allowobfuscation,allowshrinking class com.ble1st.connectias.feature.privacy.ui.PrivacyDashboardFragment {
    <init>(...);
    public *;
}

# ViewModel entry point
-keep,allowobfuscation,allowshrinking class com.ble1st.connectias.feature.privacy.ui.PrivacyDashboardViewModel {
    <init>(...);
    public *;
}

# Repository public API
-keep,allowobfuscation,allowshrinking class com.ble1st.connectias.feature.privacy.repository.PrivacyRepository {
    <init>(...);
    public *** get*(...);
    public *** refreshAll();
}
# Model classes (Parcelable - used for data transfer)
-keep,allowobfuscation class com.ble1st.connectias.feature.privacy.models.** {
    <init>(...);
    <fields>;
}

# Keep enum classes for serialization
# Keep enum classes for serialization
-keep class com.ble1st.connectias.feature.privacy.models.PrivacyLevel { *; }
-keep class com.ble1st.connectias.feature.privacy.models.LocationPermissionLevel { *; }
-keep class com.ble1st.connectias.feature.privacy.models.PermissionRiskLevel { *; }
-keep class com.ble1st.connectias.feature.privacy.models.DNSStatus { *; }
-keep class com.ble1st.connectias.feature.privacy.models.NetworkType { *; }