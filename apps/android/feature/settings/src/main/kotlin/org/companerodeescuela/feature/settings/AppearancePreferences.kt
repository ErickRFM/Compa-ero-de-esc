package org.companerodeescuela.feature.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppearanceSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val textScale: Float = 1f,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
)

@Singleton
class AppearancePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppearanceSettings> = _state.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit { putString(KEY_THEME, mode.name) }
        _state.update { it.copy(themeMode = mode) }
    }

    fun setTextScale(scale: Float) {
        val normalized = scale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        preferences.edit { putFloat(KEY_TEXT_SCALE, normalized) }
        _state.update { it.copy(textScale = normalized) }
    }

    fun setReducedMotion(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_REDUCED_MOTION, enabled) }
        _state.update { it.copy(reducedMotion = enabled) }
    }

    fun setHighContrast(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_HIGH_CONTRAST, enabled) }
        _state.update { it.copy(highContrast = enabled) }
    }

    private fun read(): AppearanceSettings =
        AppearanceSettings(
            themeMode = runCatching {
                AppThemeMode.valueOf(
                    preferences.getString(KEY_THEME, AppThemeMode.SYSTEM.name)
                        ?: AppThemeMode.SYSTEM.name,
                )
            }.getOrDefault(AppThemeMode.SYSTEM),
            textScale = preferences.getFloat(KEY_TEXT_SCALE, 1f)
                .coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
            reducedMotion = preferences.getBoolean(KEY_REDUCED_MOTION, false),
            highContrast = preferences.getBoolean(KEY_HIGH_CONTRAST, false),
        )

    private companion object {
        const val PREFS_NAME = "appearance_preferences"
        const val KEY_THEME = "theme"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_REDUCED_MOTION = "reduced_motion"
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val MIN_TEXT_SCALE = 0.9f
        const val MAX_TEXT_SCALE = 1.2f
    }
}
