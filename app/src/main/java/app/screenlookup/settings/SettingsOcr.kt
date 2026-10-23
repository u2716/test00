package app.screenlookup.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.screenlookup.BuildConfig
import app.screenlookup.Di
import chimahon.dictionary.ui.collectAsState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class OcrModelStatus(val installed: Boolean, val bytes: Long, val version: String?)

@Composable
fun SettingsOcrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Di.dictionaryPreferences }
    val ocr = remember { Di.modelDownloader }

    var statuses by remember { mutableStateOf<List<OcrModelStatus>>(emptyList()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var refreshing by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing) {
        while (true) {
            statuses = withContext(Dispatchers.IO) {
                val installed = listOf(ocr.isDownloaded, ocr.isPaddleDownloaded, ocr.isMeikiDownloaded)
                val versions = listOf(ocr.lensVersionInstalled, ocr.paddleVersionInstalled, null)
                listOf("screenai_models", "paddle_ocr", "meiki_ocr").mapIndexed { index, dir ->
                    val bytes = File(context.filesDir, dir).walkTopDown()
                        .filter { it.isFile }.sumOf { it.length() }
                    OcrModelStatus(installed[index], bytes, versions[index])
                }
            }
            delay(5_000)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            ocr.importFromUri(uri)
            refreshing++
        }
    }

    if (selected != null) {
        val index = selected!!
        val status = statuses.getOrNull(index)
        val titles = listOf("ScreenAI", "Paddle OCR", "Meiki OCR")
        AlertDialog(
            onDismissRequest = { if (!deleting) selected = null },
            title = { Text(titles[index]) },
            text = {
                Text(
                    if (status?.installed == true) {
                        "Delete downloaded files to free ${Formatter.formatFileSize(context, status.bytes)}?"
                    } else {
                        "Download this model to use this engine."
                    },
                )
            },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    if (status?.installed == true) {
                        deleting = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                when (index) {
                                    0 -> ocr.deleteLensModels()
                                    1 -> ocr.deletePaddleModels()
                                    2 -> ocr.deleteMeikiModels()
                                }
                            }
                            deleting = false
                            selected = null
                            refreshing++
                        }
                    } else {
                        when (index) {
                            0 -> ocr.triggerDownload()
                            1 -> ocr.triggerPaddleDownload()
                            2 -> ocr.triggerMeikiDownload()
                        }
                        selected = null
                    }
                }) { Text(if (status?.installed == true) "Delete" else "Download") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } },
        )
    }

    val engineOptions = buildMap {
        put("cloud", "GLens")
        if (BuildConfig.HAS_LOCAL_OCR) {
            put("local", "ScreenAI")
            put("paddle", "Paddle OCR")
            put("meiki", "Meiki OCR")
        }
    }

    val groups = buildList {
        add(
            SettingsRows.Section(
                title = "Engine",
                items = listOf(
                    SettingsRows.Item.Dropdown(
                        preference = prefs.ocrEngine(),
                        entries = engineOptions,
                        title = "OCR Engine",
                        onValueChanged = { chosen ->
                            // Destroy all engines to free native RAM when switching
                            Di.localOcrBridge.destroy()
                            Di.paddleOcrBridge.destroy()
                            Di.meikiBridge.destroy()

                            when (chosen) {
                                "local" -> if (!ocr.isDownloaded) ocr.triggerDownload()
                                "paddle" -> if (!ocr.isPaddleDownloaded) ocr.triggerPaddleDownload()
                                "meiki" -> if (!ocr.isMeikiDownloaded) ocr.triggerMeikiDownload()
                            }
                            true
                        },
                    ),
                ),
            ),
        )

        if (BuildConfig.HAS_LOCAL_OCR) {
            val titles = listOf("ScreenAI", "Paddle OCR", "Meiki OCR")
            val items = mutableListOf<SettingsRows.Item>()
            for (i in 0..2) {
                val status = statuses.getOrNull(i)
                val stateText = when {
                    status == null -> "Checking…"
                    status.installed -> "Installed" + (status.version?.let { " v$it" } ?: "")
                    status.bytes > 0 -> "Incomplete"
                    else -> "Not downloaded"
                }
                val sizeText = status?.let { " · ${Formatter.formatFileSize(context, it.bytes)}" } ?: ""
                items += SettingsRows.Item.Button(
                    title = titles[i],
                    subtitle = "$stateText$sizeText",
                    onClick = { selected = i },
                )
            }
            items += SettingsRows.Item.Button(
                title = "Import OCR models",
                subtitle = "Select a models.zip",
                onClick = { importLauncher.launch(arrayOf("application/zip")) },
            )
            add(SettingsRows.Section(title = "Models", items = items))
        }
    }

    SettingsScreen(title = "OCR", groups = groups, onBack = onBack)
}
