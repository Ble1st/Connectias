package com.ble1st.connectias.core.testing

import com.ble1st.connectias.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

/**
 * Test implementation of DispatcherProvider using TestDispatcher.
 * All dispatchers use the same TestDispatcher for deterministic testing.
 */
class TestDispatcherProvider(
    testDispatcher: TestDispatcher = StandardTestDispatcher()
) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
}
