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
    public suspend fun get*(...);
    public fun refreshAll();
}

# Model classes (Parcelable - used for data transfer)
-keep,allowobfuscation class com.ble1st.connectias.feature.privacy.models.** {
    <init>(...);
    <fields>;
}

# Keep enum classes for serialization
-keep,allowobfuscation class com.ble1st.connectias.feature.privacy.models.PrivacyLevel { *; }
-keep,allowobfuscation class com.ble1st.connectias.feature.privacy.models.LocationPermissionLevel { *; }
-keep,allowobfuscation class com.ble1st.connectias.feature.privacy.models.PermissionRiskLevel { *; }
-keep,allowobfuscation class com.ble1st.connectias.feature.privacy.models.DNSStatus { *; }
-keep,allowobfuscation class com.ble1st.connectias.feature.privacy.models.NetworkType { *; }

