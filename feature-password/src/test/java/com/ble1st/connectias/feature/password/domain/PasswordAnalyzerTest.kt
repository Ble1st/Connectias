package com.ble1st.connectias.feature.password.domain

import com.ble1st.connectias.feature.password.data.PasswordStrength
import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordAnalyzerTest {

    private val analyzer = PasswordAnalyzer()

    @Test
    fun `analyzePasswordStrength returns WEAK for empty password`() {
        val (strength, score) = analyzer.analyzePasswordStrength("")
        assertEquals(PasswordStrength.WEAK, strength)
        assertEquals(0, score)
    }

    @Test
    fun `analyzePasswordStrength returns WEAK for short simple password`() {
        val (strength, _) = analyzer.analyzePasswordStrength("12345")
        assertEquals(PasswordStrength.WEAK, strength)
    }
    
    @Test
    fun `analyzePasswordStrength returns WEAK for only letters short`() {
        val (strength, _) = analyzer.analyzePasswordStrength("password")
        assertEquals(PasswordStrength.WEAK, strength)
    }

    @Test
    fun `analyzePasswordStrength returns MEDIUM for decent length mixed case`() {
        val (strength, _) = analyzer.analyzePasswordStrength("Password123")
        // Length 11 (+10), Upper (+10), Lower (+10), Digit (+10) -> ~40. But Penalties? No.
        // Let's trace: 10 + 10 + 10 + 10 = 40. MEDIUM starts at 40.
        assertEquals(PasswordStrength.MEDIUM, strength)
    }

    @Test
    fun `analyzePasswordStrength returns STRONG for complex password`() {
        val (strength, _) = analyzer.analyzePasswordStrength("P@ssw0rd123!")
        // Length 12 (+20), All types (+45), Bonus (+15) -> 80 -> STRONG
        assertEquals(PasswordStrength.STRONG, strength)
    }

    @Test
    fun `analyzePasswordStrength returns VERY_STRONG for long complex password`() {
        val (strength, _) = analyzer.analyzePasswordStrength("Correct-Horse-Battery-Staple-99!")
        // Length > 16 (+30), All types (+45), Bonus (+15) -> 90 -> VERY_STRONG
        assertEquals(PasswordStrength.VERY_STRONG, strength)
    }
}
