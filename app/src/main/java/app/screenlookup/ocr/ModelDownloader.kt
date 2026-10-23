package app.screenlookup.ocr

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import app.screenlookup.Di
import app.screenlookup.ui.cancelNotification
import app.screenlookup.ui.notify
import chimahon.ocr.OcrModelTrigger
import chimahon.preferences.Preference
import chimahon.preferences.PreferenceStore
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ModelDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val versionResolver: ModelVersionResolver,
    preferenceStore: PreferenceStore,
) : OcrModelTrigger {
    companion object {
        private const val MODELS_VERSION = "3.1"
        private const val RELEASE_URL =
            "https://github.com/u2716/chimahon-local-models/releases/download/v"
        private const val LENS_ZIP = "models.zip"
        private const val PADDLE_ZIP = "paddle-ocr.zip"
        private const val PADDLE_DIR = "paddle_ocr"
        private const val MEIKI_ZIP = "meiki-ocr.zip"
        private const val MEIKI_DIR = "meiki_ocr"
        private const val LENS_DIR = "screenai_models"
        private const val ID_OCR_PROGRESS = -204
        private const val CHANNEL_OCR_MODEL_DOWNLOAD = "ocr_model_download_channel"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val installedLensVersion =
        preferenceStore.getString(Preference.appStateKey("ocr_lens_model_version"), "")
    private val installedPaddleVersion =
        preferenceStore.getString(Preference.appStateKey("ocr_paddle_model_version"), "")
    private val knownVersion =
        preferenceStore.getString(Preference.appStateKey("ocr_models_known_version"), "")

    val latestVersion: String
        get() = knownVersion.get().ifBlank { MODELS_VERSION }

    val lensVersionInstalled: String
        get() = installedLensVersion.get().ifBlank { latestVersion }

    val paddleVersionInstalled: String
        get() = installedPaddleVersion.get().ifBlank { latestVersion }

    val hasLensUpdate: Boolean
        get() = isDownloaded && hasNewerRevision(lensVersionInstalled, latestVersion)

    val hasPaddleUpdate: Boolean
        get() = isPaddleDownloaded && hasNewerRevision(paddleVersionInstalled, latestVersion)

    suspend fun checkForUpdates(): Boolean {
        val latest = versionResolver.latestOcrVersion() ?: return false
        if (isDownloaded && installedLensVersion.get().isBlank()) installedLensVersion.set(MODELS_VERSION)
        if (isPaddleDownloaded && installedPaddleVersion.get().isBlank()) installedPaddleVersion.set(MODELS_VERSION)
        if (knownVersion.get().isBlank()) knownVersion.set(MODELS_VERSION)
        if (hasNewerRevision(latestVersion, latest)) knownVersion.set(latest)
        return true
    }

    override val isDownloaded: Boolean
        get() = supportedAbi()?.let { abi ->
            ModelBundleInstaller.recover(context.filesDir, LENS_DIR)
            requiredLensFiles(abi).all { it.isFile && it.length() > 0L }
        } ?: false

    override val isPaddleDownloaded: Boolean
        get() = supportedAbi()?.let { abi ->
            ModelBundleInstaller.recover(context.filesDir, PADDLE_DIR)
            requiredPaddleFiles(abi).all { it.isFile && it.length() > 0L }
        } ?: false

    override val isMeikiDownloaded: Boolean
        get() = supportedAbi()?.let {
            requiredMeikiFiles().all { file -> file.isFile && file.length() > 0L }
        } ?: false

    fun deleteLensModels(): Boolean = deleteDir(File(context.filesDir, LENS_DIR)) {
        installedLensVersion.delete()
    }
    fun deletePaddleModels(): Boolean = deleteDir(File(context.filesDir, PADDLE_DIR)) {
        installedPaddleVersion.delete()
    }
    fun deleteMeikiModels(): Boolean = deleteDir(File(context.filesDir, MEIKI_DIR)) {}

    private fun deleteDir(dir: File, onDeleted: () -> Unit): Boolean = synchronized(ModelBundleInstaller) {
        val previous = File(dir.parentFile, ".${dir.name}.previous")
        val deleted = (!dir.exists() || dir.deleteRecursively()) &&
            (!previous.exists() || previous.deleteRecursively())
        if (deleted) onDeleted()
        deleted
    }

    private fun supportedAbi(): String? = when {
        "arm64-v8a" in Build.SUPPORTED_ABIS -> "arm64-v8a"
        "armeabi-v7a" in Build.SUPPORTED_ABIS -> "armeabi-v7a"
        else -> null
    }

    private fun requiredLensFiles(abi: String): List<File> {
        val root = File(context.filesDir, LENS_DIR)
        return listOf(
            File(root, "lots_multiscript_v8_runner.binarypb"),
            File(root, "lots_multiscript_v8_engine_patched.binarypb"),
            File(root, "third_party/lens/line_recognition/v678672708/line_recognition_tflite.binarypb"),
            File(root, "third_party/lens/line_recognition/v678672708/line_recognition_mobile_convnext320_omni/hanijpan.tflite"),
            File(root, "third_party/lens/line_recognition/v678672708/line_recognition_mobile_convnext320_omni/arab.tflite"),
            File(root, "third_party/lens/line_recognition/v678672708/line_recognition_mobile_convnext320_omni/kore.tflite"),
            File(root, "third_party/lens/line_recognition/v678672708/line_recognition_mobile_convnext320_omni/gocr_mobile_und.tflite"),
            File(root, "third_party/lens/line_recognition/v678672708/line_recognition_mobile_convnext320_omni/arab_fst_config.pb"),
            File(root, "third_party/lens/line_recognition/v678672708/line_recognition_mobile_convnext320_omni/jpan_fst_config.pb"),
            File(root, "third_party/lens/line_detector/v688492737/gocr_group_rpn_text_detection_config_2024_q4.binarypb"),
            File(root, "third_party/lens/line_detector/v688492737/gocr_group_rpn_text_detection_model_2024_q4.tflite"),
            File(root, "third_party/lens/layout_analysis/v607610364/aksara_page_layout_analysis_rpn_gro_2024_q4.binarypb"),
            File(root, "third_party/lens/script_detector/v541645965/gocr_script_dir_style_identification_tflite_multi_head_multiscript_v3.binarypb"),
            File(root, "third_party/lens/script_detector/v541645965/gocr_script_dir_style_identification_convnext_multi_head.tflite"),
            File(root, "lib/$abi/liblens_ondevice_engine_base.so"),
            File(root, "lib/$abi/liblens_ondevice_engine_play_ml.so"),
        )
    }

    private fun requiredPaddleFiles(abi: String): List<File> {
        val root = File(context.filesDir, PADDLE_DIR)
        return listOf(
            File(root, "manga_det_fp16.param"),
            File(root, "manga_det_fp16.bin"),
            File(root, "manga_rec_fp16.param"),
            File(root, "manga_rec_fp16.bin"),
            File(root, "ppocrv6_dict.txt"),
            File(root, "lib/$abi/libpaddle_ocr.so"),
            File(root, "lib/$abi/libomp.so"),
            File(root, "lib/$abi/libc++_shared.so"),
        )
    }

    private fun requiredMeikiFiles(): List<File> {
        val root = File(context.filesDir, MEIKI_DIR)
        return listOf(
            File(root, "meiki.text.detect.v0.1.960x544.onnx"),
            File(root, "meiki.text.rec.v0.960x32.onnx"),
            File(root, "meiki.text.rec.v0.vertical.32x480.onnx"),
        )
    }

    override fun triggerDownload(force: Boolean) {
        if (isDownloaded && !force) return
        scope.launch {
            downloadWithNotifications(
                progressText = "Downloading on-device OCR models...",
                successText = "On-device OCR models downloaded successfully",
            ) { downloadAndExtract() }
        }
    }

    override fun triggerPaddleDownload(force: Boolean) {
        if (isPaddleDownloaded && !force) return
        scope.launch {
            downloadWithNotifications(
                progressText = "Downloading Paddle OCR models...",
                successText = "Paddle OCR models downloaded successfully",
            ) { downloadPaddleAndExtract() }
        }
    }

    override fun triggerMeikiDownload() {
        if (isMeikiDownloaded) return
        scope.launch {
            downloadWithNotifications(
                progressText = "Downloading Meiki OCR models...",
                successText = "Meiki OCR models downloaded successfully",
            ) { downloadZip(MEIKI_ZIP) }
        }
    }

    private fun versionDownloadedFrom(): String = latestVersion

    private suspend fun downloadWithNotifications(
        progressText: String,
        successText: String,
        download: suspend () -> Result<Unit>,
    ): Result<Unit> {
        context.notify(ID_OCR_PROGRESS, CHANNEL_OCR_MODEL_DOWNLOAD) {
            setSmallIcon(android.R.drawable.stat_sys_download)
            setContentTitle("Downloading OCR models")
            setContentText(progressText)
            setOngoing(true)
            setOnlyAlertOnce(true)
        }
        val result = download()
        context.cancelNotification(ID_OCR_PROGRESS)
        if (result.isSuccess) {
            context.notify(ID_OCR_PROGRESS, CHANNEL_OCR_MODEL_DOWNLOAD) {
                setSmallIcon(android.R.drawable.stat_sys_download_done)
                setContentTitle("OCR models ready")
                setContentText(successText)
                setAutoCancel(true)
                setOngoing(false)
            }
        } else {
            context.notify(ID_OCR_PROGRESS, CHANNEL_OCR_MODEL_DOWNLOAD) {
                setSmallIcon(android.R.drawable.stat_sys_warning)
                setContentTitle("OCR model download failed")
                setContentText(result.exceptionOrNull()?.message ?: "Unknown error")
                setAutoCancel(true)
                setOngoing(false)
            }
        }
        return result
    }

    suspend fun updateInstalledModels(): Result<Unit> {
        if (hasLensUpdate) {
            val result = downloadWithNotifications(
                "Updating on-device OCR models...", "On-device OCR models updated",
            ) { downloadAndExtract() }
            if (result.isFailure) return result
        }
        if (hasPaddleUpdate) {
            val result = downloadWithNotifications(
                "Updating Paddle OCR models...", "Paddle OCR models updated",
            ) { downloadPaddleAndExtract() }
            if (result.isFailure) return result
        }
        return Result.success(Unit)
    }

    suspend fun downloadAndExtract(): Result<Unit> = downloadZip(LENS_ZIP)
    suspend fun downloadPaddleAndExtract(): Result<Unit> = downloadZip(PADDLE_ZIP)
    suspend fun downloadMeikiAndExtract(): Result<Unit> = downloadZip(MEIKI_ZIP)

    private suspend fun downloadZip(zipName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val version = if (zipName == MEIKI_ZIP) MODELS_VERSION else versionDownloadedFrom()
            val bundle = when (zipName) {
                LENS_ZIP -> LENS_DIR
                PADDLE_ZIP -> PADDLE_DIR
                MEIKI_ZIP -> MEIKI_DIR
                else -> error("Unknown OCR model zip: $zipName")
            }
            val url = "$RELEASE_URL$version/$zipName"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ScreenLookup/0.1")
                .header("Accept", "application/octet-stream")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        RuntimeException("Download failed: HTTP ${response.code} for $url")
                    )
                }
                val body = response.body ?: return@withContext Result.failure(
                    RuntimeException("Empty response body")
                )
                body.byteStream().use { input -> extractZip(input, bundle) }
            }
            when (zipName) {
                LENS_ZIP -> {
                    installedLensVersion.set(version)
                    Di.localOcrBridge.invalidateModels()
                }
                PADDLE_ZIP -> {
                    installedPaddleVersion.set(version)
                    Di.paddleOcrBridge.invalidateModels()
                }
                MEIKI_ZIP -> Unit
                else -> error("Unknown OCR model zip: $zipName")
            }
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(RuntimeException("Could not open file"))
            stream.use { extractZip(it) }
            installedLensVersion.set(versionDownloadedFrom())
            Di.localOcrBridge.invalidateModels()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractZip(input: InputStream, bundle: String = LENS_DIR) {
        val abi = supportedAbi() ?: error("Unsupported device architecture")
        val bundleRoot = File(context.filesDir, bundle)
        val required = when (bundle) {
            LENS_DIR -> requiredLensFiles(abi)
            PADDLE_DIR -> requiredPaddleFiles(abi)
            MEIKI_DIR -> requiredMeikiFiles()
            else -> error("Unknown OCR model bundle: $bundle")
        }
        ModelBundleInstaller.install(input, context.filesDir, bundle, required.map { it.relativeTo(bundleRoot).path })
    }

    private fun hasNewerRevision(current: String, candidate: String): Boolean {
        val c = current.split('.').mapNotNull { it.toIntOrNull() }
        val p = candidate.split('.').mapNotNull { it.toIntOrNull() }
        val n = maxOf(c.size, p.size)
        for (i in 0 until n) {
            val cv = c.getOrElse(i) { 0 }
            val pv = p.getOrElse(i) { 0 }
            if (pv > cv) return true
            if (pv < cv) return false
        }
        return false
    }
}
