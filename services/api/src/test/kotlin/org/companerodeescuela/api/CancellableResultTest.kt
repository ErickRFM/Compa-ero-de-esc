package org.companerodeescuela.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CancellableResultTest {
    @Test
    fun `cancellable result helper rethrows coroutine cancellation`() =
        runTest {
            assertFailsWith<CancellationException> {
                runCatchingCancellable<Unit> {
                    throw CancellationException("cancel test")
                }
            }
        }
}
