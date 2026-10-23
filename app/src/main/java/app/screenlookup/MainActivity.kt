package app.screenlookup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.screenlookup.screenlookup.ScreenLookupPermissionActivity
import app.screenlookup.screenlookup.ScreenLookupService
import app.screenlookup.screenlookup.ScreenLookupServiceState
import app.screenlookup.settings.SettingsAnkiScreen
import app.screenlookup.settings.SettingsDictionaryDisplayScreen
import app.screenlookup.settings.SettingsDictionaryManagementScreen
import app.screenlookup.settings.SettingsGeminiScreen
import app.screenlookup.settings.SettingsGeneralScreen
import app.screenlookup.settings.SettingsHubScreen
import app.screenlookup.settings.SettingsOcrScreen
import app.screenlookup.settings.SettingsScreenLookupScreen
import app.screenlookup.ui.AppTheme
import chimahon.dictionary.ui.collectAsState
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data object SettingsHub : Screen
    data object SettingsGeneral : Screen
    data object SettingsOcr : Screen
    data object SettingsScreenLookup : Screen
    data object SettingsDictionaryManagement : Screen
    data object SettingsDictionaryDisplay : Screen
    data object SettingsAnki : Screen
    data object SettingsGemini : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = backStack.last()

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
    }

    fun navigate(to: Screen) { backStack.add(to) }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    when (current) {
        Screen.Home -> HomeScreen(onOpenSettings = { navigate(Screen.SettingsHub) })
        Screen.SettingsHub -> SettingsHubScreen(onBack = ::back, onOpen = ::navigate)
        Screen.SettingsGeneral -> SettingsGeneralScreen(onBack = ::back)
        Screen.SettingsOcr -> SettingsOcrScreen(onBack = ::back)
        Screen.SettingsScreenLookup -> SettingsScreenLookupScreen(onBack = ::back)
        Screen.SettingsDictionaryManagement -> SettingsDictionaryManagementScreen(onBack = ::back)
        Screen.SettingsDictionaryDisplay -> SettingsDictionaryDisplayScreen(onBack = ::back)
        Screen.SettingsAnki -> SettingsAnkiScreen(onBack = ::back)
        Screen.SettingsGemini -> SettingsGeminiScreen(onBack = ::back)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var serviceRunning by remember { mutableStateOf(ScreenLookupServiceState.isRunning.value) }

    LaunchedEffect(Unit) {
        ScreenLookupServiceState.isRunning.collect { serviceRunning = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screen Lookup") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Screen Lookup", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (serviceRunning) {
                            "Running. Tap the floating button to capture the screen."
                        } else {
                            "Stopped. Enable to start the floating capture button."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            if (serviceRunning) {
                                ScreenLookupService.stop(context)
                            } else {
                                context.startActivity(
                                    Intent(context, ScreenLookupPermissionActivity::class.java),
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(if (serviceRunning) "Disable" else "Enable")
                    }
                }
            }
            StatusCard()
            StatisticsCard()
        }
    }
}

@Composable
private fun StatusCard() {
    val context = LocalContext.current
    val dictionaryCount = remember { countDictionaries(context) }
    val ocrEngine = remember { Di.dictionaryPreferences.ocrEngine().get() }
    val ankiEnabled = remember {
        Di.dictionaryPreferences.profileStore.getActiveProfile().ankiEnabled
    }
    val geminiConfigured = remember {
        Di.dictionaryPreferences.geminiApiKey().get().isNotBlank()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Status", style = MaterialTheme.typography.titleLarge)
            StatusLine("Dictionaries", if (dictionaryCount == 0) "None installed" else "$dictionaryCount installed")
            StatusLine("OCR engine", ocrEngineLabel(ocrEngine))
            StatusLine("Anki", if (ankiEnabled) "Enabled" else "Not configured")
            StatusLine("Gemini", if (geminiConfigured) "Configured" else "No API key")
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun countDictionaries(context: android.content.Context): Int {
    val dir = File(context.getExternalFilesDir(null), "dictionaries")
    if (!dir.isDirectory) return 0
    return listOf("term", "frequency", "pitch", "kanji")
        .flatMap { type ->
            File(dir, type).listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
        }
        .distinct()
        .size
}

@Composable
private fun StatisticsCard() {
    val prefs = remember { Di.dictionaryPreferences }
    
    // Observe total to trigger live updates
    val totalLookups by prefs.statsTotalLookups().collectAsState()
    
    // Recalculate stats whenever the total lookups count changes
    val stats = remember(totalLookups) {
        chimahon.dictionary.ui.StatisticsHelper.getStats(prefs)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Statistics", style = MaterialTheme.typography.titleLarge)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                StatItem("24h", stats.last24h, Modifier.weight(1f))
                StatItem("7d", stats.last7d, Modifier.weight(1f))
                StatItem("30d", stats.last30d, Modifier.weight(1f))
                StatItem("1y", stats.last365d, Modifier.weight(1f))
                StatItem("Total", stats.total, Modifier.weight(1f), isTotal = true)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int, modifier: Modifier = Modifier, isTotal: Boolean = false) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatStatNumber(value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isTotal) {
            Text(
                text = "Lookups",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatStatNumber(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", value / 1_000f)
        else -> value.toString()
    }
}

private fun ocrEngineLabel(engine: String): String = when (engine) {
    "local" -> "ScreenAI"
    "paddle" -> "Paddle OCR"
    "meiki" -> "Meiki OCR"
    else -> "GLens"
}
