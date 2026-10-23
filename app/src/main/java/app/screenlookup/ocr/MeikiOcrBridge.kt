package app.screenlookup.ocr

import android.content.Context
import android.util.Log
import chimahon.ocr.EngineLine
import chimahon.ocr.OcrEngine
import chimahon.ocr.OcrLanguage
import app.screenlookup.BuildConfig

class MeikiOcrBridge(private val context: Context) {

    private var engine: OcrEngine? = null

    val isAvailable: Boolean get() = BuildConfig.HAS_LOCAL_OCR

    @Volatile
    var isInitialized: Boolean = false
        private set

    fun init(): Boolean {
        if (!isAvailable) return false
        if (isInitialized) return true
        try {
            val clazz = Class.forName("chimahon.local.ocr.MeikiOcrEngine")
            val ctor = clazz.getConstructor(Context::class.java)
            val instance = ctor.newInstance(context) as OcrEngine
            clazz.getMethod("init").invoke(instance)
            val ok = clazz.getMethod("isInitialized").invoke(instance) as Boolean
            if (ok) {
                engine = instance
                isInitialized = true
                Log.d(TAG, "MeikiOcrEngine initialized")
            } else {
                Log.w(TAG, "MeikiOcrEngine.init returned not initialized")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "failed to init MeikiOcrEngine", e)
        }
        return isInitialized
    }

    suspend fun recognize(bytes: ByteArray, language: OcrLanguage): List<EngineLine> {
        if (!isInitialized) return emptyList()
        return engine?.recognize(bytes, language) ?: emptyList()
    }

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
        private const val TAG = "MeikiOcrBridge"
    }
}
