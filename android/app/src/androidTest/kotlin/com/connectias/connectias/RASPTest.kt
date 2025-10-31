package com.connectias.connectias

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.connectias.connectias.security.RASPDetector
import com.connectias.connectias.security.EnhancedRASPDetector
import android.os.Debug
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
        
        // Vergleiche mit tatsächlichem Debugger-Status
        val actualDebuggerState = Debug.isDebuggerConnected()
        assertEquals("RASPDetector sollte Debugger-Status korrekt erkennen", 
                   actualDebuggerState, result)
    }
    
    @Test
    fun testEmulatorDetection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rasp = RASPDetector(context)
        val result = rasp.detectEmulator()
        
        // Stelle sicher, dass ein Boolean-Wert zurückgegeben wird
        assertTrue("detectEmulator sollte Boolean zurückgeben", 
                  result == true || result == false)
        
        // Basierend auf Build-Properties sollte das Ergebnis deterministisch sein
        val brand = android.os.Build.BRAND
        val device = android.os.Build.DEVICE
        val model = android.os.Build.MODEL
        val product = android.os.Build.PRODUCT
        
        val expectedEmulator = (brand.startsWith("generic") && device.startsWith("generic")) ||
                               "google_sdk" == product ||
                               model.contains("Emulator") ||
                               model.contains("Android SDK")
        
        assertEquals("Emulator-Erkennung sollte mit Build-Properties übereinstimmen",
                    expectedEmulator, result)
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
        
        // Debugger-Erkennung: Prüfe nur wenn tatsächlich Debugger verbunden ist
        val actualDebuggerState = Debug.isDebuggerConnected()
        if (actualDebuggerState) {
            assertTrue("Debugger sollte erkannt werden wenn verbunden", 
                      result.details["debugged"] == true)
        } else {
            assertFalse("Debugger sollte NICHT erkannt werden wenn nicht verbunden", 
                       result.details["debugged"] == true)
        }
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
