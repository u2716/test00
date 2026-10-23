package app.screenlookup.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.screenlookup.Di
import chimahon.dictionary.ui.collectAsState
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeneralScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = Di.prefs
    val themePref = prefs.getString("app_theme", "system")
    val themeMode by themePref.collectAsState()

    var importError by remember { mutableStateOf<String?>(null) }
    var importSuccess by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val json = JSONObject()
                for ((k, v) in Di.sharedPrefs.all) {
                    if (k.contains("__APP_STATE_")) continue
                    when (v) {
                        is String -> json.put(k, v)
                        is Int -> json.put(k, v)
                        is Long -> json.put(k, v)
                        is Float -> json.put(k, v)
                        is Boolean -> json.put(k, v)
                        is Set<*> -> json.put(k, JSONArray(v.toList()))
                    }
                }
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toString(2).toByteArray())
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                val editor = Di.sharedPrefs.edit()
                
                for (key in Di.sharedPrefs.all.keys) {
                    if (!key.contains("__APP_STATE_")) {
                        editor.remove(key)
                    }
                }
                
                for (key in json.keys()) {
                    when (val v = json.get(key)) {
                        is String -> editor.putString(key, v)
                        is Int -> editor.putInt(key, v)
                        is Long -> editor.putLong(key, v)
                        is Float -> editor.putFloat(key, v)
                        is Boolean -> editor.putBoolean(key, v)
                        is JSONArray -> {
                            val set = mutableSetOf<String>()
                            for (i in 0 until v.length()) set.add(v.getString(i))
                            editor.putStringSet(key, set)
                        }
                    }
                }
                editor.apply()
                importSuccess = true
            } catch (e: Exception) {
                importError = e.message ?: "Failed to parse settings file"
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings?") },
            text = { Text("This will clear all your preferences, dictionary configurations, API keys, and imported OCR models. The app will restart. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        Di.sharedPrefs.edit().clear().apply()
                        showResetDialog = false
                        restartApp()
                    }
                ) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (importSuccess) {
        AlertDialog(
            onDismissRequest = { importSuccess = false; restartApp() },
            title = { Text("Import Successful") },
            text = { Text("Settings restored. The app must restart to apply all changes.") },
            confirmButton = { 
                TextButton(onClick = { importSuccess = false; restartApp() }) { Text("Restart Now") } 
            }
        )
    }

    if (importError != null) {
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Import Failed") },
            text = { Text(importError!!) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("General") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SettingsGroupHeader("Appearance") }
            item {
                SettingsDropdownRow(
                    title = "App Theme",
                    value = themeMode,
                    options = mapOf(
                        "system" to "System Default",
                        "light" to "Light",
                        "dark" to "Dark"
                    ),
                    onSelect = { themePref.set(it) }
                )
            }

            item { SettingsGroupHeader("Backup") }
            item {
                SettingsClickRow(
                    title = "Export Settings",
                    subtitle = "Save all preferences to a JSON file",
                    onClick = { exportLauncher.launch("screen_lookup_settings.json") }
                )
            }
            item {
                SettingsClickRow(
                    title = "Import Settings",
                    subtitle = "Restore preferences from a JSON file",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                )
            }
            item { SettingsGroupHeader("Reset") }
            item {
                SettingsClickRow(
                    title = "Reset Settings",
                    subtitle = "Clear all preferences and restart the app",
                    onClick = { showResetDialog = true }
                )
            }
        }
    }
}
