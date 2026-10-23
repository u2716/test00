package app.screenlookup.screenlookup

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.screenlookup.ui.AppTheme

class ScreenLookupPermissionActivity : ComponentActivity() {

    private var returnedFromOverlaySettings = false
    private var projectionRequested = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenLookupService.start(this, result.resultCode, data)
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Capture permission denied", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ScreenLookupServiceState.isEntryInProgress = true
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScreenLookupPermissionContent(
                        onRequestOverlay = ::openOverlaySettings,
                        onCancel = ::finish,
                    )
                }
            }
        }

        if (Settings.canDrawOverlays(this)) {
            requestProjection()
        }
    }

    override fun onResume() {
        super.onResume()
        if (returnedFromOverlaySettings && Settings.canDrawOverlays(this)) {
            returnedFromOverlaySettings = false
            requestProjection()
        }
    }

    override fun onDestroy() {
        ScreenLookupServiceState.isEntryInProgress = false
        super.onDestroy()
    }

    private fun openOverlaySettings() {
        returnedFromOverlaySettings = true
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.recoverCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.onFailure {
            returnedFromOverlaySettings = false
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestProjection() {
        if (projectionRequested) return
        projectionRequested = true
        val mpm = getSystemService<MediaProjectionManager>()
        if (mpm == null) {
            Toast.makeText(this, "Screen lookup unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        projectionLauncher.launch(mpm.createScreenLookupCaptureIntent())
    }

    private fun MediaProjectionManager.createScreenLookupCaptureIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
        } else {
            createScreenCaptureIntent()
        }
    }
}

@Composable
private fun ScreenLookupPermissionContent(
    onRequestOverlay: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Enable screen lookup",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Grant overlay permission so the floating button can appear over other apps.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) {
            Text("Cancel")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRequestOverlay) {
            Text("Open settings")
        }
    }
}
