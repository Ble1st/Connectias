package com.connectias.connectias.security

import android.content.Context
import java.io.File

class RASPDetector(private val context: Context) {
    
    fun detectRoot(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }
    
    fun detectDebugger(): Boolean {
        return android.os.Debug.isDebuggerConnected()
    }
    
    fun detectEmulator(): Boolean {
        val brand = android.os.Build.BRAND
        val device = android.os.Build.DEVICE
        val model = android.os.Build.MODEL
        val product = android.os.Build.PRODUCT
        
        return (brand.startsWith("generic") && device.startsWith("generic")) ||
               "google_sdk" == product ||
               model.contains("Emulator") ||
               model.contains("Android SDK")
    }
    
    fun checkIntegrity(): Boolean {
        // Package-Signatur-Prüfung
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                // API 28+ - verwende GET_SIGNING_CERTIFICATES
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                // API < 28 - verwende deprecated GET_SIGNATURES als Fallback
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
            }
            
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                // Neue API - verwende signingInfo
                val signingInfo = packageInfo.signingInfo
                signingInfo != null && (
                    signingInfo.apkContentsSigners.isNotEmpty() ||
                    signingInfo.signingCertificateHistory.isNotEmpty()
                )
            } else {
                // Alte API - verwende signatures
                val signatures = packageInfo.signatures
                signatures != null && signatures.isNotEmpty()
            }
        } catch (e: Exception) {
            return false
        }
    }
}
