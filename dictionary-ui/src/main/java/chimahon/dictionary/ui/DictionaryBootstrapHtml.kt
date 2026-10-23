package chimahon.dictionary.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

internal fun getDictionaryBootstrapHtml(
    context: Context,
    colorScheme: ColorScheme = androidx.compose.material3.lightColorScheme(),
    isDark: Boolean = false,
    isAmoled: Boolean = false,
    seedColor: Int = 0,
    fontFamily: String = "",
    eInkMode: Boolean = false,
    paginatedScrolling: Boolean = false,
    paginatedScrollStepSize: Int = 90,
    scrollBehavior: String = "smooth",
    languageCode: String = "",
    compactGlossary: Boolean = false,
): String {
    val css = dictionaryBaseCss.getOrPut(Unit) {
        readTextAsset(context.applicationContext, "dictionary/base.css")
    }
    val js = dictionaryRendererJs.getOrPut(Unit) {
        readTextAsset(context.applicationContext, "dictionary/renderer.js").replace("</script", "<\\/script")
    }
    val kanjiJs = dictionaryKanjiRendererJs.getOrPut(Unit) {
        readTextAsset(context.applicationContext, "dictionary/kanji-renderer.js")
    }

    val accentHex = "#%06X".format(0xFFFFFF and colorScheme.primary.toArgb())
    val onAccentHex = "#%06X".format(0xFFFFFF and colorScheme.onPrimary.toArgb())
    val fgHex = "#%06X".format(0xFFFFFF and colorScheme.onSurface.toArgb())
    val bgHex = if (isAmoled && isDark) "#000000" else "#%06X".format(0xFFFFFF and colorScheme.surface.toArgb())
    val secondaryHex = "#%06X".format(0xFFFFFF and colorScheme.onSurfaceVariant.toArgb())
    val borderHex = if (isAmoled && isDark) "rgba(255,255,255,0.10)" else "#%06X".format(0xFFFFFF and colorScheme.outlineVariant.toArgb())
    val hoverHex = if (isAmoled && isDark) "rgba(255,255,255,0.07)" else "#%06X".format(0xFFFFFF and colorScheme.surfaceVariant.toArgb())
    val tabBgHex = if (isAmoled && isDark) "#0d0d0d" else "#%06X".format(0xFFFFFF and colorScheme.surfaceContainer.toArgb())

    val fontFaceCss = if (fontFamily.isNotBlank()) {
        """
          :root, body, #entries {
            font-family: '$fontFamily' !important;
          }
        """.trimIndent()
    } else ""

    val dynamicThemeCss = """
        <style id="dynamic-theme">
            :root, :root[data-theme="dark"], :root[data-theme="light"] {
                --accent: $accentHex;
                --on-accent: $onAccentHex;
                --fg: $fgHex;
                --bg: $bgHex;
                --secondary: $secondaryHex;
                --border: $borderHex;
                --hover-bg: $hoverHex;
                --tab-bg: $tabBgHex;
                --pronunciation-annotation-color: $fgHex;
            }
        </style>
    """.trimIndent()

    val eInkAttr = if (eInkMode) "true" else "false"
    val paginatedScrollingAttr = if (paginatedScrolling) "true" else "false"
    val paginatedStepAttr = if (paginatedScrolling) paginatedScrollStepSize.toString() else "90"
    val themeAttr = if (isDark) "dark" else "light"
    val langAttr = if (languageCode.isNotEmpty()) """lang="$languageCode" """ else ""
    val compactGlossaryAttr = if (compactGlossary) "true" else "false"
    val glossaryLayoutModeAttr = if (compactGlossary) "compact" else "default"

    return """
        <!doctype html>
        <html $langAttr data-theme="$themeAttr" data-chima-eink-mode="$eInkAttr" data-chima-paginated-scrolling="$paginatedScrollingAttr" data-chima-paginated-step="$paginatedStepAttr" data-chima-scroll-behavior="$scrollBehavior" data-compact-glossary="$compactGlossaryAttr" data-glossary-layout-mode="$glossaryLayoutModeAttr">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
          <style>$css</style><style>$fontFaceCss</style>$dynamicThemeCss
          <style id="dictionary-styles"></style>
          <style id="chima-custom-css"></style>
          <script>
            window.AnkiBridge = {
              addToAnki: function(index, glossary, selectedDict, popupSelection) {
                if (typeof AnkiJsBridge !== 'undefined') {
                  AnkiJsBridge.addToAnki(
                    String(index),
                    String(glossary != null ? glossary : '-1'),
                    selectedDict || '',
                    popupSelection || ''
                  );
                }
              },
              openInAnki: function(index, glossary, selectedDict, popupSelection) {
                if (typeof AnkiJsBridge !== 'undefined') {
                  AnkiJsBridge.openInAnki(
                    String(index),
                    String(glossary != null ? glossary : '-1'),
                    selectedDict || '',
                    popupSelection || ''
                  );
                }
              }
            };
          </script>
        </head>
        <body>
          <main id="entries" class="entries"></main>
          <script>$js</script>
          <script>$kanjiJs</script>
        </body>
        </html>
    """.trimIndent()
}

private fun readTextAsset(context: Context, assetPath: String): String {
    return context.assets.open(assetPath).use { input ->
        input.readBytes().toString(Charsets.UTF_8)
    }
}

private val dictionaryBaseCss = java.util.concurrent.ConcurrentHashMap<Unit, String>()
private val dictionaryRendererJs = java.util.concurrent.ConcurrentHashMap<Unit, String>()
private val dictionaryKanjiRendererJs = java.util.concurrent.ConcurrentHashMap<Unit, String>()

fun getDictionaryColorScheme(
    isDark: Boolean,
    isAmoled: Boolean,
    seedColor: Int = 0,
): ColorScheme {
    return when {
        isDark && isAmoled -> darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
        )
        isDark -> darkColorScheme()
        else -> lightColorScheme()
    }
}

fun getAppThemeColorScheme(
    context: Context,
    isDark: Boolean,
    isAmoled: Boolean,
): ColorScheme {
    return getDictionaryColorScheme(isDark, isAmoled)
}

data class DictionaryThemeResolved(
    val isDark: Boolean,
    val isAmoled: Boolean,
    val seedColor: Int,
    val useAppTheme: Boolean,
)

fun resolveDictionaryTheme(
    themeMode: String,
    customColor: Int,
    eInkMode: Boolean,
    forceDefaultTheme: Boolean,
    systemIsDark: Boolean,
    appThemeMode: String,
    appAmoled: Boolean,
    appColorTheme: Int,
): DictionaryThemeResolved {
    val useAppTheme = false  // app theme integration deferred
    val hasCustomColor = customColor != 0 && !forceDefaultTheme && !useAppTheme
    val seedColor = if (hasCustomColor) customColor else appColorTheme
    val appIsDark = when (appThemeMode) {
        "dark" -> true
        "light" -> false
        else -> systemIsDark
    }
    val isAmoled = when {
        useAppTheme -> appAmoled
        themeMode == "pure_black" -> true
        else -> false
    }
    val isDark = when {
        useAppTheme -> appIsDark
        themeMode == "dark" || themeMode == "pure_black" -> true
        themeMode == "light" -> false
        hasCustomColor -> Color(seedColor).luminance() < 0.5f
        else -> systemIsDark
    }
    return DictionaryThemeResolved(
        isDark = isDark,
        isAmoled = isAmoled,
        seedColor = seedColor,
        useAppTheme = useAppTheme,
    )
}

internal fun stopDictionaryAudio(webView: android.webkit.WebView) {
    (webView.tag as? DictionaryWebViewState)?.wordAudioBridge?.stopAudio()
}
