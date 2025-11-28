package com.ble1st.connectias.feature.security.password

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PasswordStrengthProvider.
 */
class PasswordStrengthProviderTest {

    private val passwordStrengthProvider = PasswordStrengthProvider()

    @Test
    fun `test very weak password`() = runTest {
        val strength = passwordStrengthProvider.analyzePassword("123")
        
        assertEquals(Strength.VERY_WEAK, strength.strength)
        assertTrue(strength.score <= 1)
    }

    @Test
    fun `test weak password`() = runTest {
        val strength = passwordStrengthProvider.analyzePassword("password")
        
        assertTrue(strength.strength == Strength.WEAK || strength.strength == Strength.VERY_WEAK)
        assertTrue(strength.score <= 3)
    }

    @Test
    fun `test strong password`() = runTest {
        val strength = passwordStrengthProvider.analyzePassword("MyStr0ng!P@ssw0rd")
        
        assertTrue(strength.strength == Strength.STRONG || strength.strength == Strength.VERY_STRONG)
        assertTrue(strength.score >= 6)
    }

    @Test
    fun `test password generator`() = runTest {
        val password = passwordStrengthProvider.generatePassword(length = 16, includeSpecial = true)
        
        assertNotNull(password)
        assertEquals(16, password.length)
        assertTrue(password.any { it.isLowerCase() })
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { !it.isLetterOrDigit() })
    }

    @Test
    fun `test password entropy calculation`() = runTest {
        val strength1 = passwordStrengthProvider.analyzePassword("password")
        val strength2 = passwordStrengthProvider.analyzePassword("MyStr0ng!P@ssw0rd")
        
        assertTrue(strength2.entropy > strength1.entropy)
    }

    @Test
    fun `test empty password`() = runTest {
        val strength = passwordStrengthProvider.analyzePassword("")
        
        assertEquals(Strength.VERY_WEAK, strength.strength)
        assertEquals(0, strength.score)
    }
}

