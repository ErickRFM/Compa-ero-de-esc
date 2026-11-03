package org.companerodeescuela

import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.companerodeescuela.core.designsystem.theme.CompaneroAccentPalette
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroDynamicColor
import org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled
import org.companerodeescuela.core.designsystem.v8.V8LoadingState
import org.companerodeescuela.core.designsystem.v8.v8ResolvedColorScheme
import org.companerodeescuela.core.motion.ProvideCompaneroMotionPreferences
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.core.ui.component.ContentStateHost
import org.companerodeescuela.core.ui.state.ContentState
import org.companerodeescuela.feature.home.HomeAgendaCard
import org.companerodeescuela.feature.home.HomeUiState
import org.companerodeescuela.feature.home.TodayOverview
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.feature.settings.AppThemeMode
import org.companerodeescuela.feature.settings.AppearancePreferences
import org.companerodeescuela.feature.settings.AppearanceSettings
import org.companerodeescuela.feature.settings.AppearanceSettingsScreen
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class PersonalizationUxUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appearanceChoicesFitAt320dpAndCombined240PercentText() {
        val settings = mutableStateOf(AppearanceSettings(textScale = 1.2f))
        var actualFontScale = 0f
        setUi {
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density, 2f)) {
                CompaneroTheme(fontScaleMultiplier = settings.value.textScale) {
                    val actual = LocalDensity.current.fontScale
                    SideEffect { actualFontScale = actual }
                    Box(Modifier.width(320.dp).height(560.dp).testTag("appearance_viewport")) {
                        AppearanceSettingsScreen(
                            settings = settings.value,
                            onThemeMode = { settings.value = settings.value.copy(themeMode = it) },
                            onLanguage = { settings.value = settings.value.copy(language = it) },
                            onTextScale = { settings.value = settings.value.copy(textScale = it) },
                            onReducedMotion = {},
                            onHighContrast = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(2.4f, actualFontScale, 0.001f)
        val viewport = compose.onNodeWithTag("appearance_viewport").fetchSemanticsNode().boundsInRoot
        listOf("Oscuro", "English", "Grande").forEach { label ->
            val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$label must fit the viewport", bounds.left >= viewport.left && bounds.right <= viewport.right + 0.5f)
        }
        capture("appearance-320-240")
        compose.onNodeWithText("Oscuro").performScrollTo().performClick()
        assertEquals(AppThemeMode.DARK, settings.value.themeMode)
    }

    @Test
    fun reducedMotionSwitchHasNameAndWholeRowTogglesOnce() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "qa_appearance_${System.nanoTime()}_"
        val files = mutableSetOf<String>()
        val context =
            object : ContextWrapper(base) {
                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences {
                    val scoped = namespace + name
                    files += scoped
                    return base.getSharedPreferences(scoped, mode)
                }
            }
        val preferences = AppearancePreferences(context)
        var changes = 0
        try {
            setUi {
                val settings by preferences.state.collectAsState()
                CompaneroTheme {
                    AppearanceSettingsScreen(
                        settings = settings,
                        onThemeMode = preferences::setThemeMode,
                        onTextScale = preferences::setTextScale,
                        onReducedMotion = {
                            changes++
                            preferences.setReducedMotion(it)
                        },
                        onHighContrast = preferences::setHighContrast,
                    )
                }
            }
            capture("appearance-normal")
            compose
                .onNode(hasText("Reducir animaciones") and isToggleable())
                .performScrollTo()
                .performClick()
                .assertIsOn()
            assertEquals(1, changes)
            capture("appearance-accessibility")
            compose.runOnIdle {
                preferences.setThemeMode(AppThemeMode.DARK)
                preferences.setAccentPalette(CompaneroAccentPalette.VIOLET)
                preferences.setDynamicColor(true)
                preferences.setCampusBackdropEnabled(false)
                preferences.setTextScale(1.2f)
                preferences.setHighContrast(true)
            }
            assertEquals(
                AppearanceSettings(
                    themeMode = AppThemeMode.DARK,
                    accentPalette = CompaneroAccentPalette.VIOLET,
                    dynamicColor = true,
                    campusBackdropEnabled = false,
                    textScale = 1.2f,
                    reducedMotion = true,
                    highContrast = true,
                ),
                AppearancePreferences(context).state.value,
            )
        } finally {
            files.forEach(base::deleteSharedPreferences)
        }
    }

    @Test
    fun loadingAnnouncesStateAndReleasesActualContent() {
        val state = mutableStateOf<ContentState<String>>(ContentState.Loading)
        setUi {
            CompaneroTheme {
                ContentStateHost(state.value, onRetry = {}) { Text(it) }
            }
        }
        compose.onNodeWithText("Cargando contenido").assertIsDisplayed()
        compose.runOnIdle { state.value = ContentState.Content("Contenido listo") }
        compose.onNodeWithText("Contenido listo").assertIsDisplayed()
        compose.onNodeWithText("Cargando contenido").assertDoesNotExist()
    }

    @Test
    fun firstAgendaLoadDoesNotPretendEmptyAndRefreshKeepsCachedActions() {
        val state = mutableStateOf(HomeUiState())
        var schedule = 0
        var attendance = 0
        setUi {
            CompaneroTheme {
                HomeAgendaCard(state.value, { schedule++ }, { attendance++ }, Modifier.testTag("home_agenda"))
            }
        }
        compose.onNodeWithText("Cargando agenda").assertIsDisplayed()
        compose.onNodeWithText("Sin clases próximas").assertDoesNotExist()
        capture("home-loading", "home_agenda")
        val lesson = ScheduleEntry("qa", "MAT", "Matemáticas", "A", "Docente", "MONDAY", "08:00", "09:00", "Aula 1")
        val overview = TodayOverview("Ana", true, lesson, null, listOf(lesson))
        compose.runOnIdle { state.value = HomeUiState(loading = true, overview = overview, fromCache = true) }
        compose.onNodeWithText("Matemáticas").assertIsDisplayed()
        compose.onNodeWithText("Cargando agenda").assertDoesNotExist()
        compose.onNodeWithText("Pasar lista").assertDoesNotExist()
        compose.onNodeWithText("Ver mi horario").performClick()
        assertEquals(1, schedule)
        assertEquals(0, attendance)
        capture("home-cached-refresh", "home_agenda")
        compose.runOnIdle { state.value = state.value.copy(loading = false, fromCache = false) }
        compose.onNodeWithText("Pasar lista").performClick()
        assertEquals(1, attendance)
        compose.runOnIdle { state.value = HomeUiState(loading = false, errorMessage = "Sin conexión") }
        compose.onNodeWithText("Agenda no disponible").assertIsDisplayed()
        compose.onNodeWithText("Sin clases próximas").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = HomeUiState(loading = true) }
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Sin clases próximas").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun profileFollowsSixAccentsBothThemesContrastAndDynamicColors() {
        val variant = mutableStateOf(Variant(CompaneroAccentPalette.CRIMSON, false))
        var parentColors: ColorScheme? = null
        setUi {
            val current = variant.value
            val device = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(device.density, 1f)) {
                CompaneroTheme(
                    darkTheme = current.dark,
                    accentPalette = current.accent,
                    highContrast = current.contrast,
                    dynamicColor = current.dynamic,
                ) {
                    val parent = MaterialTheme.colorScheme
                    SideEffect { parentColors = parent }
                    val colors =
                        v8ResolvedColorScheme(
                            MaterialTheme.colorScheme,
                            current.contrast,
                            current.accent,
                            LocalCompaneroDynamicColor.current,
                        )
                    MaterialTheme(colorScheme = colors) {
                        CompositionLocalProvider(LocalV8GlassEnabled provides true) {
                            key(current) {
                                Box(
                                    Modifier
                                        .width(360.dp)
                                        .height(640.dp)
                                        .background(colors.background)
                                        .testTag("profile_matrix"),
                                ) {
                                    ProfileScreen("Ana López", setOf(UserRole.STUDENT, UserRole.TEACHER), onAppearance = {
                                    }, onLogout = {}, activeExperience = AppExperience.STUDENT)
                                }
                            }
                        }
                    }
                }
            }
        }

        fun verify(name: String) {
            compose.waitForIdle()
            val parent = requireNotNull(parentColors)
            assertTrue("$name primary contrast", contrast(parent.primary, parent.onPrimary) >= 4.5)
            assertTrue("$name container contrast", contrast(parent.primaryContainer, parent.onPrimaryContainer) >= 4.5)
            compose.onNodeWithText("Perfil activo · Estudiante").assertIsDisplayed()
            compose.onNodeWithText("Tu cuenta, acceso y preferencias en un solo lugar.").assertDoesNotExist()
            compose.onNodeWithText("Sesión activa").assertDoesNotExist()
            capture(name, "profile_matrix")
        }
        for (dark in listOf(false, true)) {
            for (accent in CompaneroAccentPalette.entries) {
                compose.runOnIdle { variant.value = Variant(accent, dark) }
                verify("profile-${accent.name.lowercase()}-${if (dark) "dark" else "light"}")
            }
            compose.runOnIdle { variant.value = Variant(CompaneroAccentPalette.CRIMSON, dark, contrast = true) }
            verify("profile-contrast-${if (dark) "dark" else "light"}")
            if (Build.VERSION.SDK_INT >= 31) {
                compose.runOnIdle { variant.value = Variant(CompaneroAccentPalette.CRIMSON, dark, dynamic = true) }
                verify("profile-dynamic-${if (dark) "dark" else "light"}")
            }
        }
    }

    @Test
    fun profileKeepsActionsReachableWhenTextChangesTo240Percent() {
        val systemScale = mutableStateOf(1f)
        var actualScale = 0f
        var appearance = 0
        var switch = 0
        setUi {
            val device = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(device.density, systemScale.value)) {
                CompaneroTheme(fontScaleMultiplier = 1.2f, highContrast = true) {
                    val actual = LocalDensity.current.fontScale
                    SideEffect { actualScale = actual }
                    Box(Modifier.width(320.dp).height(560.dp)) {
                        ProfileScreen(
                            "Ana López con nombre largo",
                            setOf(UserRole.STUDENT, UserRole.TEACHER),
                            canSwitchExperience = true,
                            onSwitchExperience = { switch++ },
                            onAppearance = { appearance++ },
                            onLogout = {},
                            activeExperience = AppExperience.STUDENT,
                        )
                    }
                }
            }
        }
        compose.runOnIdle { systemScale.value = 2f }
        compose.waitForIdle()
        assertEquals(2.4f, actualScale, 0.001f)
        compose.onNodeWithText("Ana López con nombre largo").performScrollTo().assertIsDisplayed()
        capture("profile-large-240")
        compose.onNodeWithText("Tema y accesibilidad").performScrollTo().performClick()
        compose.onNodeWithText("Cambiar perfil activo").performScrollTo().performClick()
        assertEquals(1, appearance)
        assertEquals(1, switch)
    }

    @Test
    fun brandedLoadingIsStaticWithReducedMotion() {
        val reduced = mutableStateOf(true)
        var expectedInk = Color.Unspecified
        setUi {
            CompaneroTheme(darkTheme = true) {
                val ink = MaterialTheme.colorScheme.onBackground
                SideEffect { expectedInk = ink }
                ProvideCompaneroMotionPreferences(reduced.value) {
                    V8LoadingState(branded = true, title = "Abriendo tu espacio")
                }
            }
        }
        compose.onNodeWithText("Compañero").assertIsDisplayed()
        val labelLayout = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText("Compañero", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(labelLayout) }
        assertEquals(
            expectedInk.toArgb(),
            labelLayout
                .single()
                .layoutInput.style.color
                .toArgb(),
        )
        compose.mainClock.autoAdvance = false
        val before = pixels()
        compose.mainClock.advanceTimeBy(240)
        assertTrue("Reduced loading must remain visually stationary", before.contentEquals(pixels()))
        capture("startup-reduced")
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { reduced.value = false }
        capture("startup-normal")
    }

    @Test
    fun appearancePanelCentersAt840dpAndKeepsSelectionAfterResize() {
        val width = mutableStateOf(840.dp)
        val settings = mutableStateOf(AppearanceSettings())
        var expectedHeaderInk = Color.Unspecified
        var fixtureDensity = 1f
        setUi {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val availablePx = with(LocalDensity.current) { maxWidth.toPx() }
                val density = minOf(1f, availablePx / 860f)
                SideEffect { fixtureDensity = density }
                CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                    CompaneroTheme(darkTheme = settings.value.themeMode == AppThemeMode.DARK) {
                        val ink = MaterialTheme.colorScheme.onSurface
                        SideEffect { expectedHeaderInk = ink }
                        Box(Modifier.width(width.value).height(500.dp).testTag("appearance_window")) {
                            AppearanceSettingsScreen(settings.value, {
                                settings.value = settings.value.copy(themeMode = it)
                            }, onTextScale = {}, onReducedMotion = {}, onHighContrast = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val wide = compose.onNodeWithTag("appearance_preview").fetchSemanticsNode().boundsInRoot
        val window = compose.onNodeWithTag("appearance_window").fetchSemanticsNode().boundsInRoot
        assertTrue("Wide settings must honor the 720dp panel cap", wide.width / fixtureDensity <= 720.5f)
        assertTrue("Wide fixture must exercise more than a phone layout", wide.width / fixtureDensity > 600f)
        assertEquals(window.center.x, wide.center.x, 0.5f)
        capture("appearance-wide", "appearance_window")
        compose.onNodeWithText("Oscuro").performScrollTo().performClick()
        compose.runOnIdle { width.value = 320.dp }
        compose.onNodeWithText("Oscuro").performScrollTo().assertIsSelected()
        val compact =
            compose
                .onNodeWithTag("appearance_preview")
                .performScrollTo()
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(compact.width / fixtureDensity > 250f && compact.width / fixtureDensity <= 320.5f)
        val compactWindow = compose.onNodeWithTag("appearance_window").fetchSemanticsNode().boundsInRoot
        assertEquals(compactWindow.center.x, compact.center.x, 0.5f)
        val headingLayout = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText("Visualización", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(headingLayout) }
        assertEquals(
            expectedHeaderInk.toArgb(),
            headingLayout
                .single()
                .layoutInput.style.color
                .toArgb(),
        )
        capture("appearance-compact", "appearance_window")
    }

    private data class Variant(
        val accent: CompaneroAccentPalette,
        val dark: Boolean,
        val contrast: Boolean = false,
        val dynamic: Boolean = false,
    )

    private fun contrast(
        a: Color,
        b: Color,
    ): Double = (maxOf(a.luminance(), b.luminance()) + 0.05) / (minOf(a.luminance(), b.luminance()) + 0.05)

    private fun setUi(content: @Composable () -> Unit) {
        compose.setContent { AuthTestLocale { Box(Modifier.fillMaxSize().safeDrawingPadding()) { content() } } }
    }

    private fun pixels(): IntArray {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        return try {
            IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun capture(
        name: String,
        tag: String? = null,
    ) {
        compose.waitForIdle()
        val bitmap = (if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)).captureToImage().asAndroidBitmap()
        val directory =
            File(
                InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                "audit-personalization",
            ).apply { mkdirs() }
        try {
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }
}
