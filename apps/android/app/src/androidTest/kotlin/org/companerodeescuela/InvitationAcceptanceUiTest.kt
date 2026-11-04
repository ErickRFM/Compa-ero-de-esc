package org.companerodeescuela

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.feature.auth.InvitationAcceptanceScreen
import org.companerodeescuela.feature.auth.SessionUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InvitationAcceptanceUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun verifiedAccountCanSubmitInvitationCode() {
        var submitted: Pair<String, String>? = null
        compose.setContent {
            CompaneroTheme {
                InvitationAcceptanceScreen(
                    state = SessionUiState(checking = false, authenticated = true, emailVerified = true),
                    onAccept = { id, code -> submitted = id to code },
                    onBack = {},
                )
            }
        }
        val id = "a".repeat(43)
        val code = "b".repeat(43)
        compose.onNodeWithTag("invitation_id").performScrollTo().performTextInput(id)
        compose.onNodeWithTag("invitation_code").performScrollTo().performTextInput(code)
        compose.onNodeWithTag("invitation_submit").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(id to code, submitted) }
    }

    @Test
    fun unverifiedAccountCannotRedeemInvitation() {
        var submitted = false
        compose.setContent {
            CompaneroTheme {
                InvitationAcceptanceScreen(
                    state = SessionUiState(checking = false, authenticated = true, emailVerified = false),
                    onAccept = { _, _ -> submitted = true },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("invitation_id").performScrollTo().performTextInput("a".repeat(43))
        compose.onNodeWithTag("invitation_code").performScrollTo().performTextInput("b".repeat(43))
        compose.onNodeWithTag("invitation_submit").assertExists()
        compose.onNodeWithTag("invitation_success").assertDoesNotExist()
        compose.runOnIdle { assertEquals(false, submitted) }
    }
}
