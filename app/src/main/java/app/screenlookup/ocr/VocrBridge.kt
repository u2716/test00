package app.screenlookup.ocr

import android.content.Context
import chimahon.ocr.EngineLine
import chimahon.ocr.OcrLanguage
import app.screenlookup.BuildConfig
import app.screenlookup.Di
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VocrBridge(private val context: Context) {
    private var engine: chimahon.ocr.OcrEngine? = null
    private val engineMutex = Mutex()

    val isAvailable: Boolean get() = BuildConfig.HAS_LOCAL_OCR
    @Volatile var isInitialized: Boolean = false; private set

    @Synchronized
    fun init(): Boolean {
        if (!isAvailable) return false
        if (isInitialized) return true
        return try {
            val tier = Di.dictionaryPreferences.vocrTier().get()
            val clazz = Class.forName("chimahon.local.ocr.VocrEngine")
            val ctor = clazz.getConstructor(Context::class.java, String::class.java)
            engine = ctor.newInstance(context, tier) as chimahon.ocr.OcrEngine
            isInitialized = true
            android.util.Log.i("VocrBridge", "init: tier=$tier")
            true
        } catch (e: Throwable) {
            android.util.Log.e("VocrBridge", "init failed", e); false
        }
    }

    suspend fun recognize(bytes: ByteArray, language: OcrLanguage): List<EngineLine> =
        engineMutex.withLock {
            if (!isInitialized && !init()) return@withLock emptyList()
            engine?.recognize(bytes, language) ?: emptyList()
        }

    @Synchronized
    fun destroy() {
        (engine as? java.io.Closeable)?.let { runCatching { it.close() } }
        engine = null; isInitialized = false
    }
}
