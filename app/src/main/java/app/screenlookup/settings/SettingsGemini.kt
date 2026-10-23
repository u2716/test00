package app.screenlookup.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.screenlookup.Di
import chimahon.dictionary.ui.collectAsState
import chimahon.gemini.GeminiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeminiScreen(onBack: () -> Unit) {
    val prefs = remember { Di.dictionaryPreferences }

    val apiKey by prefs.geminiApiKey().collectAsState()
    val model by prefs.geminiModel().collectAsState()
    val endpoint by prefs.geminiCustomEndpoint().collectAsState()
    val prompt by prefs.geminiPrompt().collectAsState()
    val sendScreenshot by prefs.geminiSendScreenshot().collectAsState()
    val fontSize by prefs.geminiFontSize().collectAsState()
    val fontFamily by prefs.geminiFontFamily().collectAsState()

    val modelOptions = remember {
        mapOf(
            GeminiConfig.MODEL_FLASH_LITE_LATEST to "Gemini Flash Lite",
            GeminiConfig.MODEL_FLASH_LATEST to "Gemini Flash",
            GeminiConfig.MODEL_GEMMA_4_26B to "Gemma 4 26B",
            GeminiConfig.MODEL_GEMMA_4_31B to "Gemma 4 31B",
            GeminiConfig.MODEL_CUSTOM to "Custom endpoint",
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gemini") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item { SettingsGroupHeader("API") }
            item {
                SettingsTextDialogRow(
                    title = "API key",
                    subtitle = if (apiKey.isBlank()) "Not set" else "•••••••• (tap to edit)",
                    currentValue = apiKey,
                    hidden = true,
                    onSave = { prefs.geminiApiKey().set(it.trim()) },
                )
            }
            item {
                SettingsDropdownRow(
                    title = "Model",
                    value = model,
                    options = modelOptions,
                    onSelect = { prefs.geminiModel().set(it) },
                )
            }
            if (model == GeminiConfig.MODEL_CUSTOM) {
                item {
                    SettingsTextDialogRow(
                        title = "Custom endpoint",
                        subtitle = if (endpoint.isBlank()) "HTTPS URL" else endpoint.take(50),
                        currentValue = endpoint,
                        multiline = true,
                        placeholder = "https://example.com/v1beta/models/…",
                        onSave = { prefs.geminiCustomEndpoint().set(it.trim()) },
                    )
                }
            }

            item { SettingsGroupHeader("Prompt") }
            item {
                SettingsTextDialogRow(
                    title = "Prompt",
                    subtitle = if (prompt.isBlank()) "Default (translation + grammar breakdown)" else "Custom (tap to edit)",
                    currentValue = prompt,
                    multiline = true,
                    onSave = { prefs.geminiPrompt().set(it) },
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Send screenshot",
                    subtitle = "Include the captured screen with the request",
                    checked = sendScreenshot,
                    onCheckedChange = { prefs.geminiSendScreenshot().set(it) },
                )
            }

            item { SettingsGroupHeader("Typography") }
            item {
                SettingsDropdownRow(
                    title = "Font Family",
                    value = fontFamily,
                    options = mapOf(
                        "" to "Default (System)",
                        "serif" to "Serif",
                        "sans-serif" to "Sans-Serif",
                        "monospace" to "Monospace",
                    ),
                    onSelect = { prefs.geminiFontFamily().set(it) },
                )
            }
            item {
                SettingsSliderRow(
                    title = "Font size",
                    subtitle = "$fontSize sp",
                    value = fontSize.toFloat(),
                    range = 8f..48f,
                    steps = 40,
                    onValueChange = { prefs.geminiFontSize().set(it.toInt()) },
                )
            }
        }
    }
}
