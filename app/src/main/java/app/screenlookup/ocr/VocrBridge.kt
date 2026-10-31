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
            val instance = ctor.newInstance(context, tier) as chimahon.ocr.OcrEngine
            engine = instance
            // Delegate to the engine's own init so we only mark the bridge as
            // initialized when the native interpreter actually loaded. Lens /
            // Paddle / Meiki verify init the same way; without this a failed
            // native load leaves isInitialized=true and every subsequent
            // recognize() silently returns emptyList() until app restart.
            val initialized = clazz.getMethod("init").invoke(instance) as? Boolean ?: false
            isInitialized = initialized
            if (initialized) {
                android.util.Log.i("VocrBridge", "init: tier=$tier")
            } else {
                android.util.Log.w("VocrBridge", "init: engine failed to load tier=$tier")
                engine = null
            }
            initialized
        } catch (e: Throwable) {
            android.util.Log.e("VocrBridge", "init failed", e); false
        }
    }

    suspend fun recognize(bytes: ByteArray, language: OcrLanguage): List<EngineLine> =
        engineMutex.withLock {
            if (!isInitialized && !init()) return@withLock emptyList()
            engine?.recognize(bytes, language) ?: emptyList()
        }

    /** Waits for any in-flight recognize() to finish before tearing the engine down. */
    suspend fun invalidateModels() = engineMutex.withLock { destroy() }

    @Synchronized
    fun destroy() {
        (engine as? java.io.Closeable)?.let { runCatching { it.close() } }
        engine = null; isInitialized = false
    }
}
