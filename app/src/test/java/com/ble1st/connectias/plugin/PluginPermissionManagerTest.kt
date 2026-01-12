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
    
    private lateinit var permissionManager: PluginPermissionManager
    
    @Before
    fun setup() {
        // Mock SharedPreferences
        val sharedPrefs = mock(android.content.SharedPreferences::class.java)
        val editor = mock(android.content.SharedPreferences.Editor::class.java)
        
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPrefs)
        `when`(sharedPrefs.edit()).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor)
        `when`(editor.remove(anyString())).thenReturn(editor)
        `when`(sharedPrefs.getBoolean(anyString(), anyBoolean())).thenReturn(false)
        
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
                "android.permission.INTERNET"
            )
        )
        
        assertTrue(critical.contains("android.permission.INSTALL_PACKAGES"))
        assertTrue(critical.contains("android.permission.REBOOT"))
        assertFalse(critical.contains("android.permission.INTERNET"))
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
        assertTrue(validation.isValid)
        assertFalse(validation.requiresUserConsent)
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
        val sharedPrefs = context.getSharedPreferences("plugin_permissions", Context.MODE_PRIVATE)
        verify(sharedPrefs.edit(), times(permissions.size)).putBoolean(anyString(), eq(true))
    }
    
    @Test
    fun `test isPermissionAllowed blocks critical permissions`() {
        val result = permissionManager.isPermissionAllowed("test", "android.permission.REBOOT")
        assertFalse(result)
    }
    
    @Test
    fun `test isPermissionAllowed allows normal permissions`() {
        val result = permissionManager.isPermissionAllowed("test", "android.permission.VIBRATE")
        assertTrue(result)
    }
}
