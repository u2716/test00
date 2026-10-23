package app.screenlookup

import android.app.Application
import android.os.Build
import android.webkit.WebView

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Di.init(this)

        // Register any dictionaries already on disk into the active
        // profile so lookups can find them.
        runCatching { Di.reconcileDictionariesWithDisk() }

        chimahon.anki.AnkiCardCreator.initialize(
            wordAudioService = Di.wordAudioService,
            dictionaryRepository = Di.dictionary,
        )

        chimahon.dictionary.ui.DictionaryUiSingletons.dictionaryPreferences = Di.dictionaryPreferences
        chimahon.dictionary.ui.DictionaryUiSingletons.dictionaryUiPreferences = Di.dictionaryUiPreferences
        chimahon.dictionary.ui.DictionaryUiSingletons.wordAudioService = Di.wordAudioService

        chimahon.gemini.GeminiSingletons.geminiService = Di.geminiService

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName()
            if (packageName != process) {
                WebView.setDataDirectorySuffix(process)
            }
        }
    }
}
