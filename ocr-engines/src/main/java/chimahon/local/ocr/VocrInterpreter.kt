package chimahon.local.ocr

import java.io.Closeable

data class VocrLineResult(
    val greedy: String,
    val text: String,
    val lmUnchanged: Boolean,
    val confMin: Float,
    val confMean: Float,
    val frames: Int,
    val error: String?,
    /** Exact per-character (x0, x1) in crop-pixel space; size == text.length iff the JNI
     *  could align LM chars onto greedy charboxes. Null → the caller falls back to uniform. */
    val charBoxes: List<Pair<Float, Float>>?,
)

class VocrInterpreter private constructor() : Closeable {
    private var handle: Long = 0L

    fun recognizeLine(grey: ByteArray, w: Int, h: Int, invert: Boolean, useLm: Boolean): VocrLineResult? {
        if (handle == 0L) return null
        val raw = nativeRecognizeLine(handle, grey, w, h, invert, useLm) ?: return null
        val greedy = raw[0]; val text = raw[1]
        val nChars = (raw.size - 7) / 2
        // Text length in *code points* — for Japanese VN text this equals length, but be strict.
        val textChars = text.codePointCount(0, text.length)
        val boxes: List<Pair<Float, Float>>? =
            if (nChars > 0 && nChars == textChars)
                List(nChars) { i -> raw[7 + i*2].toFloat() to raw[8 + i*2].toFloat() }
            else null
        return VocrLineResult(
            greedy = greedy, text = text,
            lmUnchanged = raw[5] == "1",
            confMin = raw[2].toFloatOrNull() ?: 0f,
            confMean = raw[3].toFloatOrNull() ?: 0f,
            frames = raw[4].toIntOrNull() ?: 0,
            error = raw[6].ifEmpty { null },
            charBoxes = boxes,
        )
    }

    fun findLines(grey: ByteArray, w: Int, h: Int): List<IntArray>? {
        if (handle == 0L) return null
        val flat = nativeFindLines(grey, w, h) ?: return null
        val out = ArrayList<IntArray>(flat.size / 5)
        var i = 0
        while (i + 4 < flat.size) {
            out.add(intArrayOf(flat[i], flat[i+1], flat[i+2], flat[i+3], flat[i+4])); i += 5
        }
        return out
    }

    override fun close() { if (handle != 0L) { nativeDestroy(handle); handle = 0L } }

    private external fun nativeRecognizeLine(
        handle: Long, grey: ByteArray, w: Int, h: Int, invert: Boolean, useLm: Boolean): Array<String>?
    private external fun nativeFindLines(grey: ByteArray, w: Int, h: Int): IntArray?
    private external fun nativeDestroy(handle: Long)

    companion object {
        const val MODE_AUTO = 0
        const val MODE_F32  = 1
        const val MODE_W8F  = 2
        const val MODE_W8I  = 3

        @Volatile private var libLoaded = false

        private fun ensureLibLoaded() {
            if (!libLoaded) synchronized(this) {
                if (!libLoaded) { System.loadLibrary("vocr_jni"); libLoaded = true }
            }
        }

        fun fromFiles(blobPath: String, lmPath: String? = null, mode: Int = MODE_W8I): VocrInterpreter {
            ensureLibLoaded()
            val inst = VocrInterpreter()
            inst.handle = nativeCreate(blobPath, lmPath, mode)
            check(inst.handle != 0L) { "VOCR failed to load $blobPath (logcat tag vocr-jni)" }
            return inst
        }

        @JvmStatic
        private external fun nativeCreate(blobPath: String, lmPath: String?, mode: Int): Long
    }
}
