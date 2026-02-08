// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end integration tests for LoggingService IPC flows.
 *
 * Tests real service binding, database operations, rate limiting, metrics,
 * and async callback delivery over Binder.
 *
 * Requires a connected device or emulator (androidTest).
 */
@RunWith(AndroidJUnit4::class)
class LoggingServiceIntegrationTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var service: ILoggingService
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Test passphrase — 32 zero bytes (deterministic for test isolation). */
    private val testKey = ByteArray(32) { it.toByte() }

    @Before
    fun bindService() {
        val intent = Intent().apply {
            component = ComponentName(
                context.packageName,
                "com.ble1st.connectias.service.logging.LoggingService"
            )
        }
        val binder: IBinder = serviceRule.bindService(intent)
        service = ILoggingService.Stub.asInterface(binder)
        // Provide the test key so the database initializes
        service.setDatabaseKey(testKey.clone())
    }

    // ========== Service binding ==========

    @Test
    fun testServiceBindingAndDefaultState() {
        assertTrue("Service should be enabled by default", service.isEnabled())
    }

    @Test
    fun testSetEnabled() {
        service.setEnabled(false)
        assertFalse("Service should be disabled", service.isEnabled())
        service.setEnabled(true)
        assertTrue("Service should be re-enabled", service.isEnabled())
    }

    // ========== Log submission and retrieval ==========

    @Test
    fun testSubmitAndRetrieveLog() {
        service.clearAllLogs()
        service.submitLog("com.test.integration", "INFO", "ITest", "Integration test message")
        Thread.sleep(200) // Allow async insert to complete

        val logs = service.getRecentLogs(10)
        assertTrue(
            "Submitted log should be retrievable",
            logs.any { it.message == "Integration test message" }
        )
    }

    @Test
    fun testSubmitLogWithException() {
        service.clearAllLogs()
        service.submitLogWithException(
            "com.test.integration", "ERROR", "ITest",
            "Error occurred", "java.lang.RuntimeException: test\n\tat com.example.Foo.bar()"
        )
        Thread.sleep(200)

        val logs = service.getRecentLogs(10)
        val entry = logs.find { it.message == "Error occurred" }
        assertNotNull("Log with exception should be stored", entry)
        assertNotNull("Exception trace should be stored", entry?.exceptionTrace)
    }

    @Test
    fun testGetLogsByPackage() {
        service.clearAllLogs()
        service.submitLog("com.filter.test", "WARN", "T", "Filter test")
        service.submitLog("com.other.app", "INFO", "T", "Other log")
        Thread.sleep(200)

        val filtered = service.getLogsByPackage("com.filter.test", 10)
        assertTrue("Should find logs for com.filter.test", filtered.any { it.message == "Filter test" })
        assertFalse("Should not include other package", filtered.any { it.packageName == "com.other.app" })
    }

    @Test
    fun testGetLogsByLevel() {
        service.clearAllLogs()
        service.submitLog("com.level.test", "ERROR", "T", "Error log")
        service.submitLog("com.level.test", "DEBUG", "T", "Debug log")
        Thread.sleep(200)

        val errors = service.getLogsByLevel("ERROR", 10)
        assertTrue("Should find ERROR logs", errors.any { it.message == "Error log" })
        assertFalse("Should not include DEBUG logs", errors.any { it.level == "DEBUG" })
    }

    @Test
    fun testGetLogCount() {
        service.clearAllLogs()
        repeat(5) { service.submitLog("com.count.test", "INFO", "T", "Log $it") }
        Thread.sleep(200)

        val count = service.logCount
        assertTrue("Log count should be at least 5", count >= 5)
    }

    @Test
    fun testClearAllLogs() {
        service.submitLog("com.clear.test", "INFO", "T", "Will be cleared")
        Thread.sleep(200)
        service.clearAllLogs()
        Thread.sleep(200)

        assertEquals("Log count should be 0 after clear", 0, service.logCount)
    }

    // ========== Security metrics ==========

    @Test
    fun testMetricsEndpoint() {
        service.submitLog("com.metrics.test", "INFO", "T", "Metrics test")
        Thread.sleep(200)

        val metrics = service.securityMetrics
        assertNotNull("Metrics should not be null", metrics)
        assertTrue("Total logs should be positive", metrics!!.totalLogsReceived > 0)
    }

    @Test
    fun testRateLimitViolationsReflectedInMetrics() {
        // Exhaust rate limit (burst = 150)
        repeat(200) {
            service.submitLog("com.ratelimit.test", "DEBUG", "T", "msg $it")
        }
        Thread.sleep(200)

        val metrics = service.securityMetrics
        assertNotNull(metrics)
        assertTrue("Should have rate limit violations", metrics!!.rateLimitViolations > 0)
    }

    // ========== Async IPC (callback) ==========

    @Test
    fun testGetRecentLogsAsync() {
        service.clearAllLogs()
        service.submitLog("com.async.test", "INFO", "T", "Async test message")
        Thread.sleep(200)

        val latch = CountDownLatch(1)
        var receivedLogs: List<ExternalLogParcel>? = null

        val callback = object : ILoggingResultCallback.Stub() {
            override fun onLogsResult(logs: List<ExternalLogParcel>, requestId: Int) {
                receivedLogs = logs
                latch.countDown()
            }

            override fun onAuditEventsResult(events: List<SecurityAuditParcel>, requestId: Int) {}

            override fun onError(errorMessage: String, requestId: Int) {
                latch.countDown()
            }
        }

        service.getRecentLogsAsync(50, callback, 1001)

        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue("Callback should be invoked within 5 seconds", completed)
        assertNotNull("Logs list should not be null", receivedLogs)
        assertTrue(
            "Should contain the submitted log",
            receivedLogs!!.any { it.message == "Async test message" }
        )
    }

    @Test
    fun testGetRecentAuditEventsAsync() {
        // Rate-limit to produce at least one audit event
        repeat(200) { service.submitLog("com.audit.test", "DEBUG", "T", "msg $it") }
        Thread.sleep(200)

        val latch = CountDownLatch(1)
        var receivedEvents: List<SecurityAuditParcel>? = null

        val callback = object : ILoggingResultCallback.Stub() {
            override fun onLogsResult(logs: List<ExternalLogParcel>, requestId: Int) {}

            override fun onAuditEventsResult(events: List<SecurityAuditParcel>, requestId: Int) {
                receivedEvents = events
                latch.countDown()
            }

            override fun onError(errorMessage: String, requestId: Int) {
                latch.countDown()
            }
        }

        service.getRecentAuditEventsAsync(50, callback, 2002)

        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue("Audit callback should be invoked within 5 seconds", completed)
        assertNotNull("Audit events list should not be null", receivedEvents)
    }

    // ========== Security: Database key exchange ==========

    @Test
    fun testSetDatabaseKeyIsIdempotent() {
        // Calling setDatabaseKey twice should not crash or reset the DB
        service.setDatabaseKey(testKey.clone())
        service.submitLog("com.key.test", "INFO", "T", "After second key")
        Thread.sleep(200)

        val logs = service.getRecentLogs(10)
        assertTrue("Log should still be retrievable", logs.any { it.message == "After second key" })
    }
}
