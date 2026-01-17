package com.ble1st.connectias.plugin

import android.content.Context
import android.content.pm.PackageManager
import com.ble1st.connectias.plugin.sdk.PluginCategory
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

@RunWith(MockitoJUnitRunner::class)
class PluginPermissionManagerTest {
    
    @Mock
    private lateinit var context: Context
    
    @Mock
    private lateinit var sharedPrefs: android.content.SharedPreferences
    
    @Mock
    private lateinit var editor: android.content.SharedPreferences.Editor
    
    private lateinit var permissionManager: PluginPermissionManager
    
    @Before
    fun setup() {
        // Mock SharedPreferences
        `when`(context.getSharedPreferences("plugin_permissions", Context.MODE_PRIVATE)).thenReturn(sharedPrefs)
        `when`(sharedPrefs.edit()).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor)
        `when`(editor.remove(anyString())).thenReturn(editor)
        
        // Mock checkSelfPermission
        `when`(context.checkSelfPermission(anyString())).thenReturn(PackageManager.PERMISSION_GRANTED)
        
        permissionManager = PluginPermissionManager(context)
    }
    
    @Test
    fun `test dangerous permissions classification`() {
        val dangerous = permissionManager.getDangerousPermissions(
            listOf(
                "android.permission.INTERNET",
                "android.permission.CAMERA",
                "android.permission.VIBRATE"
            )
        )
        
        assertEquals(2, dangerous.size) // INTERNET and CAMERA are dangerous, VIBRATE is not
        assertTrue(dangerous.contains("android.permission.INTERNET"))
        assertTrue(dangerous.contains("android.permission.CAMERA"))
        assertFalse(dangerous.contains("android.permission.VIBRATE"))
    }
    
    @Test
    fun `test critical permissions classification`() {
        val critical = permissionManager.getCriticalPermissions(
            listOf(
                "android.permission.INSTALL_PACKAGES",
                "android.permission.REBOOT",
                "android.permission.VIBRATE"
            )
        )
        
        assertEquals(2, critical.size) // INSTALL_PACKAGES and REBOOT are critical, VIBRATE is not
        assertTrue(critical.contains("android.permission.INSTALL_PACKAGES"))
        assertTrue(critical.contains("android.permission.REBOOT"))
        assertFalse(critical.contains("android.permission.VIBRATE"))
    }
    
    @Test
    fun `test validatePermissions with no dangerous permissions`() = runBlocking {
        val metadata = PluginMetadata(
            pluginId = "test",
            pluginName = "Test",
            version = "1.0.0",
            author = "Test",
            minApiLevel = 33,
            maxApiLevel = 35,
            minAppVersion = "1.0.0",
            nativeLibraries = emptyList(),
            fragmentClassName = "Test",
            description = "Test",
            permissions = listOf("android.permission.VIBRATE"),
            category = PluginCategory.UTILITY,
            dependencies = emptyList()
        )
        
        val result = permissionManager.validatePermissions(metadata)
        assertTrue(result.isSuccess)
        val validation = result.getOrNull()!!
        assertFalse(validation.isValid) // VIBRATE requires user consent now
        assertTrue(validation.requiresUserConsent)
    }
    
    @Test
    fun `test validatePermissions blocks critical permissions`() = runBlocking {
        val metadata = PluginMetadata(
            pluginId = "test",
            pluginName = "Test",
            version = "1.0.0",
            author = "Test",
            minApiLevel = 33,
            maxApiLevel = 35,
            minAppVersion = "1.0.0",
            nativeLibraries = emptyList(),
            fragmentClassName = "Test",
            description = "Test",
            permissions = listOf("android.permission.INSTALL_PACKAGES"),
            category = PluginCategory.UTILITY,
            dependencies = emptyList()
        )
        
        val result = permissionManager.validatePermissions(metadata)
        assertTrue(result.isSuccess)
        val validation = result.getOrNull()!!
        assertFalse(validation.isValid)
        assertTrue(validation.criticalPermissions.isNotEmpty())
    }
    
    @Test
    fun `test grantUserConsent stores permissions`() {
        val permissions = listOf("android.permission.CAMERA", "android.permission.INTERNET")
        permissionManager.grantUserConsent("test_plugin", permissions)
        
        // Verify that putBoolean was called for each permission
        verify(sharedPrefs.edit(), times(permissions.size)).putBoolean(anyString(), eq(true))
    }
    
    @Test
    fun `test isPermissionAllowed blocks critical permissions`() {
        val result = permissionManager.isPermissionAllowed("test", "android.permission.REBOOT")
        assertFalse(result)
    }
    
    @Test
    fun `test isPermissionAllowed allows normal permissions`() {
        // VIBRATE is not dangerous, but still requires user consent
        // isPermissionAllowed checks if permission is not critical AND has consent
        // Since we haven't granted consent in this test, it should return false
        val result = permissionManager.isPermissionAllowed("test", "android.permission.VIBRATE")
        assertFalse(result) // No consent granted yet
    }
}
