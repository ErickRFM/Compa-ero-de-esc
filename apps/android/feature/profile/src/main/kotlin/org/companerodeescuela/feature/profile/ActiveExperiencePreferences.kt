package org.companerodeescuela.feature.profile

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.companerodeescuela.core.navigation.AppExperience

@Singleton
class ActiveExperiencePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(userId: String): AppExperience? =
        preferences.getString(key(userId), null)
            ?.let { raw -> runCatching { AppExperience.valueOf(raw) }.getOrNull() }

    fun write(userId: String, experience: AppExperience) {
        preferences.edit { putString(key(userId), experience.name) }
    }

    fun clear(userId: String) {
        preferences.edit { remove(key(userId)) }
    }

    private fun key(userId: String): String = "active_experience_" + userId.trim()

    private companion object {
        const val PREFS_NAME = "role_experience_preferences"
    }
}
