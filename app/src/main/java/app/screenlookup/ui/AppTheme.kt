package app.screenlookup.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.screenlookup.Di
import chimahon.dictionary.ui.collectAsState

/**
 * Resolves the app-level "app_theme" preference (system / light / dark) and
 * provides the matching Material 3 color scheme to everything inside.
 *
 * Use this in every Compose entry point - Activities and overlay windows -
 * so they all agree on the theme. Do NOT use a bare `MaterialTheme {}`,
 * which always renders the light scheme.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val themePref = remember { Di.prefs.getString("app_theme", "system") }
    val themeMode by themePref.collectAsState()

    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
