package org.companerodeescuela

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.designsystem.v8.V8RedPrimaryButton
import org.companerodeescuela.core.designsystem.v8.v8ResolvedColorScheme
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.ProvideCompaneroMotionPreferences
import org.companerodeescuela.core.navigation.CompaneroScaffold
import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.core.navigation.TopLevelDestination
import org.companerodeescuela.core.navigation.navigateToTopLevel
import org.companerodeescuela.feature.home.HomeShortcutGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class MotionDesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun largeTextShortcutsKeepFourDistinctDestinationsReachable() {
        val calls = IntArray(4)
        val scale = mutableStateOf(1f)
        var observedScale = 0f
        compose.setContent {
            CompaneroTheme(darkTheme = true) {
                val density = LocalDensity.current
                val colors = v8ResolvedColorScheme(MaterialTheme.colorScheme, false)
                MaterialTheme(colorScheme = colors) {
                    CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                        val actual = LocalDensity.current.fontScale
                        SideEffect { observedScale = actual }
                        Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
                            Box(
                                Modifier
                                    .width(320.dp)
                                    .height(560.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                HomeShortcutGrid(
                                    { calls[0]++ },
                                    { calls[1]++ },
                                    { calls[2]++ },
                                    { calls[3]++ },
                                    Modifier.testTag("shortcut_cards"),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Tareas").assertIsDisplayed()
        capture("shortcuts-normal", "shortcut_cards")
        compose.runOnIdle { scale.value = 2f }
        compose.waitForIdle()
        assertEquals(2f, observedScale, 0f)
        listOf("Horario", "Asistencia", "Avisos", "Tareas").forEach {
            compose
                .onNodeWithText(it)
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
        }
        assertTrue(calls.contentEquals(intArrayOf(1, 1, 1, 1)))
        capture("shortcuts-large-200")
    }

    @Test
    fun pressFeedbackPreservesCancelAndSingleClick() {
        var calls = 0
        button(false) { calls++ }
        capture("button-resting", "motion_button")
        val before =
            compose
                .onNodeWithTag("motion_button")
                .fetchSemanticsNode()
                .boundsInRoot.width
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("motion_button").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(180)
        val pressed =
            compose
                .onNodeWithTag("motion_button")
                .fetchSemanticsNode()
                .boundsInRoot.width
        assertTrue("A held primary action has visible press feedback", pressed < before - 1f)
        capture("button-pressed", "motion_button")
        compose.onNodeWithTag("motion_button").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(180)
        assertEquals(
            before,
            compose
                .onNodeWithTag("motion_button")
                .fetchSemanticsNode()
                .boundsInRoot.width,
            0.5f,
        )
        assertEquals(0, calls)
        compose.onNodeWithTag("motion_button").performTouchInput { down(Offset(1f, centerY)) }
        compose.mainClock.advanceTimeBy(180)
        compose.onNodeWithTag("motion_button").performTouchInput { up() }
        assertEquals("Press feedback must preserve an edge tap", 1, calls)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("motion_button").performClick()
        assertEquals(2, calls)
    }

    @Test
    fun reducedMotionKeepsPressedControlStationary() {
        var calls = 0
        button(true) { calls++ }
        val before = compose.onNodeWithTag("motion_button").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("motion_button").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(180)
        assertEquals(before, compose.onNodeWithTag("motion_button").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("motion_button").performTouchInput { up() }
        assertEquals(1, calls)
    }

    @Test
    fun reducedMotionNavigationDoesNotRetainOutgoingScreen() {
        val nav = scaffold()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.navigate(Destination.Profile.route) }
        settleWithoutAnimation { compose.onAllNodesWithTag("home_content").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("profile_content").assertIsDisplayed()
        compose.onNodeWithTag("home_content").assertDoesNotExist()
        compose.runOnIdle { nav.popBackStack() }
        settleWithoutAnimation { compose.onAllNodesWithTag("profile_content").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("home_content").assertIsDisplayed()
        compose.onNodeWithTag("profile_content").assertDoesNotExist()
    }

    @Test
    fun reducedMotionSelectionImmediatelyMatchesSelectedTab() {
        val nav = scaffold()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { nav.navigateToTopLevel(Destination.Schedule) }
        settleWithoutAnimation {
            val selected = compose.onNodeWithContentDescription("Horario").fetchSemanticsNode()
            val pill = compose.onNodeWithTag("navigation_selection").fetchSemanticsNode().boundsInRoot
            selected.config.contains(SemanticsProperties.Selected) &&
                selected.config[SemanticsProperties.Selected] &&
                kotlin.math.abs(selected.boundsInRoot.left - pill.left) < 0.5f
        }
        compose.onNodeWithContentDescription("Horario").assertIsSelected()
        val selected = compose.onNodeWithContentDescription("Horario").fetchSemanticsNode().boundsInRoot
        val pill = compose.onNodeWithTag("navigation_selection").fetchSemanticsNode().boundsInRoot
        assertEquals(selected.left, pill.left, 0.5f)
    }

    // Navigation/animateAsState schedule separate collection, composition and completion effects.
    // Drain bookkeeping by state, but never advance through even the shortest spatial animation.
    private fun settleWithoutAnimation(condition: () -> Boolean) {
        val start = compose.mainClock.currentTime
        repeat(6) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (condition()) {
                assertTrue(compose.mainClock.currentTime - start < CompaneroMotionDuration.FAST)
                return
            }
        }
        assertTrue("Reduced-motion state did not settle before the shortest animation could finish", condition())
    }

    private fun capture(
        name: String,
        tag: String? = null,
    ) {
        compose.waitForIdle()
        val node = if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)
        val bitmap = node.captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "audit-design-motion").apply { mkdirs() }
        try {
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun button(
        reduced: Boolean,
        onClick: () -> Unit,
    ) {
        compose.setContent {
            CompaneroTheme {
                ProvideCompaneroMotionPreferences(reduced) {
                    V8RedPrimaryButton("Continuar QA", onClick, Modifier.width(240.dp).testTag("motion_button"))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun scaffold(): NavHostController {
        var nav: NavHostController? = null
        compose.setContent {
            CompaneroTheme {
                ProvideCompaneroMotionPreferences(true) {
                    val controller = rememberNavController()
                    SideEffect { nav = controller }
                    CompaneroScaffold(controller, Destination.Home, listOf(TopLevelDestination.Home, TopLevelDestination.Schedule)) {
                        composable(Destination.Home.route) { Text("Hoy QA", Modifier.testTag("home_content")) }
                        composable(Destination.Schedule.route) { Text("Horario QA") }
                        composable(Destination.Profile.route) { Text("Perfil QA", Modifier.testTag("profile_content")) }
                    }
                }
            }
        }
        compose.waitForIdle()
        return requireNotNull(nav)
    }
}
