package app.screenlookup.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.screenlookup.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onOpen: (Screen) -> Unit,
) {
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val allItems = listOf(
        Triple("General", "Appearance, backup, and restore", Screen.SettingsGeneral),
        Triple("Dictionaries & Audio", "Import dictionaries, word audio", Screen.SettingsDictionaryManagement),
        Triple("Dictionary Popup", "Layout, typography, theme, content", Screen.SettingsDictionaryDisplay),
        Triple("Anki", "Deck, model, fields, duplicate check", Screen.SettingsAnki),
        Triple("OCR", "Engine and model downloads", Screen.SettingsOcr),
        Triple("Screen Overlay", "Overlay button, OCR box appearance", Screen.SettingsScreenLookup),
        Triple("Translation (Gemini)", "API key, model, prompt", Screen.SettingsGemini)
    )

    val filteredItems = allItems.filter {
        searchQuery.isBlank() || 
        it.first.contains(searchQuery, ignoreCase = true) || 
        it.second.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSearching) "Search" else "Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) { isSearching = false; searchQuery = "" } 
                        else { onBack() }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (isSearching) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        placeholder = { Text("Search settings...") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                item {
                    Text(
                        "No settings found.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredItems) { item ->
                    SettingsClickRow(
                        title = item.first,
                        subtitle = item.second,
                        onClick = { onOpen(item.third) },
                    )
                }
            }
        }
    }
}
