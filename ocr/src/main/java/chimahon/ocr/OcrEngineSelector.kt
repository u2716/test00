package chimahon.ocr

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import java.io.ByteArrayOutputStream

enum class OcrEngineType {
    CLOUD,
    LOCAL,
    PADDLE,
    MEIKI,
    VOCR,
    ;

    companion object {
        fun fromPreference(value: String): OcrEngineType = when (value) {
            "local" -> LOCAL
            "paddle" -> PADDLE
            "meiki" -> MEIKI
            else -> CLOUD
        }
    }
}

suspend fun recognizePage(
    bytes: ByteArray,
    language: OcrLanguage,
    provider: OcrProvider,
    engineType: OcrEngineType? = null,
): List<OcrResult> {
    val resolved = engineType ?: OcrEngineType.fromPreference(provider.getEngineType())
    return when (resolved) {
        OcrEngineType.LOCAL ->
            recognizeWithBridge(bytes, language, provider.localBridge(), provider.modelTrigger(), isLens = true, isPaddle = false)
        OcrEngineType.PADDLE ->
            recognizeWithBridge(bytes, language, provider.paddleBridge(), provider.modelTrigger(), isLens = false, isPaddle = true)
        OcrEngineType.MEIKI ->
            recognizeWithBridge(bytes, OcrLanguage.JAPANESE, provider.meikiBridge(), provider.modelTrigger(), isLens = false, isPaddle = false)
        OcrEngineType.VOCR ->
            recognizeWithBridge(bytes, OcrLanguage.JAPANESE, provider.vocrBridge(), provider.modelTrigger(), isLens = false, isPaddle = false, isVocr = true)
        OcrEngineType.CLOUD ->
            provider.lensClient().getDebugOcrData(bytes = bytes, language = language).mergedResults
    }
}

suspend fun recognizePage(
    bitmap: Bitmap,
    language: OcrLanguage,
    provider: OcrProvider,
    engineType: OcrEngineType? = null,
): List<OcrResult> {
    val resolved = engineType ?: OcrEngineType.fromPreference(provider.getEngineType())
    return when (resolved) {
        OcrEngineType.LOCAL ->
            recognizeBitmapWithBridge(bitmap, language, provider.localBridge(), provider.modelTrigger(), isLens = true, isPaddle = false)
        OcrEngineType.PADDLE ->
            recognizeBitmapWithBridge(bitmap, language, provider.paddleBridge(), provider.modelTrigger(), isLens = false, isPaddle = true)
        OcrEngineType.MEIKI ->
            recognizeBitmapWithBridge(bitmap, OcrLanguage.JAPANESE, provider.meikiBridge(), provider.modelTrigger(), isLens = false, isPaddle = false)
        OcrEngineType.VOCR ->
            recognizeBitmapWithBridge(bitmap, OcrLanguage.JAPANESE, provider.vocrBridge(), provider.modelTrigger(), isLens = false, isPaddle = false, isVocr = true)
        OcrEngineType.CLOUD ->
            provider.lensClient().getDebugOcrData(bitmap = bitmap, language = language).mergedResults
    }
}

private suspend fun recognizeWithBridge(
    bytes: ByteArray,
    language: OcrLanguage,
    bridge: OcrBridge?,
    trigger: OcrModelTrigger?,
    isLens: Boolean,
    isPaddle: Boolean,
    isVocr: Boolean = false,
): List<OcrResult> {
    if (!ensureReady(bridge, trigger, isLens, isPaddle, isVocr)) return emptyList()
    return processImageWithChunks(bytes, language) { chunk ->
        val chunkBytes = withContext(Dispatchers.Default) { chunk.bitmap.toJpegBytes(85) }
        val lines = bridge!!.recognize(chunkBytes, language)
        chunk.bitmap.recycle()
        lines
    }
}

private suspend fun recognizeBitmapWithBridge(
    bitmap: Bitmap,
    language: OcrLanguage,
    bridge: OcrBridge?,
    trigger: OcrModelTrigger?,
    isLens: Boolean,
    isPaddle: Boolean,
    isVocr: Boolean = false,
): List<OcrResult> {
    if (!ensureReady(bridge, trigger, isLens, isPaddle, isVocr)) return emptyList()
    return processImageWithChunks(bitmap, language) { chunk ->
        val chunkBytes = withContext(Dispatchers.Default) { chunk.bitmap.toJpegBytes(85) }
        val lines = bridge!!.recognize(chunkBytes, language)
        if (chunk.bitmap !== bitmap) chunk.bitmap.recycle()
        lines
    }
}

private suspend fun ensureReady(
    bridge: OcrBridge?,
    trigger: OcrModelTrigger?,
    isLens: Boolean,
    isPaddle: Boolean,
    isVocr: Boolean = false,
): Boolean {
    if (trigger != null) {
        val downloaded = when {
            isLens -> trigger.isDownloaded
            isPaddle -> trigger.isPaddleDownloaded
            isVocr -> trigger.isVocrDownloaded
            else -> trigger.isMeikiDownloaded
        }
        if (!downloaded) {
            logcat("OcrEngineSelector", LogPriority.WARN) { "engine selected but model not downloaded" }
            when {
                isLens -> trigger.triggerDownload()
                isPaddle -> trigger.triggerPaddleDownload()
                isVocr -> trigger.triggerVocrDownload()
                else -> trigger.triggerMeikiDownload()
            }
            return false
        }
    }
    if (bridge == null || !bridge.isAvailable) return false
    if (!bridge.isInitialized && !bridge.init()) {
        logcat("OcrEngineSelector", LogPriority.WARN) { "engine failed to initialize" }
        return false
    }
    return true
}

private fun Bitmap.toJpegBytes(quality: Int): ByteArray {
    return ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }
}
