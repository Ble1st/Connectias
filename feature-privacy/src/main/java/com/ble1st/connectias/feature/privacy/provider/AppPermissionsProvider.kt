package com.ble1st.connectias.feature.privacy.provider

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.ble1st.connectias.feature.privacy.models.AppPermissionInfo
import com.ble1st.connectias.feature.privacy.models.PermissionDetail
import com.ble1st.connectias.feature.privacy.models.PermissionRiskLevel
import com.ble1st.connectias.feature.privacy.models.SpecialPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides app permission information including runtime and special permissions.
 */
@Singleton
class AppPermissionsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager by lazy {
        context.packageManager
    }
    
    private val appOpsManager: AppOpsManager? by lazy {
        context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
    }

    /**
     * Gets app permission information for all installed apps.
     * This method performs blocking I/O and should be called from a background thread.
     */
    suspend fun getAppPermissionsInfo(): List<AppPermissionInfo> = withContext(Dispatchers.IO) {
        try {
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong()
                ))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }

            installedPackages
                .filter { it.requestedPermissions != null }
                .mapNotNull { packageInfo ->
                    try {
                        createAppPermissionInfo(packageInfo)
                    } catch (e: Exception) {
                        Timber.w(e, "Error processing app ${packageInfo.packageName}")
                        null
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Error getting app permissions info")
            emptyList()
        }
    }

    private fun createAppPermissionInfo(packageInfo: PackageInfo): AppPermissionInfo {
        val packageName = packageInfo.packageName
        val appInfo = packageInfo.applicationInfo ?: return AppPermissionInfo(
            packageName = packageName,
            appName = packageName,
            runtimePermissions = emptyList(),
            installTimePermissions = emptyList(),
            specialPermissions = emptyList(),
            riskLevel = PermissionRiskLevel.LOW
        )
        val appName = packageManager.getApplicationLabel(appInfo).toString()

        val runtimePermissions = mutableListOf<PermissionDetail>()
        val installTimePermissions = mutableListOf<String>()

        val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
        requestedPermissions.forEach { permission ->
            try {
                val permissionInfo = packageManager.getPermissionInfo(permission, 0)
                val isDangerous = (permissionInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS                val isGranted = packageInfo.requestedPermissionsFlags?.let { flags ->
                    val index = requestedPermissions.indexOf(permission)
                    if (index >= 0 && index < flags.size) {
                        (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    } else {
                        false
                    }
                } ?: false

                if (isDangerous) {
                    runtimePermissions.add(
                        PermissionDetail(
                            permission = permission,
                            granted = isGranted,
                            isDangerous = true
                        )
                    )
                } else {
                    installTimePermissions.add(permission)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Permission not found, skip
            } catch (e: Exception) {
                Timber.w(e, "Error processing permission $permission")
            }
        }

        val specialPermissions = getSpecialPermissions(packageInfo)
        val riskLevel = calculateRiskLevel(runtimePermissions, specialPermissions)

        return AppPermissionInfo(
            packageName = packageName,
            appName = appName,
            runtimePermissions = runtimePermissions,
            installTimePermissions = installTimePermissions,
            specialPermissions = specialPermissions,
            riskLevel = riskLevel
        )
    }

    private fun getSpecialPermissions(packageInfo: PackageInfo): List<SpecialPermission> {
        val specialPermissions = mutableListOf<SpecialPermission>()

        // Check SYSTEM_ALERT_WINDOW per package using AppOpsManager
        try {
            val appInfo = packageInfo.applicationInfo ?: return specialPermissions
            
            // Check if the manifest declares SYSTEM_ALERT_WINDOW permission
            val hasPermissionDeclared = packageInfo.requestedPermissions?.contains(
                android.Manifest.permission.SYSTEM_ALERT_WINDOW
            ) == true
            
            if (!hasPermissionDeclared) {
                return specialPermissions
            }
            
            val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val appOpsMgr = appOpsManager ?: return specialPermissions
                val uid = appInfo.uid
                val opString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW
                } else {
                    "android:system_alert_window"
                }
                
                try {
                    val mode = @Suppress("DEPRECATION")
                    appOpsMgr.checkOpNoThrow(opString, uid, packageInfo.packageName)
                    mode == AppOpsManager.MODE_ALLOWED
                } catch (e: Exception) {
                    Timber.w(e, "Error checking overlay permission for ${packageInfo.packageName}")
                    false
                }
            } else {
                false
            }
            
            specialPermissions.add(
                SpecialPermission(
                    permission = android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                    granted = canDrawOverlays
                )
            )
        } catch (e: Exception) {
            Timber.w(e, "Error getting special permissions for ${packageInfo.packageName}")
        }

        return specialPermissions
    }

    private fun calculateRiskLevel(
        runtimePermissions: List<PermissionDetail>,
        specialPermissions: List<SpecialPermission>
    ): PermissionRiskLevel {
        val grantedDangerousPermissions = runtimePermissions.count { it.granted && it.isDangerous }
        val grantedSpecialPermissions = specialPermissions.count { it.granted }

        return when {
            grantedDangerousPermissions >= 5 || grantedSpecialPermissions >= 2 -> PermissionRiskLevel.HIGH
            grantedDangerousPermissions >= 2 || grantedSpecialPermissions >= 1 -> PermissionRiskLevel.MEDIUM
            else -> PermissionRiskLevel.LOW
        }
    }
}

