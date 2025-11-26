package com.ble1st.connectias.feature.privacy.provider

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.ble1st.connectias.feature.privacy.models.StorageAccess
import com.ble1st.connectias.feature.privacy.models.StoragePrivacyInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides storage privacy information including scoped storage status.
 */
@Singleton
class StoragePrivacyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager by lazy {
        context.packageManager
    }

    /**
     * Gets storage privacy information.
     * This method performs blocking I/O and should be called from a background thread.
     */
    suspend fun getStoragePrivacyInfo(): StoragePrivacyInfo = withContext(Dispatchers.IO) {
        try {
            val scopedStorageEnabled = isScopedStorageEnabled()
            val legacyStorageMode = isLegacyStorageMode()
            val appsWithStorageAccess = getAppsWithStorageAccess()
            val mediaStoreAccessEnabled = isMediaStoreAccessEnabled()

            StoragePrivacyInfo(
                scopedStorageEnabled = scopedStorageEnabled,
                legacyStorageMode = legacyStorageMode,
                appsWithStorageAccess = appsWithStorageAccess,
                mediaStoreAccessEnabled = mediaStoreAccessEnabled
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting storage privacy info")
            val isAndroidQPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            StoragePrivacyInfo(
                scopedStorageEnabled = isAndroidQPlus,
                legacyStorageMode = false,
                appsWithStorageAccess = emptyList(),
                mediaStoreAccessEnabled = isAndroidQPlus
            )
        }    }

    private fun isScopedStorageEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage is enforced on Android 10+
            true
        } else {
            false
        }
    }

    private fun isLegacyStorageMode(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Environment.isExternalStorageLegacy()
            } else {
                // Pre-Android 10 always uses legacy storage
                true
            }
        } catch (e: Exception) {
            Timber.w(e, "Error checking legacy storage mode")
            false
        }
    }

    private fun isMediaStoreAccessEnabled(): Boolean {
        // MediaStore access is available on Android 10+ with scoped storage
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private fun getAppsWithStorageAccess(): List<StorageAccess> {
        return try {
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
                        val hasReadStorage = hasStoragePermission(
                            packageInfo,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        ) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            (hasStoragePermission(packageInfo, android.Manifest.permission.READ_MEDIA_IMAGES) ||
                            hasStoragePermission(packageInfo, android.Manifest.permission.READ_MEDIA_VIDEO) ||
                            hasStoragePermission(packageInfo, android.Manifest.permission.READ_MEDIA_AUDIO)))

                        val hasWriteStorage = hasStoragePermission(
                            packageInfo,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )

                        if (hasReadStorage || hasWriteStorage) {
                            val appInfo = packageInfo.applicationInfo
                            val appName = appInfo?.let { packageManager.getApplicationLabel(it).toString() }
                                ?: packageInfo.packageName
                            
                            StorageAccess(
                                packageName = packageInfo.packageName,
                                appName = appName,
                                hasReadStorage = hasReadStorage,
                                hasWriteStorage = hasWriteStorage,
                                hasMediaAccess = hasReadStorage || hasWriteStorage
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Error processing package ${packageInfo.packageName} for storage access")
                        null
                    }
                }
        } catch (e: android.os.BadParcelableException) {
            Timber.e(e, "Binder transaction failed: too many packages. Returning empty list.")
            emptyList()
        } catch (e: android.os.DeadSystemException) {
            Timber.e(e, "Binder transaction failed: system is dead. Returning empty list.")
            emptyList()
        } catch (e: android.os.DeadObjectException) {
            Timber.e(e, "Binder transaction failed: remote process died or buffer full. Returning empty list.")
            emptyList()
        } catch (e: RuntimeException) {
            // Catch DeadSystemRuntimeException and other runtime exceptions
            if (e.cause is android.os.DeadSystemException) {
                Timber.e(e, "Binder transaction failed: system runtime is dead. Returning empty list.")
            } else {
                Timber.e(e, "Runtime exception during binder transaction. Returning empty list.")
            }
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error getting apps with storage access")
            emptyList()
        }
    }

    private fun hasStoragePermission(packageInfo: PackageInfo, permission: String): Boolean {
        val requestedPermissions = packageInfo.requestedPermissions ?: return false
        if (!requestedPermissions.contains(permission)) {
            return false
        }

        val index = requestedPermissions.indexOf(permission)
        val flags = packageInfo.requestedPermissionsFlags ?: return false

        return if (index >= 0 && index < flags.size) {
            (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        } else {
            false
        }
    }
}

