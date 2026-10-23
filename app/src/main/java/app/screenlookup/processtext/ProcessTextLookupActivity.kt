package app.screenlookup.processtext

import chimahon.dictionary.ui.createWarmWebView
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import chimahon.DictionaryRepository
import chimahon.dictionary.ui.DictionaryUiSingletons
import app.screenlookup.ui.AppTheme
import chimahon.dictionary.ui.viewer.OcrLookupPopup

class ProcessTextLookupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = ProcessTextLookupRequest.fromIntent(intent) ?: run {
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setFinishOnTouchOutside(true)

        setContent {
            AppTheme {
                ProcessTextLookupOverlay(
                    query = request.query,
                    onClose = ::finish,
                )
            }
        }
    }
}

@Composable
private fun ProcessTextLookupOverlay(
    query: String,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val density = LocalDensity.current
    val activeProfile = remember {
        DictionaryUiSingletons.dictionaryPreferences!!.profileStore.getActiveProfile()
    }
    val repository = remember { DictionaryRepository(context.getExternalFilesDir(null), { "legacy" }) }
    val webView = remember(activeProfile.languageCode) {
        createWarmWebView(context, activeProfile.languageCode)
    }

    DisposableEffect(webView) {
        onDispose {
            webView.runCatching {
                stopLoading()
                destroy()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose,
            ),
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val anchorY = ((screenHeightPx - with(density) { 400.dp.toPx() }) / 2f)
            .coerceAtLeast(0f)

        Box(modifier = Modifier.fillMaxSize()) {
            OcrLookupPopup(
                visible = true,
                lookupString = query,
                fullText = query,
                charOffset = 0,
                onDismiss = onClose,
                webView = webView,
                repository = repository,
                anchorX = screenWidthPx / 2f,
                anchorY = anchorY,
                anchorWidth = 1f,
                anchorHeight = 1f,
                isVertical = false,
                activeProfile = activeProfile,
                type = "process_text",
                usePopup = false,
            )
        }
    }
}
