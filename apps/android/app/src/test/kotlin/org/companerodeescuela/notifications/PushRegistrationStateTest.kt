package org.companerodeescuela.notifications

import org.companerodeescuela.core.common.result.AppError
import kotlin.test.Test
import kotlin.test.assertEquals

class PushRegistrationStateTest {
    @Test fun `registration rejection never reports registered`() {
        assertEquals(PushRegistrationState.REJECTED, pushRegistrationFailure(AppError.Http(400)))
        assertEquals(PushRegistrationState.REJECTED, pushRegistrationFailure(AppError.Http(403)))
        assertEquals(PushRegistrationState.AUTH_REQUIRED, pushRegistrationFailure(AppError.Http(401)))
    }

    @Test fun `only transient registration failures retry`() {
        assertEquals(PushRegistrationState.RETRYING, pushRegistrationFailure(AppError.Network()))
        assertEquals(PushRegistrationState.RETRYING, pushRegistrationFailure(AppError.Http(429)))
        assertEquals(PushRegistrationState.RETRYING, pushRegistrationFailure(AppError.Http(503)))
        assertEquals(PushRegistrationState.REJECTED, pushRegistrationFailure(AppError.Serialization()))
    }
}
