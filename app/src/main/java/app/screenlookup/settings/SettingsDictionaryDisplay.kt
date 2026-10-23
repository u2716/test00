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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.screenlookup.Di
import chimahon.dictionary.ui.DictionaryPreferences
import chimahon.dictionary.ui.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDictionaryDisplayScreen(onBack: () -> Unit) {
    val prefs = remember { Di.dictionaryPreferences }

    val fontSize by prefs.fontSize().collectAsState()
    val fontFamily by prefs.fontFamily().collectAsState()
    val popupWidth by prefs.popupWidth().collectAsState()
    val popupHeight by prefs.popupHeight().collectAsState()
    val popupMode by prefs.popupMode().collectAsState()
    val themeMode by prefs.themeMode().collectAsState()
    val swipeDismiss by prefs.popupSwipeToDismiss().collectAsState()
    val swipeThreshold by prefs.popupSwipeThreshold().collectAsState()

    val frequencyModesPref = prefs.frequencyDisplayModes()
    val pitchModesPref = prefs.pitchDisplayModes()
    
    // Sync the boolean flags to the sets on first load
    LaunchedEffect(Unit) {
        frequencyModesPref.set(
            buildSet {
                if (prefs.showFrequencyHarmonic().get()) add(DictionaryPreferences.FREQ_HARMONIC)
                if (prefs.showFrequencyAverage().get()) add(DictionaryPreferences.FREQ_AVERAGE)
            },
        )
        pitchModesPref.set(
            buildSet {
                if (prefs.showPitchDiagram().get()) add(DictionaryPreferences.PITCH_DIAGRAM)
                if (prefs.showPitchNumber().get()) add(DictionaryPreferences.PITCH_NUMBER)
                if (prefs.showPitchText().get()) add(DictionaryPreferences.PITCH_TEXT)
            },
        )
    }

    val groupTerms by prefs.groupTerms().collectAsState()
    val groupPitches by prefs.groupPitches().collectAsState()
    val compactGlossary by prefs.compactGlossary().collectAsState()
    val showNavigation by prefs.showNavigationButtons().collectAsState()
    val recursiveMode by prefs.recursiveLookupMode().collectAsState()
    val scrollBehavior by prefs.scrollBehavior().collectAsState()
    val volumeKeyNav by prefs.volumeKeyNavigation().collectAsState()
    val customCss by prefs.customCss().collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dictionary Popup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SettingsGroupHeader("Layout") }
            item {
                SettingsSliderRow(
                    title = "Popup width",
                    subtitle = "$popupWidth dp",
                    value = popupWidth.toFloat(),
                    range = 200f..1920f,
                    steps = 171,
                    onValueChange = { prefs.popupWidth().set(it.toInt()) },
                )
            }
            item {
                SettingsSliderRow(
                    title = "Popup height",
                    subtitle = "$popupHeight dp",
                    value = popupHeight.toFloat(),
                    range = 100f..1080f,
                    steps = 97,
                    onValueChange = { prefs.popupHeight().set(it.toInt()) },
                )
            }
            item {
                SettingsDropdownRow(
                    title = "Popup mode",
                    value = popupMode,
                    options = mapOf("floating" to "Floating", "full_width" to "Full width", "full_height" to "Full height"),
                    onSelect = { prefs.popupMode().set(it) },
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Swipe to dismiss",
                    subtitle = "Horizontal swipe closes the popup",
                    checked = swipeDismiss,
                    onCheckedChange = { prefs.popupSwipeToDismiss().set(it) },
                )
            }
            if (swipeDismiss) {
                item {
                    SettingsSliderRow(
                        title = "Swipe threshold",
                        subtitle = "$swipeThreshold dp",
                        value = swipeThreshold.toFloat(),
                        range = 20f..100f,
                        steps = 79,
                        onValueChange = { prefs.popupSwipeThreshold().set(it.toInt()) },
                    )
                }
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
                        "Noto Serif JP" to "Noto Serif JP",
                        "Noto Sans JP" to "Noto Sans JP"
                    ),
                    onSelect = { prefs.fontFamily().set(it) },
                )
            }
            item {
                SettingsSliderRow(
                    title = "Font size",
                    subtitle = "$fontSize sp",
                    value = fontSize.toFloat(),
                    range = 8f..48f,
                    steps = 40,
                    onValueChange = { prefs.fontSize().set(it.toInt()) },
                )
            }

            item { SettingsGroupHeader("Theme") }
            item {
                SettingsDropdownRow(
                    title = "Theme mode",
                    value = themeMode,
                    options = mapOf("system" to "System", "app" to "App theme", "light" to "Light", "dark" to "Dark", "pure_black" to "Pure black"),
                    onSelect = { prefs.themeMode().set(it) },
                )
            }
            item {
                SettingsTextDialogRow(
                    title = "Custom CSS",
                    subtitle = if (customCss.isBlank()) "None" else "${customCss.length} chars",
                    currentValue = customCss,
                    multiline = true,
                    onSave = { prefs.customCss().set(it) },
                )
            }

            item { SettingsGroupHeader("Content") }
            item {
                SettingsRowView(
                    SettingsRows.Item.MultiSelect(
                        preference = frequencyModesPref,
                        entries = mapOf(
                            DictionaryPreferences.FREQ_HARMONIC to "Harmonic rank",
                            DictionaryPreferences.FREQ_AVERAGE to "Average freq"
                        ),
                        title = "Frequency display",
                        onValueChanged = { selected ->
                            prefs.showFrequencyHarmonic().set(DictionaryPreferences.FREQ_HARMONIC in selected)
                            prefs.showFrequencyAverage().set(DictionaryPreferences.FREQ_AVERAGE in selected)
                            true
                        }
                    )
                )
            }
            item {
                SettingsRowView(
                    SettingsRows.Item.MultiSelect(
                        preference = pitchModesPref,
                        entries = mapOf(
                            DictionaryPreferences.PITCH_DIAGRAM to "Graph",
                            DictionaryPreferences.PITCH_NUMBER to "012",
                            DictionaryPreferences.PITCH_TEXT to "Text"
                        ),
                        title = "Pitch Accent Display",
                        onValueChanged = { selected ->
                            prefs.showPitchDiagram().set(DictionaryPreferences.PITCH_DIAGRAM in selected)
                            prefs.showPitchNumber().set(DictionaryPreferences.PITCH_NUMBER in selected)
                            prefs.showPitchText().set(DictionaryPreferences.PITCH_TEXT in selected)
                            true
                        }
                    )
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Group terms",
                    checked = groupTerms,
                    onCheckedChange = { prefs.groupTerms().set(it) },
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Group pitches",
                    checked = groupPitches,
                    onCheckedChange = { prefs.groupPitches().set(it) },
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Compact glossaries",
                    subtitle = "Show senses inline instead of a numbered list",
                    checked = compactGlossary,
                    onCheckedChange = { prefs.compactGlossary().set(it) },
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Show navigation buttons",
                    checked = showNavigation,
                    onCheckedChange = { prefs.showNavigationButtons().set(it) },
                )
            }
            item {
                SettingsDropdownRow(
                    title = "Recursive lookup mode",
                    value = recursiveMode,
                    options = mapOf("tabs" to "Tabs", "stack" to "Stack (back)", "popup" to "Popup"),
                    onSelect = { prefs.recursiveLookupMode().set(it) },
                )
            }
            item {
                SettingsDropdownRow(
                    title = "Scroll behavior",
                    value = scrollBehavior,
                    options = mapOf(
                        DictionaryPreferences.SCROLL_SMOOTH to "Smooth",
                        DictionaryPreferences.SCROLL_INSTANT to "Instant"
                    ),
                    onSelect = { prefs.scrollBehavior().set(it) },
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Volume key navigation",
                    subtitle = "Use volume keys to scroll between dictionary entries",
                    checked = volumeKeyNav,
                    onCheckedChange = { prefs.volumeKeyNavigation().set(it) },
                )
            }
        }
    }
}
