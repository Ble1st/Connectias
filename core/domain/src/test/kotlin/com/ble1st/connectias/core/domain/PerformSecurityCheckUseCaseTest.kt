package com.ble1st.connectias.core.domain

import com.ble1st.connectias.core.data.repository.SecurityRepository
import com.ble1st.connectias.core.testing.FakeData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PerformSecurityCheckUseCaseTest {

    private lateinit var securityRepository: SecurityRepository
    private lateinit var useCase: PerformSecurityCheckUseCase

    @Before
    fun setup() {
        securityRepository = mockk(relaxed = true)
        useCase = PerformSecurityCheckUseCase(securityRepository)
    }

    @Test
    fun `should perform security check and return result`() = runTest {
        // Given
        val expectedResult = FakeData.createSecurityCheckResult(isSecure = true)
        coEvery { securityRepository.performSecurityCheck() } returns expectedResult

        // When
        val result = useCase()

        // Then
        assertEquals(expectedResult, result)
        coVerify { securityRepository.performSecurityCheck() }
    }

    @Test
    fun `should log all detected threats`() = runTest {
        // Given
        val threats = listOf(
            FakeData.createSecurityThreat("root"),
            FakeData.createSecurityThreat("debugger")
        )
        val checkResult = FakeData.createSecurityCheckResult(
            isSecure = false,
            threats = threats
        )
        coEvery { securityRepository.performSecurityCheck() } returns checkResult

        // When
        useCase()

        // Then
        coVerify(exactly = 2) { securityRepository.logThreat(any()) }
        coVerify { securityRepository.logThreat(threats[0]) }
        coVerify { securityRepository.logThreat(threats[1]) }
    }

    @Test
    fun `should not log threats when none detected`() = runTest {
        // Given
        val checkResult = FakeData.createSecurityCheckResult(isSecure = true)
        coEvery { securityRepository.performSecurityCheck() } returns checkResult

        // When
        useCase()

        // Then
        coVerify(exactly = 0) { securityRepository.logThreat(any()) }
    }
}
