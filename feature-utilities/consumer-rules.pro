# Keep utilities feature module public API classes
# Fragment entry points
-keep class com.ble1st.connectias.feature.utilities.ui.** {
    <init>(...);
    public *;
}
# ViewModel entry points
-keep,allowobfuscation,allowshrinking class com.ble1st.connectias.feature.utilities.**.ViewModel {
-keep class com.ble1st.connectias.feature.utilities.**.ViewModel {
    <init>(...);
    public *;
}
# Provider public API
-keep class com.ble1st.connectias.feature.utilities.**.Provider {
    <init>(...);
    public *** get*(...);
    public *** process*(...);
}
# Model classes (Parcelable - used for data transfer)
-keep,allowobfuscation class com.ble1st.connectias.feature.utilities.models.** {
    <init>(...);
    <fields>;
}

