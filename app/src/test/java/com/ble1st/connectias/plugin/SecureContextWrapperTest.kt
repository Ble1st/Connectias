package com.ble1st.connectias.plugin

import android.content.Context
import android.content.pm.PackageManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

@RunWith(MockitoJUnitRunner::class)
class SecureContextWrapperTest {
    
    @Mock
    private lateinit var baseContext: Context
    
    @Mock
    private lateinit var permissionManager: PluginPermissionManager
    
    private lateinit var secureContext: SecureContextWrapper
    
    private val pluginId = "test_plugin"
    
    @Before
    fun setup() {
        // Mock getSystemService
        `when`(baseContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(null)
        `when`(baseContext.getExternalFilesDir(null)).thenReturn(null)
        
        secureContext = SecureContextWrapper(baseContext, pluginId, permissionManager)
    }
    
    @Test
    fun `test getSystemService allows access with permission`() {
        `when`(permissionManager.isPermissionAllowed(pluginId, android.Manifest.permission.INTERNET)).thenReturn(true)
        
        // Should not throw exception
        secureContext.getSystemService(Context.CONNECTIVITY_SERVICE)
        
        // Verify permission was checked
        verify(permissionManager).isPermissionAllowed(pluginId, android.Manifest.permission.INTERNET)
    }
    
    @Test(expected = SecurityException::class)
    fun `test getSystemService blocks access without permission`() {
        `when`(permissionManager.isPermissionAllowed(pluginId, android.Manifest.permission.INTERNET)).thenReturn(false)
        
        // Should throw SecurityException
        secureContext.getSystemService(Context.CONNECTIVITY_SERVICE)
    }
    
    @Test
    fun `test checkSelfPermission delegates to permission manager`() {
        `when`(permissionManager.isPermissionAllowed(pluginId, "android.permission.CAMERA"))
            .thenReturn(true)
        
        val result = secureContext.checkSelfPermission("android.permission.CAMERA")
        
        assertEquals(PackageManager.PERMISSION_GRANTED, result)
        verify(permissionManager).isPermissionAllowed(pluginId, "android.permission.CAMERA")
    }
    
    @Test
    fun `test checkSelfPermission returns denied when not allowed`() {
        `when`(permissionManager.isPermissionAllowed(pluginId, "android.permission.CAMERA"))
            .thenReturn(false)
        
        val result = secureContext.checkSelfPermission("android.permission.CAMERA")
        
        assertEquals(PackageManager.PERMISSION_DENIED, result)
    }
    
    @Test(expected = SecurityException::class)
    fun `test getExternalFilesDir blocks without storage permission`() {
        `when`(permissionManager.isPermissionAllowed(pluginId, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)).thenReturn(false)
        
        // Should throw SecurityException
        secureContext.getExternalFilesDir(null)
    }
}
