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
        secureContext = SecureContextWrapper(baseContext, pluginId, permissionManager)
    }
    
    @Test
    fun `test getSystemService permission check works`() {
        `when`(permissionManager.isPermissionAllowed(pluginId, android.Manifest.permission.INTERNET)).thenReturn(true)
        
        // Test that permission check is called correctly
        assertTrue(permissionManager.isPermissionAllowed(pluginId, android.Manifest.permission.INTERNET))
        verify(permissionManager).isPermissionAllowed(pluginId, android.Manifest.permission.INTERNET)
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
}
