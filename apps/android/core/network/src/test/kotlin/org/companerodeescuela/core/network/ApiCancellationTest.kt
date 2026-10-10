package org.companerodeescuela.core.network

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class ApiCancellationTest {
    @Test fun cancellingARequestDoesNotBecomeAnErrorOrAllowFurtherWork() = runTest {
        assertFailsWith<CancellationException> {
            apiCall<Unit> { throw CancellationException("cancelled") }
        }
    }
}
