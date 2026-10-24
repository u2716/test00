package app.screenlookup.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.screenlookup.Di
import app.screenlookup.dictionary.DictionaryUpdateJob
import app.screenlookup.dictionary.DictionaryUpdater
import chimahon.HoshiDicts
import chimahon.anki.AnkiProfile
import chimahon.audio.WordAudioSource
import chimahon.dictionary.ui.collectAsState
import java.io.File
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsDictionaryManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Di.dictionaryPreferences }
    val profileStore = prefs.profileStore
    val json = remember { Json { ignoreUnknownKeys = true } }

    var currentProfile by remember { mutableStateOf(profileStore.getActiveProfile()) }
    var dictionaries by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    val autoUpdateEnabled by prefs.autoUpdateEnabled().collectAsState()
    val autoUpdateInterval by prefs.autoUpdateInterval().collectAsState()
    var typeFilter by remember { mutableStateOf<String?>(null) }
    val koreanParser by prefs.koreanParserMode().collectAsState()
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var pendingRename by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    fun reloadData() {
        currentProfile = profileStore.getActiveProfile()
        dictionaries = scanDictionaries(context)
    }

    LaunchedEffect(Unit) {
        Di.reconcileDictionariesWithDisk()
        reloadData()
    }

    val visibleDicts = remember(currentProfile.dictionaryOrder, dictionaries, typeFilter) {
        val ordered = (currentProfile.dictionaryOrder.filter { it in dictionaries } +
                dictionaries.keys.filter { it !in currentProfile.dictionaryOrder }).distinct()
        if (typeFilter == null) ordered
        else ordered.filter { dictionaries[it]?.contains(typeFilter) == true }
    }

    val lazyState = rememberLazyListState()
    val dictListState: SnapshotStateList<String> = remember { mutableStateListOf() }
    LaunchedEffect(visibleDicts) {
        if (dictListState.toList() != visibleDicts) {
            dictListState.clear()
            dictListState.addAll(visibleDicts)
        }
    }

    val reorderable = rememberReorderableLazyListState(lazyState) { from, to ->
        // Identify items by key, not by LazyColumn index. The list is preceded
        // by a variable number of `item {}` blocks (headers + conditional
        // import-error row), so `from.index - N` drifts and the guard fails.
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState

        val fromIdx = dictListState.indexOf(fromKey)
        val toIdx = dictListState.indexOf(toKey)
        if (fromIdx == -1 || toIdx == -1 || fromIdx == toIdx) {
            return@rememberReorderableLazyListState
        }

        dictListState.add(toIdx, dictListState.removeAt(fromIdx))

        // Persist the new order back to the active profile.
        val full = currentProfile.dictionaryOrder.toMutableList()
        if (typeFilter == null) {
            full.clear()
            full.addAll(dictListState)
        } else {
            val visible = currentProfile.dictionaryOrder
                .filter { dictionaries[it]?.contains(typeFilter) == true }
            var vi = 0
            for (i in full.indices) {
                if (full[i] in visible) full[i] = dictListState[vi++]
            }
        }
        val updated = currentProfile.copy(dictionaryOrder = full)
        profileStore.updateProfile(updated)
        currentProfile = updated
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            importError = null
            val result = withContext(Dispatchers.IO) {
                runCatching { importDictionaryFromUri(context, uri) }
            }
            importing = false
            result.onFailure { importError = it.message ?: "Import failed" }
            if (result.isSuccess) {
                Di.reconcileDictionariesWithDisk()
                reloadData()
            }
        }
    }

    pendingRename?.let { name ->
        val currentDisplay = prefs.getDisplayName(name)
        var text by remember { mutableStateOf(currentDisplay ?: name) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Display name for $name") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = text.trim()
                    prefs.setDisplayName(name, if (trimmed.isBlank() || trimmed == name) null else trimmed)
                    pendingRename = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Cancel") } }
        )
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete dictionary?") },
            text = { Text("Delete \"$name\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { deleteDictionary(context, name) }
                        val updated = profileStore.getActiveProfile().let { prof ->
                            prof.copy(
                                dictionaryOrder = prof.dictionaryOrder - name,
                                enabledDictionaries = prof.enabledDictionaries - name,
                                dictionaryDisplayModes = prof.dictionaryDisplayModes - name
                            )
                        }
                        profileStore.updateProfile(updated)
                        reloadData()
                        pendingDelete = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    if (importing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing dictionary...") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("Please wait.")
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dictionaries and audio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = lazyState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item { SettingsGroupHeader("Dictionary profiles") }
            item {
                ProfileRow(currentProfile, profileStore, onProfileChanged = { reloadData() })
            }

            item { SettingsGroupHeader("Imported dictionaries") }
            item {
                SettingsClickRow(
                    title = "Import dictionary",
                    subtitle = "Import a Yomitan dictionary ZIP file",
                    enabled = !importing,
                    onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                )
            }
            if (importError != null) {
                item { Text(importError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }

            item {
                CollapseBehaviorRow(
                    current = currentProfile.dictionaryCollapseMode,
                    onSelect = { mode ->
                        val updated = currentProfile.copy(dictionaryCollapseMode = mode)
                        profileStore.updateProfile(updated)
                        currentProfile = updated
                    }
                )
            }

            item { TypeFilterRow(current = typeFilter, onSelect = { typeFilter = it }) }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

            if (dictListState.isEmpty()) {
                item { Text("No dictionaries installed.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                itemsIndexed(dictListState, key = { _, name -> name }) { _, name ->
                    ReorderableItem(reorderable, key = name) {
                        DictRow(
                            name = name,
                            types = dictionaries[name].orEmpty(),
                            displayName = prefs.getDisplayName(name),
                            enabled = currentProfile.enabledDictionaries.isEmpty() || name in currentProfile.enabledDictionaries,
                            displayMode = currentProfile.dictionaryDisplayModes[name] ?: AnkiProfile.DICTIONARY_DISPLAY_FALLBACK,
                            collapseMode = currentProfile.dictionaryCollapseMode,
                            onToggle = {
                                val prof = profileStore.getActiveProfile()
                                val next = if (prof.enabledDictionaries.isEmpty()) {
                                    dictionaries.keys.filter { it != name }.toSet()
                                } else if (name in prof.enabledDictionaries) {
                                    prof.enabledDictionaries - name
                                } else {
                                    prof.enabledDictionaries + name
                                }
                                val updated = prof.copy(enabledDictionaries = next)
                                profileStore.updateProfile(updated)
                                currentProfile = updated
                            },
                            onDelete = { pendingDelete = name },
                            onRename = { pendingRename = name },
                            onDisplayModeChanged = { mode ->
                                val updated = currentProfile.copy(dictionaryDisplayModes = currentProfile.dictionaryDisplayModes + (name to mode))
                                profileStore.updateProfile(updated)
                                currentProfile = updated
                            },
                            dragModifier = Modifier.draggableHandle()
                        )
                    }
                }
            }

            item { SettingsGroupHeader("Advanced Parsing") }
            item {
                SettingsDropdownRow(
                    title = "Korean parser",
                    subtitle = "Choose the parser used for Korean dictionary lookups",
                    value = koreanParser,
                    options = mapOf(
                        chimahon.dictionary.ko.KoreanParserMode.Legacy to "Legacy parser",
                        chimahon.dictionary.ko.KoreanParserMode.Analyzer to "Morpheme analyzer"
                    ),
                    onSelect = { prefs.koreanParserMode().set(it) },
                )
            }

            item { SettingsGroupHeader("Updates") }
            item {
                val lastCheckMs by prefs.lastDictUpdateCheck().collectAsState()
                val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                val lastCheckedSubtitle = if (lastCheckMs > 0L) {
                    "Last checked: ${sdf.format(Date(lastCheckMs))}"
                } else {
                    "Not checked yet"
                }
                
                var isChecking by remember { mutableStateOf(false) }
                var updateProgressText by remember { mutableStateOf("") }
                var intervalExpanded by remember { mutableStateOf(false) }
                
                val intervalEntries = mapOf(
                    "0" to "Never",
                    "1" to "Every hour",
                    "6" to "Every 6 hours",
                    "12" to "Every 12 hours",
                    "24" to "Every 24 hours",
                    "48" to "Every 2 days",
                    "72" to "Every 3 days",
                    "168" to "Every week"
                )
                val currentIntervalKey = if (!autoUpdateEnabled) "0" else autoUpdateInterval.toString()
                val currentIntervalLabel = intervalEntries[currentIntervalKey] ?: "Never"

                Box(modifier = Modifier.fillMaxWidth()) {
                    SettingsClickRow(
                        title = "Auto-update dictionaries",
                        subtitle = currentIntervalLabel,
                        onClick = { intervalExpanded = true }
                    )
                    DropdownMenu(expanded = intervalExpanded, onDismissRequest = { intervalExpanded = false }) {
                        intervalEntries.forEach { (k, v) ->
                            DropdownMenuItem(text = { Text(v) }, onClick = {
                                val hours = k.toIntOrNull() ?: 0
                                if (hours == 0) {
                                    prefs.autoUpdateEnabled().set(false)
                                    DictionaryUpdateJob.schedule(context, 0)
                                } else {
                                    prefs.autoUpdateEnabled().set(true)
                                    prefs.autoUpdateInterval().set(hours)
                                    DictionaryUpdateJob.schedule(context, hours)
                                }
                                intervalExpanded = false
                            })
                        }
                    }
                }
                
                SettingsClickRow(
                    title = "Check for updates",
                    subtitle = lastCheckedSubtitle,
                    enabled = !isChecking,
                    onClick = {
                        scope.launch {
                            isChecking = true
                            val updates = DictionaryUpdater.checkForUpdates(context, Di.httpClient)
                            if (updates.isEmpty()) {
                                updateProgressText = "All dictionaries are up to date."
                            } else {
                                var successCount = 0
                                for ((index, u) in updates.withIndex()) {
                                    updateProgressText = "Updating ${u.name} (${index + 1}/${updates.size})..."
                                    if (DictionaryUpdater.installUpdate(context, u)) {
                                        successCount++
                                    }
                                }
                                updateProgressText = "Successfully updated $successCount dictionaries."
                                Di.reconcileDictionariesWithDisk()
                                reloadData()
                            }
                            prefs.lastDictUpdateCheck().set(System.currentTimeMillis())
                            isChecking = false
                        }
                    }
                )

                if (isChecking) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Checking for updates...") },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(if (updateProgressText.isBlank()) "Scanning dictionaries..." else updateProgressText)
                            }
                        },
                        confirmButton = {}
                    )
                } else if (updateProgressText.isNotBlank()) {
                    AlertDialog(
                        onDismissRequest = { updateProgressText = "" },
                        title = { Text("Update Complete") },
                        text = { Text(updateProgressText) },
                        confirmButton = {
                            TextButton(onClick = { updateProgressText = "" }) { Text("OK") }
                        }
                    )
                }
            }

            item { SettingsGroupHeader("Word Audio") }
            item { WordAudioSection(prefs) }
        }
    }
}

@Composable
fun ProfileRow(profile: AnkiProfile, profileStore: chimahon.anki.AnkiProfileStore, onProfileChanged: () -> Unit) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNewDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Active profile: ${profile.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { showRenameDialog = true }) { Icon(Icons.Outlined.Edit, "Rename") }
            IconButton(onClick = { showDeleteDialog = true }, enabled = profileStore.getProfiles().size > 1) { Icon(Icons.Outlined.Delete, "Delete") }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            items(profileStore.getProfiles()) { p ->
                FilterChip(selected = p.id == profile.id, onClick = { profileStore.setActiveProfile(p.id); onProfileChanged() }, label = { Text(p.name) })
            }
            item { FilterChip(selected = false, onClick = { showNewDialog = true }, label = { Text("+") }) }
        }

        var langExpanded by remember { mutableStateOf(false) }
        val languages = mapOf("" to "Any", "ja" to "Japanese", "ko" to "Korean", "ar" to "Arabic", "zh" to "Chinese", "en" to "English", "de" to "German", "fr" to "French", "ru" to "Russian", "es" to "Spanish", "it" to "Italian")
        
        SettingsClickRow("Language", languages[profile.languageCode] ?: profile.languageCode, { langExpanded = true })
        DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
            languages.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { profileStore.updateProfile(profile.copy(languageCode = k)); onProfileChanged(); langExpanded = false }) }
        }
        
        var scanExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            SettingsClickRow("Scan", profile.scanResolution.ifBlank { "Auto" }, { scanExpanded = true })
            DropdownMenu(expanded = scanExpanded, onDismissRequest = { scanExpanded = false }) {
                mapOf("" to "Auto", AnkiProfile.SCAN_RESOLUTION_WORD to "Word", AnkiProfile.SCAN_RESOLUTION_CHARACTER to "Character").forEach { (k, v) ->
                    DropdownMenuItem(text = { Text(v) }, onClick = { profileStore.updateProfile(profile.copy(scanResolution = k)); onProfileChanged(); scanExpanded = false })
                }
            }
        }
        
        var searchExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            SettingsClickRow("Search", profile.searchResolution.ifBlank { "Auto" }, { searchExpanded = true })
            DropdownMenu(expanded = searchExpanded, onDismissRequest = { searchExpanded = false }) {
                mapOf("" to "Auto", AnkiProfile.SEARCH_RESOLUTION_WORD to "Word", AnkiProfile.SEARCH_RESOLUTION_LETTER to "Letter").forEach { (k, v) ->
                    DropdownMenuItem(text = { Text(v) }, onClick = { profileStore.updateProfile(profile.copy(searchResolution = k)); onProfileChanged(); searchExpanded = false })
                }
            }
        }
    }

    if (showRenameDialog) {
        var name by remember { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false }, title = { Text("Rename Profile") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { profileStore.updateProfile(profile.copy(name = name.trim())); onProfileChanged() }; showRenameDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }
    if (showNewDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewDialog = false }, title = { Text("New Profile") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { profileStore.addProfile(profile.copy(id = java.util.UUID.randomUUID().toString(), name = name.trim())); onProfileChanged() }; showNewDialog = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("Cancel") } }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false }, title = { Text("Delete Profile") }, text = { Text("Are you sure you want to delete '${profile.name}'?") },
            confirmButton = { TextButton(onClick = { profileStore.deleteProfile(profile.id); onProfileChanged(); showDeleteDialog = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun CollapseBehaviorRow(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        AnkiProfile.DICTIONARY_COLLAPSE_EXPAND_ALL to "Expand all dictionaries",
        AnkiProfile.DICTIONARY_COLLAPSE_EXPAND_FIRST_AVAILABLE to "Expand first available",
        AnkiProfile.DICTIONARY_COLLAPSE_COLLAPSE_ALL to "Collapse all dictionaries",
        AnkiProfile.DICTIONARY_COLLAPSE_CUSTOM to "Custom per dictionary"
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Collapse behavior", style = MaterialTheme.typography.titleSmall)
        Text("Controls which dictionary groups open first in lookup results.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(labels[current] ?: current, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.KeyboardArrowDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                labels.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { onSelect(k); expanded = false }) }
            }
        }
    }
}

@Composable
fun TypeFilterRow(current: String?, onSelect: (String?) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Filter:", style = MaterialTheme.typography.labelMedium)
        FilterChip(selected = current == null, onClick = { onSelect(null) }, label = { Text("All") })
        listOf("term" to "term", "frequency" to "freq", "pitch" to "pitch", "kanji" to "kanji").forEach { (k, v) ->
            FilterChip(selected = current == k, onClick = { onSelect(if (current == k) null else k) }, label = { Text(v) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DictRow(name: String, types: Set<String>, displayName: String?, enabled: Boolean, displayMode: String, collapseMode: String, onToggle: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit, onDisplayModeChanged: (String) -> Unit, dragModifier: Modifier) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DragHandle, "Drag", modifier = dragModifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = onRename)) {
                    Text(displayName ?: name, style = MaterialTheme.typography.bodyLarge)
                    Text(types.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(if (enabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, "Toggle", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Edit, "Rename", modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
            }
            if (collapseMode == AnkiProfile.DICTIONARY_COLLAPSE_CUSTOM) {
                var expanded by remember { mutableStateOf(false) }
                val label = when (displayMode) {
                    AnkiProfile.DICTIONARY_DISPLAY_ALWAYS_EXPANDED -> "Always expanded"
                    AnkiProfile.DICTIONARY_DISPLAY_ALWAYS_COLLAPSED -> "Always collapsed"
                    else -> "Fallback"
                }
                Box(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp)) {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Display: $label", modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.KeyboardArrowDown, null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(AnkiProfile.DICTIONARY_DISPLAY_ALWAYS_EXPANDED to "Always expanded", AnkiProfile.DICTIONARY_DISPLAY_FALLBACK to "Fallback", AnkiProfile.DICTIONARY_DISPLAY_ALWAYS_COLLAPSED to "Always collapsed").forEach { (k, l) ->
                            DropdownMenuItem(text = { Text(l) }, onClick = { onDisplayModeChanged(k); expanded = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WordAudioSection(prefs: chimahon.dictionary.ui.DictionaryPreferences) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val enabled by prefs.wordAudioEnabled().collectAsState()
    val autoplay by prefs.wordAudioAutoplay().collectAsState()
    val localEnabled by prefs.wordAudioLocalEnabled().collectAsState()
    val localUri by prefs.wordAudioLocalUri().collectAsState()
    val sourcesJson by prefs.wordAudioSources().collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val wordAudioLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                prefs.wordAudioLocalUri().set(uri.toString())
                prefs.wordAudioLocalPath().set("") // Clear old legacy path
            } catch (_: Exception) {}
        }
    }
    val sources = remember(sourcesJson) { runCatching { json.decodeFromString<List<WordAudioSource>>(sourcesJson) }.getOrDefault(emptyList()) }

    fun updateSources(newSources: List<WordAudioSource>) {
        prefs.wordAudioSources().set(json.encodeToString(newSources))
    }

    Column {
        SettingsSwitchRow("Enable Word Audio", "Show audio buttons in dictionary entries", enabled, { prefs.wordAudioEnabled().set(it) })
        SettingsSwitchRow("Autoplay", "Automatically play the first audio found", autoplay, { prefs.wordAudioAutoplay().set(it) })
        
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Local Audio (android.db)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = localEnabled, onCheckedChange = { prefs.wordAudioLocalEnabled().set(it) })
            }
            if (localEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (localUri.isBlank()) "No database selected" else "Database loaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { wordAudioLauncher.launch(arrayOf("*/*")) }) {
                    Text("Select Database")
                }
            }
        }

        Text("Online Sources", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
        
        sources.forEachIndexed { index, source ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.bodyMedium)
                    Text(source.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Checkbox(checked = source.isEnabled, onCheckedChange = { checked -> val list = sources.toMutableList(); list[index] = source.copy(isEnabled = checked); updateSources(list) })
                IconButton(onClick = { val list = sources.toMutableList(); list.removeAt(index); updateSources(list) }) { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
            }
        }

        var newName by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }
        Column(modifier = Modifier.padding(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)) {
            Text("Add Source", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(newUrl, { newUrl = it }, label = { Text("URL Template ({term}, {reading})") }, modifier = Modifier.weight(1f), singleLine = true)
                IconButton(onClick = { if (newName.isNotBlank() && newUrl.isNotBlank()) { updateSources(sources + WordAudioSource(name = newName, url = newUrl)); newName = ""; newUrl = "" } }, enabled = newName.isNotBlank() && newUrl.isNotBlank()) { Icon(Icons.Outlined.Add, "Add") }
            }
        }
    }
}

private fun scanDictionaries(context: Context): Map<String, Set<String>> {
    val dir = File(context.getExternalFilesDir(null), "dictionaries")
    if (!dir.isDirectory) return emptyMap()
    val out = mutableMapOf<String, MutableSet<String>>()
    listOf("term", "frequency", "pitch", "kanji").forEach { type ->
        File(dir, type).listFiles()?.filter { it.isDirectory }?.forEach { sub -> out.getOrPut(sub.name) { mutableSetOf() }.add(type) }
    }
    return out
}

private fun deleteDictionary(context: Context, name: String) {
    val dir = File(context.getExternalFilesDir(null), "dictionaries")
    listOf("term", "frequency", "pitch", "kanji").forEach { type -> File(File(dir, type), name).deleteRecursively() }
}

private fun importDictionaryFromUri(context: Context, uri: Uri) {
    val tempZip = File(context.cacheDir, "chimahon_import_${System.currentTimeMillis()}.zip")
    val tempDir = File(context.cacheDir, "chimahon_import_tmp_${System.currentTimeMillis()}")
    try {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open selected file")
        stream.use { input -> tempZip.outputStream().use { out -> input.copyTo(out) } }
        tempDir.mkdirs()
        val result = HoshiDicts.importDictionary(tempZip.absolutePath, tempDir.absolutePath)
        if (!result.success) error(result.title.ifBlank { "Import failed" })
        val importedDir = tempDir.listFiles()?.firstOrNull { it.isDirectory } ?: error("Import succeeded but no directory found")
        val title = if (result.title.isNotBlank()) result.title else importedDir.name
        val dictDir = File(context.getExternalFilesDir(null), "dictionaries")
        if (!dictDir.exists()) dictDir.mkdirs()
        
        data class T(val type: String, val count: Long)
        val targets = buildList {
            if (result.termCount > 0) add(T("term", result.termCount))
            if (result.freqCount > 0) add(T("frequency", result.freqCount))
            if (result.pitchCount > 0) add(T("pitch", result.pitchCount))
            if (result.kanjiCount > 0) add(T("kanji", result.kanjiCount))
        }
        if (targets.isEmpty()) error("Dictionary has no recognized entries")
        
        for (t in targets) {
            val typeDir = File(dictDir, t.type)
            if (!typeDir.exists()) typeDir.mkdirs()
            val dest = File(typeDir, title)
            if (dest.exists()) dest.deleteRecursively()
            importedDir.copyRecursively(dest, overwrite = true)
        }
    } finally {
        tempZip.delete()
        tempDir.deleteRecursively()
    }
}
