package org.companerodeescuela.shared.validation

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ValidatorsTest {

    @Test
    @DisplayName("notBlank rejects null, empty and whitespace-only values")
    fun notBlankRejectsBlankValues() {
        assertTrue(Validators.notBlank("field", "value").isValid)
        assertFalse(Validators.notBlank("field", null).isValid)
        assertFalse(Validators.notBlank("field", "").isValid)
        assertFalse(Validators.notBlank("field", "   ").isValid)
    }

    @Test
    @DisplayName("absoluteHttpUrl accepts blank as not-configured")
    fun absoluteHttpUrlAcceptsBlank() {
        assertTrue(Validators.absoluteHttpUrl("MONGODB_URI", null).isValid)
        assertTrue(Validators.absoluteHttpUrl("MONGODB_URI", "  ").isValid)
    }

    @Test
    @DisplayName("absoluteHttpUrl requires an absolute http(s) URL")
    fun absoluteHttpUrlRequiresAbsoluteUrl() {
        assertTrue(Validators.absoluteHttpUrl("BASE_URL", "https://api.example.edu").isValid)
        assertTrue(Validators.absoluteHttpUrl("BASE_URL", "http://localhost:8080").isValid)

        assertFalse(Validators.absoluteHttpUrl("BASE_URL", "/relative").isValid)
        assertFalse(Validators.absoluteHttpUrl("BASE_URL", "ftp://example.edu").isValid)
        assertFalse(Validators.absoluteHttpUrl("BASE_URL", "not a uri").isValid)
    }

    @Test
    @DisplayName("port accepts only 1..65535")
    fun portRange() {
        assertTrue(Validators.port("API_PORT", "8080").isValid)
        assertTrue(Validators.port("API_PORT", " 1 ").isValid)
        assertTrue(Validators.port("API_PORT", "65535").isValid)

        assertFalse(Validators.port("API_PORT", "0").isValid)
        assertFalse(Validators.port("API_PORT", "65536").isValid)
        assertFalse(Validators.port("API_PORT", "-1").isValid)
        assertFalse(Validators.port("API_PORT", "http").isValid)
        assertFalse(Validators.port("API_PORT", null).isValid)
    }

    @Test
    @DisplayName("nonNegativeInt rejects negatives and non-numbers")
    fun nonNegativeInt() {
        assertTrue(Validators.nonNegativeInt("TIMEOUT_MS", "0").isValid)
        assertTrue(Validators.nonNegativeInt("TIMEOUT_MS", "5000").isValid)

        assertFalse(Validators.nonNegativeInt("TIMEOUT_MS", "-1").isValid)
        assertFalse(Validators.nonNegativeInt("TIMEOUT_MS", "abc").isValid)
        assertFalse(Validators.nonNegativeInt("TIMEOUT_MS", "").isValid)
    }

    @Test
    @DisplayName("describe() names the field and the reason but never the value")
    fun describeDoesNotLeakValues() {
        val result = Validators.notBlank("JWT_SECRET", "   ")

        assertFalse(result.isValid)
        val described = result.describe()
        assertTrue(described.contains("JWT_SECRET"), described)
        assertEquals("JWT_SECRET: must not be blank", described)
    }

    @Test
    @DisplayName("Invalid requires at least one failure")
    fun invalidRequiresFailure() {
        assertThrows<IllegalArgumentException> {
            ValidationResult.Invalid(emptyList())
        }
    }
}
