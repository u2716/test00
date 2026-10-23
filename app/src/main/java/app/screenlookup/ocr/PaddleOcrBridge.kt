package app.screenlookup.ocr

import android.content.Context
import android.util.Log
import chimahon.ocr.EngineLine
import chimahon.ocr.OcrEngine
import chimahon.ocr.OcrLanguage
import app.screenlookup.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PaddleOcrBridge(private val context: Context) {

    private var engine: OcrEngine? = null
    private val engineMutex = Mutex()

    val isAvailable: Boolean get() = BuildConfig.HAS_LOCAL_OCR

    @Volatile
    var isInitialized: Boolean = false
        private set

    @Synchronized
    fun init(): Boolean {
        if (!isAvailable) return false
        if (isInitialized) return true
        try {
            val clazz = Class.forName("chimahon.local.ocr.PaddleOcrEngine")
            val ctor = clazz.getConstructor(Context::class.java)
            val instance = ctor.newInstance(context) as OcrEngine
            clazz.getMethod("init").invoke(instance)
            val ok = clazz.getMethod("isInitialized").invoke(instance) as Boolean
            if (ok) {
                engine = instance
                isInitialized = true
                Log.d(TAG, "PaddleOcrEngine initialized")
            } else {
                Log.w(TAG, "PaddleOcrEngine.init returned not initialized")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "failed to init PaddleOcrEngine", e)
        }
        return isInitialized
    }

    suspend fun recognize(bytes: ByteArray, language: OcrLanguage): List<EngineLine> =
        engineMutex.withLock {
            if (!isInitialized && !init()) return@withLock emptyList()
            engine?.recognize(bytes, language) ?: emptyList()
        }

    suspend fun invalidateModels() = engineMutex.withLock { destroy() }

    @Synchronized
    fun destroy() {
        engine?.let { e ->
            try {
                e.javaClass.getMethod("destroy").invoke(e)
            } catch (_: Exception) {
            }
        }
        engine = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "PaddleOcrBridge"
    }
}
