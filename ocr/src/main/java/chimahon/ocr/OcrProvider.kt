package chimahon.ocr

/**
 * Bridge to a local OCR engine (:ocr-engines). :ocr does not depend
 * on :ocr-engines — the app supplies an implementation when one is
 * available (BuildConfig.HAS_LOCAL_OCR) or null otherwise.
 */
interface OcrBridge {
    val isAvailable: Boolean
    val isInitialized: Boolean
    suspend fun init(): Boolean
    suspend fun recognize(bytes: ByteArray, language: OcrLanguage): List<EngineLine>
}

/**
 * Trigger for downloading the optional model bundles. The app wires
 * this to its ModelDownloader; :ocr just calls the trigger when a
 * local engine is selected but its model is missing.
 */
interface OcrModelTrigger {
    val isDownloaded: Boolean
    val isPaddleDownloaded: Boolean
    val isMeikiDownloaded: Boolean
    val isVocrDownloaded: Boolean
    fun triggerDownload(force: Boolean = false)
    fun triggerPaddleDownload(force: Boolean = false)
    fun triggerMeikiDownload()
    fun triggerVocrDownload(force: Boolean = false)
}

/**
 * Everything :ocr needs from the host app. Declared by :ocr, supplied
 * by :app, :dictionary-ui, or any other caller.
 *
 * Replaces the reference implementation's `Injekt.get<...>` calls
 * (DictionaryPreferences, LocalOcrBridge, PaddleOcrBridge,
 * MeikiOcrBridge, ModelDownloader, LensClient).
 */
interface OcrProvider {
    fun getEngineType(): String
    fun localBridge(): OcrBridge?
    fun paddleBridge(): OcrBridge?
    fun meikiBridge(): OcrBridge?
    fun vocrBridge(): OcrBridge?
    fun modelTrigger(): OcrModelTrigger?
    fun lensClient(): LensClient
}
