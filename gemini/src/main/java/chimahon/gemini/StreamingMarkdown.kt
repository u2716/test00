package chimahon.gemini

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.fluid.afm.markdown.MarkdownParser
import com.fluid.afm.markdown.MarkdownParserFactory
import com.fluid.afm.markdown.widget.PrinterMarkDownTextView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
fun StreamingMarkdown(
    tokens: Flow<String>,
    modifier: Modifier = Modifier,
    fontSize: Int = 0,
    fontFamily: String = "",
) {
    val textColor = LocalContentColor.current.toArgb()

    val cleanedTokens = remember(tokens) {
        tokens.map { text ->
            text.replace(Regex("\\n{3,}"), "\\n\\n")
        }
    }

    val markdownState = remember { mutableStateOf("") }

    LaunchedEffect(cleanedTokens) {
        val buffer = StringBuilder()
        cleanedTokens.collect { chunk ->
            buffer.append(chunk)
            markdownState.value = autoCloseMarkdown(buffer.toString())
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val textView = PrinterMarkDownTextView(ctx)
            val parser = MarkdownParserFactory.create(ctx, textView)
            textView.tag = parser
            textView
        },
        update = { view ->
            val parser = view.tag as? MarkdownParser
            if (parser != null && view is TextView) {
                // 1. Update Markdown content
                parser.markwon.setMarkdown(view, markdownState.value)

                // 2. Force correct color (prevents gray text in dark mode)
                view.setTextColor(textColor)

                // 3. Apply font settings
                if (fontSize > 0) {
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
                }
                view.typeface = when (fontFamily) {
                    "sans-serif" -> Typeface.SANS_SERIF
                    "serif" -> Typeface.SERIF
                    "monospace" -> Typeface.MONOSPACE
                    else -> Typeface.DEFAULT
                }

                // 4. Force tight line spacing and remove extra font padding
                // This overrides the giant gaps caused by the default MarkdownStyles
                view.setLineSpacing(0f, 1.15f)
                view.includeFontPadding = false
            }
        }
    )
}

private fun autoCloseMarkdown(text: String): String {
    var inBold = false
    var inItalic = false
    var inCode = false
    var inStrike = false
    var i = 0

    while (i < text.length) {
        when {
            text.startsWith("**", i) -> { inBold = !inBold; i += 2 }
            text.startsWith("__", i) -> { inBold = !inBold; i += 2 }
            text.startsWith("~~", i) -> { inStrike = !inStrike; i += 2 }
            text.startsWith("`", i) -> { inCode = !inCode; i += 1 }
            text[i] == '*' && !inCode -> { inItalic = !inItalic; i += 1 }
            text[i] == '_' && !inCode -> { inItalic = !inItalic; i += 1 }
            else -> i += 1
        }
    }

    val sb = StringBuilder(text)
    if (inBold) sb.append("**")
    if (inItalic) sb.append("*")
    if (inCode) sb.append("`")
    if (inStrike) sb.append("~~")
    return sb.toString()
}
