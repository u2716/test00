package app.screenlookup.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.screenlookup.Di
import chimahon.anki.AnkiCardCreator
import chimahon.anki.AnkiDroidBridge
import chimahon.anki.AnkiProfile
import chimahon.anki.LapisPreset
import chimahon.anki.Marker
import chimahon.ocr.CropPresets
import kotlinx.coroutines.launch
import org.json.JSONObject

private val markerSections = listOf(
    "Core" to listOf(Marker.EXPRESSION, Marker.READING, Marker.FURIGANA, Marker.FURIGANA_PLAIN),
    "Glossary" to listOf(Marker.GLOSSARY, Marker.GLOSSARY_BRIEF, Marker.GLOSSARY_PLAIN, Marker.GLOSSARY_FIRST, Marker.SELECTED_GLOSSARY, Marker.SINGLE_GLOSSARY),
    "Sentence" to listOf(Marker.SENTENCE, Marker.SENTENCE_BOLD, Marker.SENTENCE_FURIGANA, Marker.CLOZE_PREFIX, Marker.CLOZE_BODY, Marker.CLOZE_SUFFIX),
    "Pitch" to listOf(Marker.PITCH_ACCENTS, Marker.PITCH_ACCENT_POSITIONS, Marker.PITCH_ACCENT_CATEGORIES, Marker.PITCH_ACCENT_GRAPHS, Marker.MORAE),
    "Frequency" to listOf(Marker.FREQUENCIES, Marker.FREQUENCY_LOWEST, Marker.FREQUENCY_HARMONIC_RANK, Marker.FREQUENCY_AVERAGE_RANK),
    "Media" to listOf(Marker.WORD_AUDIO, Marker.AUDIO, Marker.SCREENSHOT, Marker.SENTENCE_AUDIO),
    "Context" to listOf(Marker.TAGS, Marker.PART_OF_SPEECH, Marker.CONJUGATION, Marker.DICTIONARY, Marker.MEDIA_NAME, Marker.CHAPTER)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAnkiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val prefs = remember { Di.dictionaryPreferences }
    val bridge = remember { AnkiDroidBridge(context) }
    val profileStore = prefs.profileStore
    val lifecycleOwner = LocalLifecycleOwner.current

    var decks by remember { mutableStateOf<List<String>>(emptyList()) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelFields by remember { mutableStateOf<List<String>>(emptyList()) }
    var installed by remember { mutableStateOf<Boolean?>(null) }
    var hasPermission by remember { mutableStateOf(bridge.hasPermission()) }
    var pendingPermissionCheck by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var profile by remember { mutableStateOf(profileStore.getActiveProfile()) }

    fun updateProfile(block: AnkiProfile.() -> AnkiProfile) {
        val updated = profile.block()
        profile = updated
        profileStore.updateProfile(updated)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingPermissionCheck) {
                pendingPermissionCheck = false
                val nowHasPerm = bridge.hasPermission()
                hasPermission = nowHasPerm
                if (nowHasPerm) {
                    updateProfile { copy(ankiEnabled = true) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(profile.ankiEnabled, hasPermission) {
        if (!profile.ankiEnabled) {
            installed = null
            return@LaunchedEffect
        }
        installed = bridge.isAnkiDroidInstalled()
        if (installed == true && hasPermission) {
            isLoading = true
            try {
                decks = bridge.deckNames()
                models = bridge.modelNames()
                
                var updated = profileStore.getActiveProfile()
                
                if (updated.ankiDeck.isBlank()) {
                    runCatching { bridge.ensureDefaultDeckName() }
                    updated = updated.copy(ankiDeck = LapisPreset.DEFAULT_DECK_NAME)
                }
                if (updated.ankiModel.isBlank()) {
                    runCatching { bridge.ensureLapisModelName() }
                    updated = updated.copy(ankiModel = LapisPreset.MODEL_NAME)
                }
                if (LapisPreset.isBlankFieldMap(updated.ankiFieldMap)) {
                    updated = updated.copy(ankiFieldMap = LapisPreset.defaultFieldMapJson)
                }
                
                profileStore.updateProfile(updated)
                profile = updated
                
                decks = bridge.deckNames()
                models = bridge.modelNames()
            } catch (e: Exception) {
                // Ignored
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(profile.ankiModel, installed, hasPermission) {
        if (profile.ankiModel.isNotBlank() && installed == true && hasPermission) {
            modelFields = bridge.modelFieldNames(profile.ankiModel)
            if (profile.ankiFieldMap == "{}") {
                val detected = if (LapisPreset.isLapisLikeModel(profile.ankiModel, modelFields)) {
                    LapisPreset.defaultFieldMapFor(modelFields)
                } else {
                    modelFields.mapIndexedNotNull { idx, name ->
                        val m = Marker.autoDetect(name, idx)
                        if (m != null) name to "{$m}" else null
                    }.toMap()
                }
                if (detected.isNotEmpty()) {
                    updateProfile { copy(ankiFieldMap = JSONObject(detected).toString()) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anki") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SettingsGroupHeader("Integration") }
            item {
                SettingsSwitchRow(
                    title = "Enable AnkiDroid integration",
                    subtitle = when {
                        installed == false -> "AnkiDroid not installed"
                        profile.ankiEnabled -> "Cards will be added to your collection"
                        else -> "Disabled"
                    },
                    checked = profile.ankiEnabled,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            updateProfile { copy(ankiEnabled = false) }
                        } else {
                            scope.launch {
                                val isInstalled = bridge.isAnkiDroidInstalled()
                                if (!isInstalled) {
                                    installed = false
                                    return@launch
                                }
                                if (!bridge.hasPermission() && activity != null) {
                                    pendingPermissionCheck = true
                                    bridge.requestPermission(activity)
                                    return@launch
                                }
                                updateProfile { copy(ankiEnabled = true) }
                            }
                        }
                    }
                )
            }

            if (profile.ankiEnabled) {
                if (isLoading) {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp)) }
                } else if (installed == false) {
                    item { Text("AnkiDroid not installed", modifier = Modifier.padding(16.dp)) }
                } else {
                    item { SettingsGroupHeader("Deck & Model") }
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SettingsClickRow(
                                title = "Deck",
                                subtitle = profile.ankiDeck.ifBlank { "Not set" },
                                onClick = { expanded = true }
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                decks.forEach { deck ->
                                    DropdownMenuItem(text = { Text(deck) }, onClick = {
                                        updateProfile { copy(ankiDeck = deck) }
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }
                    
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SettingsClickRow(
                                title = "Model",
                                subtitle = profile.ankiModel.ifBlank { "Not set" },
                                onClick = { expanded = true }
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                models.forEach { model ->
                                    DropdownMenuItem(text = { Text(model) }, onClick = {
                                        updateProfile { copy(ankiModel = model, ankiFieldMap = "{}") }
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }

                    item {
                        SettingsTextDialogRow(
                            title = "Default tags",
                            subtitle = profile.ankiTags.ifBlank { "None" },
                            currentValue = profile.ankiTags,
                            onSave = { updateProfile { copy(ankiTags = it) } }
                        )
                    }

                    item { SettingsGroupHeader("Field Mapping") }
                    item {
                        val fieldMap = remember(profile.ankiFieldMap) { AnkiCardCreator.parseFieldMap(profile.ankiFieldMap) }
                        Column {
                            modelFields.forEach { field ->
                                AnkiFieldRow(
                                    fieldName = field,
                                    currentValue = fieldMap[field] ?: "",
                                    onSave = { newValue ->
                                        val map = fieldMap.toMutableMap()
                                        if (newValue.isBlank() || newValue == "{}") map.remove(field) else map[field] = newValue
                                        updateProfile { copy(ankiFieldMap = JSONObject(map).toString()) }
                                    }
                                )
                            }
                        }
                    }

                    item { SettingsGroupHeader("Screenshots") }
                    item {
                        val cropModeEntries = mapOf(
                            "full" to "Full screen",
                            "crop" to "Crop preset",
                            "no_screenshot" to "No screenshot"
                        )
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SettingsClickRow(
                                title = "Screenshot mode",
                                subtitle = cropModeEntries[profile.ankiCropMode] ?: profile.ankiCropMode,
                                onClick = { expanded = true }
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                cropModeEntries.forEach { (k, v) ->
                                    DropdownMenuItem(text = { Text(v) }, onClick = {
                                        updateProfile { copy(ankiCropMode = k) }
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }
                    if (profile.ankiCropMode == "crop") {
                        item {
                            val presetEntries = CropPresets.ASPECT_RATIO_PRESETS.associate { it.key to it.label } + ("full" to "Full")
                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SettingsClickRow(
                                    title = "Crop preset",
                                    subtitle = presetEntries[profile.ankiCropPreset] ?: "Full",
                                    onClick = { expanded = true }
                                )
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    presetEntries.forEach { (k, v) ->
                                        DropdownMenuItem(text = { Text(v) }, onClick = {
                                            updateProfile { copy(ankiCropPreset = k) }
                                            expanded = false
                                        })
                                    }
                                }
                            }
                        }
                    }

                    item { SettingsGroupHeader("Duplicates") }
                    item {
                        SettingsSwitchRow(
                            title = "Check duplicates",
                            subtitle = null,
                            checked = profile.ankiDupCheck,
                            onCheckedChange = { updateProfile { copy(ankiDupCheck = it) } }
                        )
                    }
                    if (profile.ankiDupCheck) {
                        item {
                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SettingsClickRow(
                                    title = "Duplicate scope",
                                    subtitle = if (profile.ankiDupScope == "all") "Everywhere" else "Deck only",
                                    onClick = { expanded = true }
                                )
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    mapOf("deck" to "Deck only", "all" to "Everywhere").forEach { (k, v) ->
                                        DropdownMenuItem(text = { Text(v) }, onClick = {
                                            updateProfile { copy(ankiDupScope = k) }
                                            expanded = false
                                        })
                                    }
                                }
                            }
                        }
                        item {
                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SettingsClickRow(
                                    title = "On duplicate",
                                    subtitle = when(profile.ankiDupAction) { "prevent" -> "Prevent"; "add" -> "Add anyway"; "overwrite" -> "Overwrite"; else -> profile.ankiDupAction },
                                    onClick = { expanded = true }
                                )
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    mapOf("prevent" to "Prevent", "add" to "Add anyway", "overwrite" to "Overwrite").forEach { (k, v) ->
                                        DropdownMenuItem(text = { Text(v) }, onClick = {
                                            updateProfile { copy(ankiDupAction = k) }
                                            expanded = false
                                        })
                                    }
                                }
                            }
                        }
                    }

                    item { SettingsGroupHeader("Sync") }
                    item {
                        SettingsSwitchRow(
                            title = "Sync after adding",
                            subtitle = "Trigger AnkiDroid sync when a card is created",
                            checked = profile.ankiSyncOnCreate,
                            onCheckedChange = { updateProfile { copy(ankiSyncOnCreate = it) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnkiFieldRow(
    fieldName: String,
    currentValue: String,
    onSave: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var textValue by remember(currentValue) { mutableStateOf(currentValue) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(fieldName, style = MaterialTheme.typography.bodyMedium)
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onSave(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("{}", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = false,
                maxLines = 5,
            )
            IconButton(
                onClick = { showPicker = true },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(24.dp),
            ) {
                Icon(Icons.Outlined.KeyboardArrowDown, "Select markers", modifier = Modifier.size(20.dp))
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Select Markers") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    markerSections.forEach { (section, markers) ->
                        item {
                            Text(section, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(markers) { marker ->
                            val markerStr = "{$marker}"
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (textValue.contains(markerStr)) {
                                        textValue = textValue.replace(markerStr, "")
                                    } else {
                                        textValue += markerStr
                                    }
                                    onSave(textValue)
                                }.padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = textValue.contains(markerStr), onCheckedChange = null)
                                Text(marker, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Done") }
            }
        )
    }
}
