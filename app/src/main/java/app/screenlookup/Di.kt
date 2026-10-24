package app.screenlookup

import android.app.Application
import android.content.Context
import app.screenlookup.ocr.LocalOcrBridge
import app.screenlookup.ocr.MeikiOcrBridge
import app.screenlookup.ocr.ModelDownloader
import app.screenlookup.ocr.ModelVersionResolver
import app.screenlookup.ocr.OcrProviderImpl
import app.screenlookup.ocr.PaddleOcrBridge
import chimahon.DictionaryRepository
import chimahon.audio.WordAudioPreferences
import chimahon.audio.WordAudioService
import chimahon.dictionary.ui.DictionaryPreferences
import chimahon.dictionary.ui.DictionaryUiPreferences
import chimahon.gemini.GeminiPreferences
import chimahon.gemini.GeminiService
import chimahon.ocr.LensClient
import chimahon.ocr.OcrProvider
import chimahon.preferences.PreferenceStore
import chimahon.preferences.SharedPreferencesPreferenceStore
import okhttp3.OkHttpClient
import java.io.File

object Di {
    lateinit var app: Application
        private set

    val prefs: PreferenceStore by lazy {
        SharedPreferencesPreferenceStore(
            app.getSharedPreferences("screen_lookup_prefs", Context.MODE_PRIVATE),
        )
    }

    val httpClient: OkHttpClient by lazy { OkHttpClient() }

    val sharedPrefs by lazy {
        app.getSharedPreferences("screen_lookup_prefs", Context.MODE_PRIVATE)
    }

    val dictionary: DictionaryRepository by lazy {
        DictionaryRepository(
            externalFilesDir = app.getExternalFilesDir(null),
            koreanParserMode = { dictionaryPreferences.koreanParserMode().get() },
        )
    }

    val dictionaryPreferences: DictionaryPreferences by lazy { DictionaryPreferences(prefs) }

    val dictionaryUiPreferences: DictionaryUiPreferences by lazy {
        object : DictionaryUiPreferences {
            override val themeMode: String
                get() = prefs.getString("pref_theme_mode_key", "SYSTEM").get().lowercase()
            override val appAmoled: Boolean
                get() = prefs.getBoolean("pref_theme_dark_amoled_key", false).get()
            override val appColorTheme: Int
                get() = prefs.getInt("pref_color_theme", 0xFFDF0090.toInt()).get()
        }
    }

    val wordAudioService: WordAudioService by lazy {
        WordAudioService(
            context = app,
            preferences = object : WordAudioPreferences {
                override val enabled: Boolean
                    get() = prefs.getBoolean("pref_word_audio_enabled", true).get()
                override val autoplay: Boolean
                    get() = prefs.getBoolean("pref_word_audio_autoplay", false).get()
                override val sourcesJson: String
                    get() = prefs.getString("pref_word_audio_sources", "[]").get()
                override var localPath: String
                    get() = prefs.getString("pref_word_audio_local_path", "").get()
                    set(value) { prefs.getString("pref_word_audio_local_path", "").set(value) }
                override var localUri: String
                    get() = prefs.getString("pref_word_audio_local_uri", "").get()
                    set(value) { prefs.getString("pref_word_audio_local_uri", "").set(value) }
                override val localEnabled: Boolean
                    get() = prefs.getBoolean("pref_word_audio_local_enabled", false).get()
            },
            httpClient = httpClient,
        )
    }

    val geminiPreferences: GeminiPreferences by lazy {
        object : GeminiPreferences {
            override val geminiApiKey: String get() = dictionaryPreferences.geminiApiKey().get()
            override val geminiModel: String get() = dictionaryPreferences.geminiModel().get()
            override val geminiCustomEndpoint: String get() = dictionaryPreferences.geminiCustomEndpoint().get()
            override val geminiSendScreenshot: Boolean get() = dictionaryPreferences.geminiSendScreenshot().get()
            override val geminiPrompt: String get() = dictionaryPreferences.geminiPrompt().get()
            override val geminiFontSize: Int get() = dictionaryPreferences.geminiFontSize().get()
            override val geminiFontFamily: String get() = dictionaryPreferences.geminiFontFamily().get()
        }
    }

    val geminiService: GeminiService by lazy { GeminiService(httpClient, geminiPreferences) }

    val lensClient: LensClient by lazy { LensClient(httpClient) }

    val localOcrBridge: LocalOcrBridge by lazy { LocalOcrBridge(app) }
    val paddleOcrBridge: PaddleOcrBridge by lazy { PaddleOcrBridge(app) }
    val meikiBridge: MeikiOcrBridge by lazy { MeikiOcrBridge(app) }

    val modelVersionResolver: ModelVersionResolver by lazy { ModelVersionResolver(httpClient) }

    val modelDownloader: ModelDownloader by lazy {
        ModelDownloader(
            context = app,
            httpClient = httpClient,
            versionResolver = modelVersionResolver,
            preferenceStore = prefs,
        )
    }

    val ocrProvider: OcrProvider by lazy {
        OcrProviderImpl(
            engineTypeGetter = { dictionaryPreferences.ocrEngine().get() },
            local = if (BuildConfig.HAS_LOCAL_OCR) localOcrBridge else null,
            paddle = if (BuildConfig.HAS_LOCAL_OCR) paddleOcrBridge else null,
            meiki = if (BuildConfig.HAS_LOCAL_OCR) meikiBridge else null,
            downloader = modelDownloader,
            lens = lensClient,
        )
    }


    /** Scans disk for dictionary folder names under each type subdir. */
    fun scanDictionaryNames(): List<String> {
        val dir = File(app.getExternalFilesDir(null), "dictionaries")
        if (!dir.isDirectory) return emptyList()
        return listOf("term", "frequency", "pitch", "kanji")
            .flatMap { type ->
                File(dir, type).listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { it.name }
                    .orEmpty()
            }
            .distinct()
            .sorted()
    }

    /**
     * Registers any dictionary on disk that isn't yet in the active profile's
     * dictionaryOrder. Lookups fail silently with "No results" when a dict
     * is on disk but not in the profile, so this is called at startup and
     * after every import. Returns the number of names added.
     */
    fun reconcileDictionariesWithDisk(): Int {
        val diskNames = scanDictionaryNames()
        if (diskNames.isEmpty()) return 0
        val prefs = dictionaryPreferences
        val profile = prefs.profileStore.getActiveProfile()
        val missing = diskNames.filter { it !in profile.dictionaryOrder }
        if (missing.isEmpty()) return 0
        prefs.profileStore.updateProfile(
            profile.copy(dictionaryOrder = profile.dictionaryOrder + missing),
        )
        return missing.size
    }

    fun init(application: Application) {
        app = application
    }
}
