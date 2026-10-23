package chimahon.dictionary.ui

import android.content.Context
import chimahon.DictionaryPaths
import chimahon.anki.AnkiProfile
import java.io.File

/**
 * Resolves the on-disk paths for each dictionary type based on the
 * active profile's dictionaryOrder/enabledDictionaries.
 *
 * The reference implementation lived in :app; kept here so :dictionary-ui
 * can call it without an upward dependency.
 */
fun getDictionaryPaths(context: Context, profile: AnkiProfile): DictionaryPaths {
    val root = File(context.getExternalFilesDir(null), "dictionaries")
    val order = profile.dictionaryOrder
    val enabled = profile.enabledDictionaries.ifEmpty { order.toSet() }

    fun pathsFor(type: String) = order
        .filter { it in enabled }
        .map { File(File(root, type), it) }
        .filter { it.isDirectory }
        .map { it.absolutePath }

    return DictionaryPaths(
        termPaths = pathsFor("term"),
        freqPaths = pathsFor("frequency"),
        pitchPaths = pathsFor("pitch"),
        kanjiPaths = pathsFor("kanji"),
    )
}
