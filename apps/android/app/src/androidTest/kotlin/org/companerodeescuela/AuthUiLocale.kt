package org.companerodeescuela

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** Each composition declares its locale; tests do not depend on emulator settings or execution order. */
@Composable internal fun AuthTestLocale(language: String = "es", content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
    CompositionLocalProvider(LocalContext provides context.createConfigurationContext(configuration), LocalConfiguration provides configuration, content = content)
}
