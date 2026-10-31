package app.screenlookup.ocr

import chimahon.ocr.LensClient
import chimahon.ocr.OcrBridge
import chimahon.ocr.OcrModelTrigger
import chimahon.ocr.OcrProvider

/**
 * Wires :ocr's OcrProvider interface to the concrete bridges and downloader.
 *
 * Bridges are conditionally present (BuildConfig.HAS_LOCAL_OCR); when the
 * ocr-engines module is absent, all three are null and only the cloud engine
 * works.
 */
class OcrProviderImpl(
    private val engineTypeGetter: () -> String,
    private val local: LocalOcrBridge?,
    private val paddle: PaddleOcrBridge?,
    private val meiki: MeikiOcrBridge?,
    private val vocr: VocrBridge?,
    private val downloader: ModelDownloader,
    private val lens: LensClient,
) : OcrProvider {

    override fun getEngineType(): String = engineTypeGetter()

    override fun localBridge(): OcrBridge? = local?.asBridge()

    override fun paddleBridge(): OcrBridge? = paddle?.asBridge()

    override fun meikiBridge(): OcrBridge? = meiki?.asBridge()
    override fun vocrBridge(): OcrBridge? = vocr?.asBridge()

    override fun modelTrigger(): OcrModelTrigger = downloader

    override fun lensClient(): LensClient = lens

    private fun VocrBridge.asBridge() = object : OcrBridge {
        override val isAvailable get() = this@asBridge.isAvailable
        override val isInitialized get() = this@asBridge.isInitialized
        override suspend fun init() = this@asBridge.init()
        override suspend fun recognize(bytes: ByteArray, language: chimahon.ocr.OcrLanguage) =
            this@asBridge.recognize(bytes, language)
    }

    private fun LocalOcrBridge.asBridge() = object : OcrBridge {
        override val isAvailable get() = this@asBridge.isAvailable
        override val isInitialized get() = this@asBridge.isInitialized
        override suspend fun init() = this@asBridge.init()
        override suspend fun recognize(bytes: ByteArray, language: chimahon.ocr.OcrLanguage) =
            this@asBridge.recognize(bytes, language)
    }

    private fun PaddleOcrBridge.asBridge() = object : OcrBridge {
        override val isAvailable get() = this@asBridge.isAvailable
        override val isInitialized get() = this@asBridge.isInitialized
        override suspend fun init() = this@asBridge.init()
        override suspend fun recognize(bytes: ByteArray, language: chimahon.ocr.OcrLanguage) =
            this@asBridge.recognize(bytes, language)
    }

    private fun MeikiOcrBridge.asBridge() = object : OcrBridge {
        override val isAvailable get() = this@asBridge.isAvailable
        override val isInitialized get() = this@asBridge.isInitialized
        override suspend fun init() = this@asBridge.init()
        override suspend fun recognize(bytes: ByteArray, language: chimahon.ocr.OcrLanguage) =
            this@asBridge.recognize(bytes, language)
    }
}
