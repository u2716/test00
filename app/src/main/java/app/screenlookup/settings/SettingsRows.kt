package app.screenlookup.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import chimahon.dictionary.ui.collectAsState
import chimahon.preferences.Preference

object SettingsRows {
    sealed class Item {
        abstract val title: String
        open val subtitle: String? = null

        data class Toggle(
            val preference: Preference<Boolean>,
            override val title: String,
            override val subtitle: String? = null,
        ) : Item()

        data class Slider(
            val value: Int,
            override val title: String,
            override val subtitle: String? = null,
            val valueRange: IntRange,
            val steps: Int = 0,
            val onValueChanged: (Int) -> Unit,
        ) : Item()

        data class Dropdown(
            val preference: Preference<String>,
            val entries: Map<String, String>,
            override val title: String,
            override val subtitle: String? = null,
            val onValueChanged: (String) -> Boolean = { true },
        ) : Item()

        data class BasicDropdown(
            val value: String,
            val entries: Map<String, String>,
            override val title: String,
            override val subtitle: String? = null,
            val onValueChanged: (String) -> Unit,
        ) : Item()

        data class MultiSelect(
            val preference: Preference<Set<String>>,
            val entries: Map<String, String>,
            override val title: String,
            override val subtitle: String? = null,
            val onValueChanged: (Set<String>) -> Boolean = { true },
        ) : Item()

        data class Button(
            override val title: String,
            override val subtitle: String? = null,
            val onClick: () -> Unit,
        ) : Item()

        data class Custom(
            override val title: String,
            val content: @Composable () -> Unit,
        ) : Item()

        data class Info(
            override val title: String,
        ) : Item()
    }

    data class Section(
        val title: String,
        val items: List<Item>,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    title: String,
    groups: List<SettingsRows.Section>,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            groups.forEach { group ->
                item(key = "h-${group.title}") { SettingsGroupHeader(group.title) }
                items(group.items) { item -> SettingsRowView(item) }
            }
        }
    }
}

@Composable
fun SettingsRowView(item: SettingsRows.Item) {
    when (item) {
        is SettingsRows.Item.Toggle -> {
            val value by item.preference.collectAsState()
            SettingsSwitchRow(
                title = item.title,
                subtitle = item.subtitle,
                checked = value,
                onCheckedChange = { item.preference.set(it) },
            )
        }
        is SettingsRows.Item.Slider -> {
            SettingsSliderRow(
                title = item.title,
                subtitle = item.subtitle ?: item.value.toString(),
                value = item.value.toFloat(),
                range = item.valueRange.first.toFloat()..item.valueRange.last.toFloat(),
                steps = item.steps,
                onValueChange = { item.onValueChanged(it.toInt()) },
            )
        }
        is SettingsRows.Item.Dropdown -> {
            val value by item.preference.collectAsState()
            SettingsDropdownRow(
                title = item.title,
                subtitle = item.subtitle,
                value = value,
                options = item.entries,
                onSelect = { if (item.onValueChanged(it)) item.preference.set(it) },
            )
        }
        is SettingsRows.Item.BasicDropdown -> {
            SettingsDropdownRow(
                title = item.title,
                subtitle = item.subtitle,
                value = item.value,
                options = item.entries,
                onSelect = item.onValueChanged,
            )
        }
        is SettingsRows.Item.MultiSelect -> {
            val current by item.preference.collectAsState()
            var showDialog by remember { mutableStateOf(false) }
            val summary = current.mapNotNull { item.entries[it] }.joinToString(", ").ifBlank { "None" }
            SettingsClickRow(
                title = item.title,
                subtitle = item.subtitle ?: summary,
                onClick = { showDialog = true },
            )
            if (showDialog) {
                var working by remember(current) { mutableStateOf(current) }
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text(item.title) },
                    text = {
                        Column {
                            item.entries.forEach { (key, label) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            working = if (key in working) working - key else working + key
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = key in working,
                                        onCheckedChange = { c ->
                                            working = if (c) working + key else working - key
                                        },
                                    )
                                    Text(label, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (item.onValueChanged(working)) item.preference.set(working)
                            showDialog = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                    },
                )
            }
        }
        is SettingsRows.Item.Button -> {
            SettingsClickRow(
                title = item.title,
                subtitle = item.subtitle,
                onClick = item.onClick,
            )
        }
        is SettingsRows.Item.Custom -> item.content()
        is SettingsRows.Item.Info -> {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
