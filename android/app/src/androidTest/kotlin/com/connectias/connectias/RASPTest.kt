package com.connectias.connectias

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.connectias.connectias.security.RASPDetector
import com.connectias.connectias.security.EnhancedRASPDetector
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class RASPTest {
    
    @Test
    fun testRootDetection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rasp = RASPDetector(context)
        val result = rasp.detectRoot()
        
        // In einem normalen Test-Environment sollte Root nicht erkannt werden
        // (außer auf gerooteten Geräten)
        assertFalse("Root sollte nicht erkannt werden in normalem Test-Environment", result)
    }
    
    @Test
    fun testDebuggerDetection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rasp = RASPDetector(context)
        val result = rasp.detectDebugger()
        
        // Debugger sollte erkannt werden wenn Tests laufen
        assertTrue("Debugger sollte erkannt werden während Tests", result)
    }
    
    @Test
    fun testEmulatorDetection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rasp = RASPDetector(context)
        val result = rasp.detectEmulator()
        
        // Emulator-Erkennung hängt vom Test-Environment ab
        // Teste nur, dass die Methode ohne Crash läuft
        assertNotNull("Emulator-Detection sollte ein Boolean zurückgeben", result)
    }
    
    @Test
    fun testIntegrityCheck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rasp = RASPDetector(context)
        val result = rasp.checkIntegrity()
        
        // Integrity sollte in normalem Environment true sein
        assertTrue("App-Integrität sollte in normalem Environment OK sein", result)
    }
    
    @Test
    fun testEnhancedRASP() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enhanced = EnhancedRASPDetector(context)
        val result = enhanced.performCheck()
        
        // Prüfe dass alle Details vorhanden sind
        assertTrue("Security-Check sollte Details enthalten", result.details.isNotEmpty())
        assertTrue("Details sollten 'rooted' enthalten", result.details.containsKey("rooted"))
        assertTrue("Details sollten 'debugged' enthalten", result.details.containsKey("debugged"))
        assertTrue("Details sollten 'emulator' enthalten", result.details.containsKey("emulator"))
        assertTrue("Details sollten 'integrity' enthalten", result.details.containsKey("integrity"))
        
        // Debugger sollte erkannt werden (da Tests laufen)
        assertTrue("Debugger sollte während Tests erkannt werden", 
                  result.details["debugged"] == true)
    }
    
    @Test
    fun testSecurityResultStructure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enhanced = EnhancedRASPDetector(context)
        val result = enhanced.performCheck()
        
        // Prüfe Struktur des SecurityResult
        assertNotNull("SecurityResult sollte nicht null sein", result)
        assertNotNull("passed sollte nicht null sein", result.passed)
        assertNotNull("details sollte nicht null sein", result.details)
        
        // Prüfe dass alle erwarteten Keys vorhanden sind
        val expectedKeys = setOf("rooted", "debugged", "emulator", "integrity")
        assertEquals("Details sollten alle erwarteten Keys enthalten", 
                    expectedKeys, result.details.keys)
    }
}
