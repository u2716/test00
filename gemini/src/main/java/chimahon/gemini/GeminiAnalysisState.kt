package chimahon.gemini

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Reusable state holder for a single Gemini analysis session, shared by the
 * screen lookup overlay, the manga reader, the novel reader, and the video
 * player.
 *
 * Call [analyze] to start a stream. Deltas accumulate in [content]. Use
 * [retry] to re-run with the last text/screenshot. Call [dismiss] to cancel
 * the job and hide the popup.
 */
class GeminiAnalysisState internal constructor(
    private val service: GeminiService,
) {
    var content by mutableStateOf("")
        private set
    var visible by mutableStateOf(false)
        private set
    var error by mutableStateOf<GeminiError?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    /**
     * The raw token Flow to pass to StreamingMarkdown.
     * Collecting it updates content, loading, and error as side effects.
     * The UI must collect this exactly once — StreamingTypewriter does that.
     */
    var tokenFlow by mutableStateOf<Flow<String>?>(null)
        private set

    val fontSize: Int get() = service.fontSize
    val fontFamily: String get() = service.fontFamily

    private var job: Job? = null
    private var lastText: String? = null
    private var lastScreenshot: Bitmap? = null
    private var lastForceText: Boolean = false
    private var lastForceScreenshot: Boolean = false
    private var generation: Int = 0

    fun analyze(
        scope: CoroutineScope,
        text: String? = null,
        screenshot: Bitmap? = null,
        forceText: Boolean = false,
        forceScreenshot: Boolean = false,
    ) {
        lastText = text
        lastScreenshot = screenshot
        lastForceText = forceText
        lastForceScreenshot = forceScreenshot
        start(scope)
    }

    fun retry(scope: CoroutineScope) = start(scope)

    fun dismiss() {
        job?.cancel()
        visible = false
    }

    private fun start(scope: CoroutineScope) {
        job?.cancel()
        content = ""
        visible = true
        error = null
        loading = true
        val myGeneration = ++generation

        tokenFlow = service
            .streamAnalysis(lastText, lastScreenshot, lastForceText, lastForceScreenshot)
            .onStart {
                if (generation == myGeneration) loading = true
            }
            .onEach { delta ->
                if (generation == myGeneration) content += delta
            }
            .onCompletion {
                if (generation == myGeneration) loading = false
            }
            .catch { e ->
                if (generation == myGeneration) {
                    error = when (e) {
                        is HttpAnalysisException -> GeminiError.Http(e.code, e.message)
                        is kotlin.coroutines.cancellation.CancellationException -> throw e
                        else -> when (e.message) {
                            "API_KEY_BLANK" -> GeminiError.NoApiKey
                            "NO_INPUT" -> GeminiError.NoInput
                            "CUSTOM_ENDPOINT_BLANK" -> GeminiError.CustomEndpointBlank
                            "CUSTOM_ENDPOINT_INVALID" -> GeminiError.CustomEndpointInvalid
                            else -> GeminiError.Generic(e.localizedMessage ?: "Unknown error")
                        }
                    }
                    loading = false
                }
            }

        // No separate collector: the UI's StreamingTypewriter collects
        // tokenFlow, which triggers the operators above exactly once.
    }
}

@Composable
fun rememberGeminiAnalysisState(): GeminiAnalysisState {
    val service = remember {
        checkNotNull(GeminiSingletons.geminiService) { "GeminiSingletons.geminiService not set" }
    }
    return remember(service) { GeminiAnalysisState(service) }
}
