package app.screenlookup.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.screenlookup.Di
import app.screenlookup.ui.ColorPickerDialog
import chimahon.dictionary.ui.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenLookupScreen(onBack: () -> Unit) {
    val prefs = remember { Di.dictionaryPreferences }

    val buttonSize by prefs.ocrButtonSize().collectAsState()
    val buttonAlpha by prefs.ocrButtonAlpha().collectAsState()
    val buttonColor by prefs.ocrButtonColor().collectAsState()
    
    val boxScaleX by prefs.ocrBoxScaleX().collectAsState()
    val boxScaleY by prefs.ocrBoxScaleY().collectAsState()
    
    val borderColor by prefs.ocrBorderColor().collectAsState()
    val borderOpacity by prefs.ocrBorderOpacity().collectAsState()
    val boxBgColor by prefs.ocrBoxBgColor().collectAsState()
    val boxOpacity by prefs.ocrBoxOpacity().collectAsState()
    val highlightColor by prefs.ocrHighlightColor().collectAsState()
    val highlightOpacity by prefs.ocrHighlightOpacity().collectAsState()
    val activeBgColor by prefs.activeOcrBgColor().collectAsState()
    val bgOpacity by prefs.activeOcrBgOpacity().collectAsState()

    var colorPickerState by remember { mutableStateOf<Pair<String, Int>?>(null) }

    if (colorPickerState != null) {
        val (title, currentColor) = colorPickerState!!
        ColorPickerDialog(
            title = title,
            initialColor = currentColor,
            onDismiss = { colorPickerState = null },
            onConfirm = { newColor ->
                when (title) {
                    "Button Color" -> prefs.ocrButtonColor().set(if (newColor == 0) 0xFF6750A4.toInt() else newColor)
                    "Border Color" -> prefs.ocrBorderColor().set(if (newColor == 0) 0xFF00AAFF.toInt() else newColor)
                    "Inactive Box Fill" -> prefs.ocrBoxBgColor().set(if (newColor == 0) 0xFFFFFFFF.toInt() else newColor)
                    "Active Box Fill" -> prefs.activeOcrBgColor().set(if (newColor == 0) 0xFFFFFFFF.toInt() else newColor)
                    "Highlight Color" -> prefs.ocrHighlightColor().set(if (newColor == 0) 0xFF6750A4.toInt() else newColor)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screen Overlay") },
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
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SettingsGroupHeader("Floating button") }
            
            // Floating Button Preview
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color(0xFF222222)), // Mock screen background
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(buttonSize.dp)
                            .alpha(buttonAlpha)
                            .background(
                                color = if (buttonColor == 0) Color(0xFF6750A4) else Color(buttonColor),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }

            item {
                SettingsSliderRow(
                    title = "Button size",
                    subtitle = "${buttonSize}dp",
                    value = buttonSize.toFloat(),
                    range = 40f..96f,
                    steps = 27,
                    onValueChange = { prefs.ocrButtonSize().set(it.toInt()) },
                )
            }
            item {
                SettingsSliderRow(
                    title = "Button opacity",
                    subtitle = "${(buttonAlpha * 100).toInt()}%",
                    value = buttonAlpha * 100f,
                    range = 10f..100f,
                    steps = 17,
                    onValueChange = { prefs.ocrButtonAlpha().set(it / 100f) },
                )
            }
            item {
                SettingsClickRow(
                    title = "Button color",
                    subtitle = "Tap to change",
                    trailing = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(if (buttonColor == 0) Color(0xFF6750A4) else Color(buttonColor), CircleShape)
                        )
                    },
                    onClick = { colorPickerState = "Button Color" to buttonColor }
                )
            }

            item { SettingsGroupHeader("OCR Box Geometry") }
            item {
                SettingsSliderRow(
                    title = "Box scale X",
                    subtitle = String.format("%.1fx", boxScaleX),
                    value = boxScaleX,
                    range = 0.5f..3f,
                    steps = 24,
                    onValueChange = { prefs.ocrBoxScaleX().set(it) },
                )
            }
            item {
                SettingsSliderRow(
                    title = "Box scale Y",
                    subtitle = String.format("%.1fx", boxScaleY),
                    value = boxScaleY,
                    range = 0.5f..3f,
                    steps = 24,
                    onValueChange = { prefs.ocrBoxScaleY().set(it) },
                )
            }

            item { SettingsGroupHeader("Outer Border") }
            item {
                SettingsClickRow(
                    title = "Border Color",
                    subtitle = "Tap to change",
                    trailing = {
                        Box(modifier = Modifier.size(24.dp).background(Color(borderColor), CircleShape))
                    },
                    onClick = { colorPickerState = "Border Color" to borderColor }
                )
            }
            item {
                SettingsSliderRow(
                    title = "Border Opacity",
                    subtitle = "${(borderOpacity * 100).toInt()}%",
                    value = borderOpacity * 100f,
                    range = 0f..100f,
                    steps = 19,
                    onValueChange = { prefs.ocrBorderOpacity().set(it / 100f) },
                )
            }

            item { SettingsGroupHeader("Inactive Boxes") }
            item {
                SettingsClickRow(
                    title = "Fill Color",
                    subtitle = "Tap to change",
                    trailing = {
                        Box(modifier = Modifier.size(24.dp).background(Color(boxBgColor), CircleShape))
                    },
                    onClick = { colorPickerState = "Inactive Box Fill" to boxBgColor }
                )
            }
            item {
                SettingsSliderRow(
                    title = "Fill Opacity",
                    subtitle = "${(boxOpacity * 100).toInt()}%",
                    value = boxOpacity * 100f,
                    range = 0f..100f,
                    steps = 19,
                    onValueChange = { prefs.ocrBoxOpacity().set(it / 100f) },
                )
            }

            item { SettingsGroupHeader("Active Boxes") }
            item {
                SettingsClickRow(
                    title = "Fill Color",
                    subtitle = "Tap to change",
                    trailing = {
                        Box(modifier = Modifier.size(24.dp).background(Color(activeBgColor), CircleShape))
                    },
                    onClick = { colorPickerState = "Active Box Fill" to activeBgColor }
                )
            }
            item {
                SettingsSliderRow(
                    title = "Fill Opacity",
                    subtitle = "${(bgOpacity * 100).toInt()}%",
                    value = bgOpacity * 100f,
                    range = 0f..100f,
                    steps = 19,
                    onValueChange = { prefs.activeOcrBgOpacity().set(it / 100f) },
                )
            }

            item { SettingsGroupHeader("Highlighted Word") }
            item {
                SettingsClickRow(
                    title = "Highlight Color",
                    subtitle = "Tap to change",
                    trailing = {
                        Box(modifier = Modifier.size(24.dp).background(Color(highlightColor), CircleShape))
                    },
                    onClick = { colorPickerState = "Highlight Color" to highlightColor }
                )
            }
            item {
                SettingsSliderRow(
                    title = "Highlight Opacity",
                    subtitle = "${(highlightOpacity * 100).toInt()}%",
                    value = highlightOpacity * 100f,
                    range = 0f..100f,
                    steps = 19,
                    onValueChange = { prefs.ocrHighlightOpacity().set(it / 100f) },
                )
            }

            // OCR Boxes Preview
            item { SettingsGroupHeader("Preview") }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(200.dp)
                        .background(Color(0xFF333333), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Inactive Box preview
                        Box(
                            modifier = Modifier
                                .border((1 * boxScaleX).dp, Color(borderColor).copy(alpha = borderOpacity), RoundedCornerShape(2.dp))
                                .background(Color(boxBgColor).copy(alpha = boxOpacity))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Inactive block of text",
                                color = Color.White, // Mock text color
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Active Box preview with Highlight
                        Box(
                            modifier = Modifier
                                .border((2 * boxScaleX).dp, Color(borderColor).copy(alpha = borderOpacity), RoundedCornerShape(2.dp))
                                .background(Color(activeBgColor).copy(alpha = bgOpacity))
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Active block with ",
                                    color = Color.White, // Mock text color
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Box(
                                    modifier = Modifier.background(Color(highlightColor).copy(alpha = highlightOpacity))
                                ) {
                                    Text(
                                        text = "highlighted word",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
