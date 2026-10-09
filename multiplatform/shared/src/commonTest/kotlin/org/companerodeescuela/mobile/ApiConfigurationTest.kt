package org.companerodeescuela.mobile

import kotlin.test.*

class ApiConfigurationTest {
    @Test fun preservesPathPrefixAndNormalizesSlash() {
        assertEquals("https://school.example/api/", ApiConfiguration("https://school.example/api").baseUrl)
    }
    @Test fun rejectsNonBaseUrlsAndEmbeddedCredentials() {
        listOf("", "school.example", "httpx://school.example", "https:///missing-host",
            "https://user:pass@school.example/", "https://school.example/?key=value",
            "https://school.example/#fragment").forEach {
            assertFailsWith<IllegalArgumentException>(it) { ApiConfiguration(it) }
        }
    }
    @Test fun httpRequiresDebugAndLoopback() {
        assertFailsWith<IllegalArgumentException> { ApiConfiguration("http://localhost:8080/") }
        assertEquals("http://localhost:8080/", ApiConfiguration("http://localhost:8080/", true).baseUrl)
        assertEquals("http://127.0.0.1:8080/", ApiConfiguration("http://127.0.0.1:8080/", true).baseUrl)
        assertFailsWith<IllegalArgumentException> { ApiConfiguration("http://school.example/", true) }
    }
}
