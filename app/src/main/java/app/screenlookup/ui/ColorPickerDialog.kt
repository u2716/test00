package app.screenlookup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val controller = rememberColorPickerController()
    
    // Safely handle 0 (transparent/unset) by falling back to a default color
    val safeColor = if (initialColor == 0 || Color(initialColor).alpha == 0f) {
        Color(0xFF6750A4) // Default Material Purple
    } else {
        Color(initialColor).copy(alpha = 1f)
    }

    // Observe the controller directly (it is a State<Color>, not a StateFlow)
    val selectedColor by controller.selectedColor
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HsvColorPicker(
                    modifier = Modifier.size(240.dp),
                    controller = controller,
                    initialColor = safeColor,
                )
                Spacer(modifier = Modifier.height(16.dp))
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    controller = controller,
                    initialColor = safeColor,
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Preview Box
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(selectedColor, CircleShape)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selectedColor.toArgb())
                onDismiss()
            }) {
                Text("Confirm Color")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onConfirm(0) // 0 means reset/default
                onDismiss()
            }) {
                Text("Reset")
            }
        }
    )
}
