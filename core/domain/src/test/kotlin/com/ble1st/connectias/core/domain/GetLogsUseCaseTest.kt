package com.ble1st.connectias.core.domain

import app.cash.turbine.test
import com.ble1st.connectias.core.data.repository.LogRepository
import com.ble1st.connectias.core.model.LogLevel
import com.ble1st.connectias.core.testing.FakeData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetLogsUseCaseTest {

    private lateinit var logRepository: LogRepository
    private lateinit var useCase: GetLogsUseCase

    @Before
    fun setup() {
        logRepository = mockk()
        useCase = GetLogsUseCase(logRepository)
    }

    @Test
    fun `should return logs with correct counts`() = runTest {
        // Given
        val logs = listOf(
            FakeData.createLogEntry(level = LogLevel.INFO),
            FakeData.createLogEntry(level = LogLevel.ERROR),
            FakeData.createLogEntry(level = LogLevel.ERROR),
            FakeData.createLogEntry(level = LogLevel.WARN),
            FakeData.createLogEntry(level = LogLevel.DEBUG)
        )
        every { logRepository.getLogsByLevel(any(), any()) } returns flowOf(logs)

        // When & Then
        useCase(LogLevel.DEBUG, 1000).test {
            val result = awaitItem()
            assertEquals(5, result.totalCount)
            assertEquals(2, result.errorCount)
            assertEquals(1, result.warningCount)
            awaitComplete()
        }
    }

    @Test
    fun `should return empty result when no logs`() = runTest {
        // Given
        every { logRepository.getLogsByLevel(any(), any()) } returns flowOf(emptyList())

        // When & Then
        useCase(LogLevel.INFO, 100).test {
            val result = awaitItem()
            assertEquals(0, result.totalCount)
            assertEquals(0, result.errorCount)
            assertEquals(0, result.warningCount)
            awaitComplete()
        }
    }

    @Test
    fun `should pass correct parameters to repository`() = runTest {
        // Given
        val minLevel = LogLevel.WARN
        val limit = 500
        every { logRepository.getLogsByLevel(minLevel, limit) } returns flowOf(emptyList())

        // When
        useCase(minLevel, limit).test {
            awaitItem()
            awaitComplete()
        }

        // Then - mockk verifies the call was made with correct parameters
    }
}
