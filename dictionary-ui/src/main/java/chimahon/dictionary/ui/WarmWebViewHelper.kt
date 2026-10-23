package chimahon.dictionary.ui

import android.content.Context
import android.webkit.WebView

/**
 * Public accessor for the internal prepareDictionaryWebViewShell.
 * :app needs to build a warmed dictionary WebView for the screen lookup
 * overlay and the ProcessText receiver; those live in :app, not
 * :dictionary-ui, so a public bridge is required.
 */
fun createWarmWebView(context: Context, languageCode: String = ""): WebView {
    return prepareDictionaryWebViewShell(context, languageCode = languageCode)
}
