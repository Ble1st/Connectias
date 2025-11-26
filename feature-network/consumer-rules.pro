# Keep network feature module public API classes
# Fragment entry point
-keep class com.ble1st.connectias.feature.network.ui.NetworkDashboardFragment {
    <init>(...);
    public *;
}
# ViewModel entry point
-keep,allowobfuscation,allowshrinking class com.ble1st.connectias.feature.network.ui.NetworkDashboardViewModel {
-keep class com.ble1st.connectias.feature.network.ui.NetworkDashboardViewModel {
    <init>(...);
    public *;
}# Repository public API
-keep class com.ble1st.connectias.feature.network.repository.NetworkRepository {
    <init>(...);
    public *** get*(...);
    public *** refreshAll();
}
# Model classes (Parcelable - used for data transfer)
-keep,allowobfuscation class com.ble1st.connectias.feature.network.models.** {
    <init>(...);
    <fields>;
}

