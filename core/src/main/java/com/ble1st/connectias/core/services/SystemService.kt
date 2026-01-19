package com.ble1st.connectias.core.services

import android.content.Context
import android.os.Build
import java.util.Locale
import javax.inject.Singleton

@Singleton
class SystemService(
    private val context: Context
) {
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    fun getApiLevel(): Int = Build.VERSION.SDK_INT

    fun getAppVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun getLocaleTag(): String = Locale.getDefault().toLanguageTag()
}

