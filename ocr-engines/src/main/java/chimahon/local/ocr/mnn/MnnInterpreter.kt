package chimahon.local.ocr.mnn

import android.util.Log
import java.io.Closeable

/**
 * Thin Kotlin handle over MNN's Session-API inference.
 *
 * Loads a converted `.mnn` model and runs typed named tensors through it.
 * Multi-input / multi-output is the normal path for Meiki's D-FINE models,
 * which take `images` + int32 `orig_target_sizes` and return `char_codes`,
 * `boxes`, `scores`.
 *
 * **Not thread-safe.** One MNN Session cannot run concurrently; serialize
 * externally (MeikiOcrEngine already gates this with a Semaphore).
 *
 * arm64-v8a only — matches the rest of :mnn / the OCR engines module.
 */
class MnnInterpreter private constructor() : Closeable {

    private var handle: Long = 0L

    /** Flat output data with shape. */
    class Result(val data: FloatArray, val shape: IntArray)

    /** Typed tensor payload — MNN session tensors are float or int32. */
    sealed class TensorData {
        class Floats(val data: FloatArray) : TensorData()
        class Ints(val data: IntArray) : TensorData()
    }

    /** Named tensor with NCHW shape + typed data. */
    class NamedTensor(val name: String, val shape: IntArray, val data: TensorData)

    /**
     * Run a forward pass. [inputs] matched to session inputs **by name**;
     * returns every session output as a NamedTensor (look up by type/shape
     * or name — order is not guaranteed).
     */
    fun run(inputs: List<NamedTensor>): List<NamedTensor> {
        check(handle != 0L) { "MnnInterpreter is closed" }
        val n = inputs.size
        val names = Array(n) { inputs[it].name }
        val shapes = Array(n) { inputs[it].shape }
        val dtypes = IntArray(n) { if (inputs[it].data is TensorData.Ints) 1 else 0 }
        val data = Array<Any>(n) {
            when (val d = inputs[it].data) {
                is TensorData.Floats -> d.data
                is TensorData.Ints -> d.data
            }
        }
        val out = nativeRunMulti(handle, names, shapes, dtypes, data)
            ?: error("MNN multi-IO inference failed (see logcat tag 'mnn-ocr')")
        @Suppress("UNCHECKED_CAST")
        val arr = out as Array<Any>
        @Suppress("UNCHECKED_CAST")
        val outNames = arr[0] as Array<String>
        val outDtypes = arr[1] as IntArray
        val outShapes = arr[2] as Array<*>
        val outData = arr[3] as Array<*>
        return List(outNames.size) { i ->
            val td = if (outDtypes[i] == 1) TensorData.Ints(outData[i] as IntArray)
                     else TensorData.Floats(outData[i] as FloatArray)
            NamedTensor(outNames[i], outShapes[i] as IntArray, td)
        }
    }

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(path: String, numThread: Int, precision: Int): Long
    private external fun nativeRunMulti(
        handle: Long,
        names: Array<String>,
        shapes: Array<IntArray>,
        dtypes: IntArray,
        data: Array<Any>,
    ): Any?
    private external fun nativeDestroy(handle: Long)

    companion object {
        private const val TAG = "MnnInterpreter"

        @Volatile
        private var libLoaded = false

        fun ensureLibLoaded() {
            if (!libLoaded) synchronized(this) {
                if (!libLoaded) {
                    System.loadLibrary("mnn_ocr_jni")
                    libLoaded = true
                    Log.i(TAG, "Loaded native library 'mnn_ocr_jni'")
                }
            }
        }

        /**
         * Load an MNN model from [path].
         *
         * @param numThread CPU threads (default 4).
         * @param precision 0=Normal, 1=High(force fp32), 2=Low(fp16 on ARM).
         */
        @JvmStatic
        @JvmOverloads
        fun fromFile(path: String, numThread: Int = 4, precision: Int = 0): MnnInterpreter {
            ensureLibLoaded()
            val inst = MnnInterpreter()
            inst.handle = inst.nativeCreate(path, numThread, precision)
            check(inst.handle != 0L) {
                "MNN failed to load model: $path (see logcat tag 'mnn-ocr')"
            }
            return inst
        }
    }
}
