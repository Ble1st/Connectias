// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LogInputValidator.
 *
 * Tests input validation, sanitization, and security checks.
 */
class LogInputValidatorTest {

    @Test
    fun `valid input passes validation`() {
        val result = LogInputValidator.validate(
            packageName = "com.example.app",
            level = "INFO",
            tag = "TestTag",
            message = "Test message",
            exceptionTrace = null
        )

        assertNotNull("Valid input should pass", result)
        assertEquals("com.example.app", result?.packageName)
        assertEquals("INFO", result?.level)
        assertEquals("TestTag", result?.tag)
        assertEquals("Test message", result?.message)
        assertNull(result?.exceptionTrace)
    }

    @Test
    fun `empty package name fails validation`() {
        val result = LogInputValidator.validate(
            packageName = "",
            level = "INFO",
            tag = "Tag",
            message = "Message",
            exceptionTrace = null
        )

        assertNull("Empty package name should fail", result)
    }

    @Test
    fun `blank package name fails validation`() {
        val result = LogInputValidator.validate(
            packageName = "   ",
            level = "INFO",
            tag = "Tag",
            message = "Message",
            exceptionTrace = null
        )

        assertNull("Blank package name should fail", result)
    }

    @Test
    fun `oversized package name is truncated`() {
        val longName = "com.example." + "a".repeat(300)
        val result = LogInputValidator.validate(
            packageName = longName,
            level = "INFO",
            tag = "Tag",
            message = "Message",
            exceptionTrace = null
        )

        assertNotNull(result)
        assertTrue("Package name should be truncated",
            result!!.packageName.length <= LogInputValidator.MAX_PACKAGE_NAME_SIZE)
    }

    @Test
    fun `log level normalization works`() {
        // Test short forms
        val testCases = mapOf(
            "V" to "VERBOSE",
            "D" to "DEBUG",
            "I" to "INFO",
            "W" to "WARN",
            "E" to "ERROR",
            "A" to "ASSERT"
        )

        testCases.forEach { (input, expected) ->
            val result = LogInputValidator.validate("com.test", input, "Tag", "Msg", null)
            assertEquals("Level $input should normalize to $expected", expected, result?.level)
        }
    }

    @Test
    fun `invalid log level defaults to INFO`() {
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INVALID",
            tag = "Tag",
            message = "Message",
            exceptionTrace = null
        )

        assertNotNull(result)
        assertEquals("Invalid level should default to INFO", "INFO", result?.level)
    }

    @Test
    fun `lowercase log levels are normalized`() {
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "debug",
            tag = "Tag",
            message = "Message",
            exceptionTrace = null
        )

        assertNotNull(result)
        assertEquals("Lowercase should be normalized", "DEBUG", result?.level)
    }

    @Test
    fun `empty tag gets default value`() {
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = "",
            message = "Message",
            exceptionTrace = null
        )

        assertNotNull(result)
        assertEquals("Empty tag should get default", "ExternalApp", result?.tag)
    }

    @Test
    fun `oversized tag is truncated`() {
        val longTag = "a".repeat(200)
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = longTag,
            message = "Message",
            exceptionTrace = null
        )

        assertNotNull(result)
        assertTrue("Tag should be truncated",
            result!!.tag.length <= LogInputValidator.MAX_TAG_SIZE + 3) // +3 for "..."
    }

    @Test
    fun `empty message gets default value`() {
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = "Tag",
            message = "",
            exceptionTrace = null
        )

        assertNotNull(result)
        assertEquals("Empty message should get default", "(empty message)", result?.message)
    }

    @Test
    fun `oversized message is truncated`() {
        val longMessage = "a".repeat(10000)
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = "Tag",
            message = longMessage,
            exceptionTrace = null
        )

        assertNotNull(result)
        assertTrue("Message should be truncated",
            result!!.message.length <= LogInputValidator.MAX_MESSAGE_SIZE + 20) // +20 for " [TRUNCATED]"
        assertTrue("Should have truncation marker", result.message.contains("[TRUNCATED]"))
    }

    @Test
    fun `oversized exception trace is truncated`() {
        val longTrace = "Stack trace\n".repeat(1000)
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "ERROR",
            tag = "Tag",
            message = "Error occurred",
            exceptionTrace = longTrace
        )

        assertNotNull(result)
        assertNotNull(result?.exceptionTrace)
        assertTrue("Exception trace should be truncated",
            result!!.exceptionTrace!!.length <= LogInputValidator.MAX_EXCEPTION_TRACE_SIZE + 20)
    }

    @Test
    fun `null exception trace is preserved`() {
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = "Tag",
            message = "Message",
            exceptionTrace = null
        )

        assertNotNull(result)
        assertNull("Null exception should stay null", result?.exceptionTrace)
    }

    @Test
    fun `blank exception trace becomes null`() {
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "ERROR",
            tag = "Tag",
            message = "Error",
            exceptionTrace = "   "
        )

        assertNotNull(result)
        assertNull("Blank exception should become null", result?.exceptionTrace)
    }

    @Test
    fun `control characters are sanitized in tag`() {
        val tagWithControlChars = "Tag\u0000\u0001\u001bTest"
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = tagWithControlChars,
            message = "Message",
            exceptionTrace = null
        )

        assertNotNull(result)
        assertFalse("Control chars should be escaped",
            result!!.tag.contains("\u0000"))
        assertTrue("Should contain escaped representation",
            result.tag.contains("\\x"))
    }

    @Test
    fun `newlines are preserved in message`() {
        val messageWithNewlines = "Line 1\nLine 2\nLine 3"
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = "Tag",
            message = messageWithNewlines,
            exceptionTrace = null
        )

        assertNotNull(result)
        assertTrue("Newlines should be preserved in message",
            result!!.message.contains("\n"))
        assertEquals("Should have 3 lines", 3, result.message.split("\n").size)
    }

    @Test
    fun `newlines are preserved in exception trace`() {
        val traceWithNewlines = "at com.example.Class.method()\nat com.example.Other.method()"
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "ERROR",
            tag = "Tag",
            message = "Error",
            exceptionTrace = traceWithNewlines
        )

        assertNotNull(result)
        assertNotNull(result?.exceptionTrace)
        assertTrue("Newlines should be preserved in trace",
            result!!.exceptionTrace!!.contains("\n"))
    }

    @Test
    fun `null bytes are sanitized`() {
        val messageWithNullBytes = "Test\u0000Message"
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = "Tag",
            message = messageWithNullBytes,
            exceptionTrace = null
        )

        assertNotNull(result)
        assertFalse("Null bytes should be removed",
            result!!.message.contains("\u0000"))
        assertTrue("Should contain escaped representation",
            result.message.contains("\\0") || result.message.contains("\\x00"))
    }

    @Test
    fun `ANSI escape sequences are sanitized`() {
        val messageWithAnsi = "\u001b[31mRed Text\u001b[0m"
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = "Tag",
            message = messageWithAnsi,
            exceptionTrace = null
        )

        assertNotNull(result)
        assertFalse("ANSI escapes should be removed",
            result!!.message.contains("\u001b"))
        assertTrue("Should contain escaped representation",
            result.message.contains("\\x1b"))
    }

    @Test
    fun `SQL injection patterns are detected`() {
        val sqlPatterns = listOf(
            "'; DROP TABLE users; --",
            "1' OR '1'='1",
            "UNION SELECT * FROM passwords",
            "/* comment */ DELETE FROM data"
        )

        sqlPatterns.forEach { pattern ->
            val hasSuspicious = LogInputValidator.hasSuspiciousPatterns(pattern)
            assertTrue("SQL pattern should be detected: $pattern", hasSuspicious)
        }
    }

    @Test
    fun `excessive special characters are detected`() {
        // Create a string with 80% special characters (definitely should be flagged)
        val suspiciousMessage = "<><><><><><><><><><><><><><>" // 26 chars, all special (100%)
        val hasSuspicious = LogInputValidator.hasSuspiciousPatterns(suspiciousMessage)
        assertTrue("Excessive special chars should be detected", hasSuspicious)
    }

    @Test
    fun `normal messages are not flagged as suspicious`() {
        val normalMessages = listOf(
            "User logged in successfully",
            "Network request completed in 123ms",
            "Error: File not found at /path/to/file",
            "Processing 50 items..."
        )

        normalMessages.forEach { message ->
            val hasSuspicious = LogInputValidator.hasSuspiciousPatterns(message)
            assertFalse("Normal message should not be suspicious: $message", hasSuspicious)
        }
    }

    @Test
    fun `messages with null bytes are flagged as suspicious`() {
        val messageWithNull = "Test\u0000Message"
        val hasSuspicious = LogInputValidator.hasSuspiciousPatterns(messageWithNull)
        assertTrue("Message with null byte should be suspicious", hasSuspicious)
    }

    @Test
    fun `validation handles all fields correctly in complex case`() {
        val longPackage = "com.example.verylongpackagename." + "app".repeat(100)
        val invalidLevel = "CUSTOM_LEVEL"
        val longTag = "VeryLongTag" + "X".repeat(200)
        val longMessage = "Message content " + "data ".repeat(1000)
        val longTrace = "Stack trace line\n".repeat(500)

        val result = LogInputValidator.validate(
            packageName = longPackage,
            level = invalidLevel,
            tag = longTag,
            message = longMessage,
            exceptionTrace = longTrace
        )

        assertNotNull("Should handle complex case", result)

        // Verify all truncations/normalizations
        assertTrue("Package truncated",
            result!!.packageName.length <= LogInputValidator.MAX_PACKAGE_NAME_SIZE)
        assertEquals("Level normalized", "INFO", result.level)
        assertTrue("Tag truncated",
            result.tag.length <= LogInputValidator.MAX_TAG_SIZE + 3)
        assertTrue("Message truncated",
            result.message.length <= LogInputValidator.MAX_MESSAGE_SIZE + 20)
        assertNotNull(result.exceptionTrace)
        assertTrue("Trace truncated",
            result.exceptionTrace!!.length <= LogInputValidator.MAX_EXCEPTION_TRACE_SIZE + 20)
    }

    @Test
    fun `whitespace is trimmed from all fields`() {
        val result = LogInputValidator.validate(
            packageName = "  com.test  ",
            level = "  INFO  ",
            tag = "  Tag  ",
            message = "  Message  ",
            exceptionTrace = "  Trace  "
        )

        assertNotNull(result)
        assertEquals("com.test", result?.packageName)
        assertEquals("INFO", result?.level)
        assertEquals("Tag", result?.tag)
        assertEquals("Message", result?.message)
        assertEquals("Trace", result?.exceptionTrace)
    }

    @Test
    fun `DEL character is sanitized`() {
        val messageWithDel = "Test\u007FMessage"
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = "Tag",
            message = messageWithDel,
            exceptionTrace = null
        )

        assertNotNull(result)
        assertFalse("DEL char should be removed",
            result!!.message.contains("\u007F"))
    }

    @Test
    fun `tabs are preserved in messages`() {
        val messageWithTab = "Column1\tColumn2\tColumn3"
        val result = LogInputValidator.validate(
            packageName = "com.test",
            level = "INFO",
            tag = "Tag",
            message = messageWithTab,
            exceptionTrace = null
        )

        assertNotNull(result)
        assertTrue("Tabs should be preserved",
            result!!.message.contains("\t"))
    }
}
