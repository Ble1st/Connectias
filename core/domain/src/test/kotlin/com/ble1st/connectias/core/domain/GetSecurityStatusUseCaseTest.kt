package com.ble1st.connectias.core.domain

import app.cash.turbine.test
import com.ble1st.connectias.core.data.repository.SecurityRepository
import com.ble1st.connectias.core.testing.FakeData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetSecurityStatusUseCaseTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var useCase: GetSecurityStatusUseCase

    @Before
    fun setup() {
        securityRepository = mockk()
        useCase = GetSecurityStatusUseCase(securityRepository)
    }

    @Test
    fun `should calculate CRITICAL risk level when root detected`() = runTest {
        // Given
        val threats = listOf(FakeData.createSecurityThreat("root"))
        every { securityRepository.getRecentThreats(any()) } returns flowOf(threats)

        // When & Then
        useCase().test {
            val result = awaitItem()
            assertEquals(RiskLevel.CRITICAL, result.riskLevel)
            assertTrue(result.currentThreats.isNotEmpty())
            assertTrue(result.recommendations.any { it.contains("rooted") })
            awaitComplete()
        }
    }

    @Test
    fun `should calculate HIGH risk level when debugger detected`() = runTest {
        // Given
        val threats = listOf(FakeData.createSecurityThreat("debugger"))
        every { securityRepository.getRecentThreats(any()) } returns flowOf(threats)

        // When & Then
        useCase().test {
            val result = awaitItem()
            assertEquals(RiskLevel.HIGH, result.riskLevel)
            assertTrue(result.recommendations.any { it.contains("Debugger") })
            awaitComplete()
        }
    }

    @Test
    fun `should calculate MEDIUM risk level when emulator detected`() = runTest {
        // Given
        val threats = listOf(FakeData.createSecurityThreat("emulator"))
        every { securityRepository.getRecentThreats(any()) } returns flowOf(threats)

        // When & Then
        useCase().test {
            val result = awaitItem()
            assertEquals(RiskLevel.MEDIUM, result.riskLevel)
            assertTrue(result.recommendations.any { it.contains("emulator") })
            awaitComplete()
        }
    }

    @Test
    fun `should calculate LOW risk level when no threats`() = runTest {
        // Given
        every { securityRepository.getRecentThreats(any()) } returns flowOf(emptyList())

        // When & Then
        useCase().test {
            val result = awaitItem()
            assertEquals(RiskLevel.LOW, result.riskLevel)
            assertTrue(result.currentThreats.isEmpty())
            assertTrue(result.recommendations.isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun `should limit threat history to 10 items`() = runTest {
        // Given
        val threats = List(20) { FakeData.createSecurityThreat("emulator") }
        every { securityRepository.getRecentThreats(any()) } returns flowOf(threats)

        // When & Then
        useCase().test {
            val result = awaitItem()
            assertEquals(10, result.threatHistory.size)
            awaitComplete()
        }
    }
}
