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
    }

    @Test
    fun testModuleCatalogFindById() {
        val utilitiesModule = ModuleCatalog.findById("utilities")
        val backupModule = ModuleCatalog.findById("backup")
        
        assertNotNull("Utilities module should exist", utilitiesModule)
        assertNotNull("Backup module should exist", backupModule)
    }

    @Test
    fun testModuleCatalogGetByCategory() {
        val utilityModules = ModuleCatalog.getByCategory(ModuleCatalog.ModuleCategory.UTILITY)
        
        assertTrue("Should have utility modules", utilityModules.isNotEmpty())
    }

    @Test
    fun testAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.ble1st.connectias", appContext.packageName)
    }
}

