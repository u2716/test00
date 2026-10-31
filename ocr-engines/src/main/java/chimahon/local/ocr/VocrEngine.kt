package chimahon.local.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import chimahon.ocr.EngineChar
import chimahon.ocr.EngineLine
import chimahon.ocr.NormalizedBBox
import chimahon.ocr.OcrBitmapDecoder
import chimahon.ocr.OcrLanguage
import chimahon.ocr.WritingDirection
import java.io.Closeable
import java.io.File
import java.util.concurrent.Semaphore

/**
 * VOCR engine. Japanese-only. Line finder → greedy recognizer → optional LM → charboxes.
 *
 * [tier] is "small", "medium" (default), or "large" — matches VOCR6.2_{S,N,L}. The zip
 * contains all three; the tier only selects which one loads into RAM. Fallback: if the
 * chosen tier's file is missing but another is present, the available one is used.
 */
class VocrEngine(
    context: Context,
    private val tier: String = "medium",
) : chimahon.ocr.OcrEngine, Closeable {

    override val name: String = "Vita VN OCR"

    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "vocr_models")
    private val permits = Semaphore(INFER_PERMITS)

    @Volatile private var interp: VocrInterpreter? = null
    @Volatile private var initTried = false

    private fun recognizerFile(): File? {
        val preferred = File(modelDir, when (tier) {
            "small" -> "VOCR6.2_S_w8.vocr"
            "large" -> "VOCR6.2_L_w8_2l.vocr"
            else    -> "VOCR6.2_N_w8g2.vocr"
        })
        if (preferred.isFile && preferred.length() > 0L) return preferred
        return listOf("VOCR6.2_N_w8g2.vocr", "VOCR6.2_S_w8.vocr", "VOCR6.2_L_w8_2l.vocr")
            .map { File(modelDir, it) }.firstOrNull { it.isFile && it.length() > 0L }
    }

    private fun spotterFile(): File? = File(modelDir, "textspot8_t64n8c16_w8.vtsp").takeIf { it.isFile && it.length() > 0L }

    private fun lmFile(): File? = listOf(
        "vocr_lm6_o3_1000k.vlm", "vocr_lm_o3_500k.vlm", "vocr_lm_o3_2000k.vlm",
    ).map { File(modelDir, it) }.firstOrNull { it.isFile && it.length() > 0L }

    private fun ensureInit(): VocrInterpreter? {
        interp?.let { return it }
        synchronized(this) {
            interp?.let { return it }
            if (initTried) return interp
            initTried = true
            val recognizer = recognizerFile() ?: run {
                Log.e(TAG, "no .vocr in ${modelDir.absolutePath}"); return null
            }
            val lm = lmFile()
            val spotter = spotterFile()
            val built = try {
                VocrInterpreter.fromFiles(recognizer.absolutePath, lm?.absolutePath, spotter?.absolutePath, VocrInterpreter.MODE_W8I)
            } catch (e: Throwable) {
                Log.e(TAG, "fromFiles failed", e); return null
            }
            Log.i(TAG, "init: tier=$tier recognizer=${recognizer.name}${if (lm != null) " + ${lm.name}" else ""}")
            interp = built
            return built
        }
    }

    fun init(): Boolean = ensureInit() != null

    fun isInitialized(): Boolean = interp != null

    override suspend fun recognize(bytes: ByteArray, language: OcrLanguage): List<EngineLine> {
        permits.acquire()
        try {
            val engine = ensureInit() ?: return emptyList()
            val bmp: Bitmap = try { OcrBitmapDecoder.decode(bytes) } catch (e: Throwable) {
                Log.w(TAG, "decode failed", e); return emptyList()
            }
            return try {
                val grey = bitmapToGrey(bmp)
                val w = bmp.width; val h = bmp.height
                
                // The C++ spotter has a hard limit of 2048px per side to prevent int32 overflow.
                // We must downscale the image if it's too large, find the text, then scale the
                // bounding boxes back up to the original resolution.
                val maxSide = 2048
                val scale = minOf(1f, maxSide.toFloat() / w, maxSide.toFloat() / h)
                val sw = (w * scale).toInt().coerceAtLeast(1)
                val sh = (h * scale).toInt().coerceAtLeast(1)
                
                val spotterGrey = if (scale >= 1f) grey else {
                    val scaledBmp = Bitmap.createScaledBitmap(bmp, sw, sh, true)
                    try { bitmapToGrey(scaledBmp) } finally { scaledBmp.recycle() }
                }
                
                val boxes = engine.findTextBlocks(spotterGrey, sw, sh) ?: return emptyList()
                val lines = ArrayList<EngineLine>(boxes.size)
                for (b in boxes) {
                    // Scale coordinates back to the original image size
                    val x = (b[0] / scale).toInt().coerceIn(0, w - 1)
                    val y = (b[1] / scale).toInt().coerceIn(0, h - 1)
                    val bw = (b[2] / scale).toInt().coerceAtMost(w - x)
                    val bh = (b[3] / scale).toInt().coerceAtMost(h - y)
                    val dark = b[4] != 0
                    
                    if (bw <= 0 || bh <= 0) continue
                    
                    val crop = cropGrey(grey, w, x, y, bw, bh)
                    val r = engine.recognizeLine(crop, bw, bh, invert = dark, useLm = true) ?: continue
                    if (r.error != null) { Log.w(TAG, "line ${r.error}"); continue }
                    if (r.text.isBlank()) continue

                    // Exact per-char boxes when JNI could align them to the returned text.
                    // Otherwise null → downstream falls back to uniform interpolation (same
                    // behaviour Meiki uses when it can't align).
                    val chars: List<EngineChar>? = r.charBoxes?.let { cb ->
                        val count = r.text.codePointCount(0, r.text.length)
                        if (cb.size != count) null
                        else buildList {
                            var cpIndex = 0
                            r.text.codePoints().forEachOrdered { cp ->
                                val (x0, x1) = cb[cpIndex++]
                                add(EngineChar(
                                    char = String(Character.toChars(cp)).first(),
                                    bbox = NormalizedBBox(
                                        left   = (x + x0).toDouble() / w,
                                        top    = y.toDouble() / h,
                                        right  = (x + x1).toDouble() / w,
                                        bottom = (y + bh).toDouble() / h,
                                    ),
                                    conf = r.confMean,
                                ))
                            }
                        }
                    }

                    lines += EngineLine(
                        text = r.text,
                        bbox = NormalizedBBox(
                            left   = x.toDouble() / w,
                            top    = y.toDouble() / h,
                            right  = (x + bw).toDouble() / w,
                            bottom = (y + bh).toDouble() / h,
                        ),
                        writingDirection = WritingDirection.LTR,
                        language = OcrLanguage.JAPANESE,
                        chars = chars,
                    )
                }
                lines
            } finally { bmp.recycle() }
        } finally { permits.release() }
    }

    override fun close() {
        // Drain the permit pool first: a recognize() may be running on another
        // dispatcher thread, and freeing the native interpreter out from under
        // it is a use-after-free in JNI. Block until every slot is held, tear
        // the engine down, then release.
        repeat(INFER_PERMITS) { permits.acquire() }
        try {
            synchronized(this) {
                interp?.close(); interp = null; initTried = false
            }
        } finally {
            repeat(INFER_PERMITS) { permits.release() }
        }
    }

    private fun bitmapToGrey(bmp: Bitmap): ByteArray {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            out[i] = (((77 * ((c shr 16) and 0xFF)) + (150 * ((c shr 8) and 0xFF)) + (29 * (c and 0xFF))) shr 8).toByte()
        }
        return out
    }

    private fun cropGrey(src: ByteArray, srcW: Int, x: Int, y: Int, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h)
        for (row in 0 until h) System.arraycopy(src, (y + row) * srcW + x, out, row * w, w)
        return out
    }

    companion object {
        private const val TAG = "VocrEngine"
        private val INFER_PERMITS = if (Runtime.getRuntime().availableProcessors() >= 8) 2 else 1
    }
}
