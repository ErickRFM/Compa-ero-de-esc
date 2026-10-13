package org.companerodeescuela.api

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class CancellableResultTest {
    @Test
    fun `cancellable result helper rethrows coroutine cancellation`() = runTest {
        assertFailsWith<CancellationException> {
            runCatchingCancellable<Unit> { throw CancellationException("cancel test") }
        }
    }
}
