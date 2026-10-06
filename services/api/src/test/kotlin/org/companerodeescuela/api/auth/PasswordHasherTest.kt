package org.companerodeescuela.api.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {
    @Test
    fun `hash is salted and verifies the original password`() {
        val hasher = PasswordHasher()
        val first = hasher.hash("Correct horse battery staple")
        val second = hasher.hash("Correct horse battery staple")

        assertNotEquals(first, second)
        assertTrue(hasher.verify("Correct horse battery staple", first))
        assertFalse(hasher.verify("wrong", first))
    }
}
