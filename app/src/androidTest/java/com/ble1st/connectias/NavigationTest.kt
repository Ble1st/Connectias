package com.ble1st.connectias

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ble1st.connectias.core.module.ModuleCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for navigation and module discovery.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @Test
    fun testModuleCatalogContainsAllModules() {
        val allModules = ModuleCatalog.ALL_MODULES
        
        assertTrue("Should have at least core modules", allModules.isNotEmpty())
        
        val coreModules = ModuleCatalog.CORE_MODULES
        assertTrue("Should have at least one core module", coreModules.isNotEmpty())
        
        val securityModule = ModuleCatalog.findById("security")
        assertNotNull("Security module should exist", securityModule)
        assertTrue("Security module should be core", securityModule?.isCore == true)
    }

    @Test
    fun testModuleCatalogFindById() {
        val utilitiesModule = ModuleCatalog.findById("utilities")
        val backupModule = ModuleCatalog.findById("backup")
        val networkModule = ModuleCatalog.findById("network")
        
        assertNotNull("Utilities module should exist", utilitiesModule)
        assertNotNull("Backup module should exist", backupModule)
        assertNotNull("Network module should exist", networkModule)
    }

    @Test
    fun testModuleCatalogGetByCategory() {
        val utilityModules = ModuleCatalog.getByCategory(ModuleCatalog.ModuleCategory.UTILITY)
        val securityModules = ModuleCatalog.getByCategory(ModuleCatalog.ModuleCategory.SECURITY)
        
        assertTrue("Should have utility modules", utilityModules.isNotEmpty())
        assertTrue("Should have security modules", securityModules.isNotEmpty())
    }

    @Test
    fun testAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.ble1st.connectias", appContext.packageName)
    }
}

