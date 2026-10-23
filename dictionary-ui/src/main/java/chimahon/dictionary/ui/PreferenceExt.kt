package chimahon.dictionary.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import chimahon.preferences.Preference

/**
 * Collects a Preference<T> as Compose State.
 *
 * The reference had tachiyomi.presentation.core.util.collectAsState which
 * wrapped this exact logic. Reimplemented here so :dictionary-ui does not
 * depend on :presentation-core.
 */
@Composable
fun <T> Preference<T>.collectAsState(): State<T> {
    val flow = remember(this) { this.changes() }
    return flow.collectAsState(initial = this.get())
}
