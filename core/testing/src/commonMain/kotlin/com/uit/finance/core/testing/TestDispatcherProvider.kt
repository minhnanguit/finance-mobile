package com.uit.finance.core.testing

import com.uit.finance.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope

/** Routes every dispatcher to a single [TestDispatcher] so `runTest` controls all coroutines. */
class TestDispatcherProvider(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = testDispatcher
    override val io: CoroutineDispatcher get() = testDispatcher
    override val default: CoroutineDispatcher get() = testDispatcher
    override val unconfined: CoroutineDispatcher get() = testDispatcher
}

/**
 * Provider bound to the `runTest` scheduler. Always prefer this inside `runTest`: a provider created
 * with a fresh `StandardTestDispatcher()` would run on a scheduler `runTest` never advances.
 */
fun TestScope.testDispatcherProvider(): TestDispatcherProvider =
    TestDispatcherProvider(StandardTestDispatcher(testScheduler))
