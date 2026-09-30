package org.companerodeescuela.core.ui.state

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ContentStateTest {

    @Test
    @DisplayName("A non-empty success becomes Content")
    fun successBecomesContent() {
        val state = Outcome.Success(listOf("a")).toContentState { it.isEmpty() }

        assertEquals<ContentState<*>>(ContentState.Content(listOf("a")), state)
    }

    @Test
    @DisplayName("An empty success becomes Empty, not a blank list")
    fun emptySuccessBecomesEmpty() {
        val state = Outcome.Success(emptyList<String>()).toContentState { it.isEmpty() }

        assertEquals<ContentState<*>>(ContentState.Empty, state)
    }

    @Test
    @DisplayName("A failure keeps the error for the UI to explain")
    fun failureBecomesFailed() {
        // Outcome.Failure carries no value, so the emptiness check is never
        // reached; the explicit type keeps the compiler from guessing.
        val state: ContentState<Nothing> =
            Outcome.Failure(AppError.Network("timeout")).toContentState { true }

        assertEquals<ContentState<*>>(ContentState.Failed(AppError.Network("timeout")), state)
    }

    @Test
    @DisplayName("Emptiness is the caller's decision, not a hardcoded list check")
    fun emptinessIsCallerDefined() {
        val schedule = Outcome.Success("sin-clases")

        assertEquals<ContentState<*>>(
            ContentState.Empty,
            schedule.toContentState { value: String -> value.isEmpty() || value == "sin-clases" },
        )
        assertIs<ContentState.Content<String>>(
            schedule.toContentState { value: String -> value.isEmpty() },
        )
    }

    @Test
    @DisplayName("Every failure carries a message that is safe to show a user")
    fun failuresAreUserPresentable() {
        val errors = listOf(
            AppError.Network(),
            AppError.Http(401),
            AppError.Http(404),
            AppError.Http(503),
            AppError.Http(418),
            AppError.Serialization(),
            AppError.Unknown(),
        )

        errors.forEach { error ->
            assertTrue(error.userMessage.isNotBlank(), "$error has no user message")
            assertTrue(
                error.userMessage.none { it == '{' || it == '}' },
                "${error.userMessage} looks like a raw technical string",
            )
        }
    }
}
