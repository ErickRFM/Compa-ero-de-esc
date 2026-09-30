package org.companerodeescuela.core.testing.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.companerodeescuela.core.common.dispatcher.DispatcherProvider

/**
 * Dispatchers backed by a [TestDispatcher], so coroutines under test run
 * deterministically and `advanceUntilIdle` really does drain them.
 */
class TestDispatcherProvider(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}

/** Runs everything eagerly, which keeps simple tests readable. */
fun immediateTestDispatcherProvider(): DispatcherProvider =
    TestDispatcherProvider(UnconfinedTestDispatcher())
