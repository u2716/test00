package chimahon.dictionary.ui

import chimahon.audio.WordAudioService
import chimahon.dictionary.ui.DictionaryPreferences

/**
 * Process-wide singletons for :dictionary-ui. Replaces Injekt.get<T>()
 * for the three dependencies this module needs but cannot obtain via
 * constructor injection (because some call sites are not composables).
 *
 * :app wires these once in App.onCreate():
 *
 *   DictionaryUiSingletons.dictionaryPreferences = Di.dictionaryPreferences
 *   DictionaryUiSingletons.dictionaryUiPreferences = Di.dictionaryUiPreferences
 *   DictionaryUiSingletons.wordAudioService = Di.wordAudioService
 *
 * Accessors use `!!` — if the app forgets to wire them, the popup will
 * crash at the first use with a clear NPE message. That is intentional:
 * silently using nulls would produce much worse bugs.
 */
object DictionaryUiSingletons {
    @Volatile
    var dictionaryPreferences: DictionaryPreferences? = null

    @Volatile
    var dictionaryUiPreferences: DictionaryUiPreferences? = null

    @Volatile
    var wordAudioService: WordAudioService? = null
}
