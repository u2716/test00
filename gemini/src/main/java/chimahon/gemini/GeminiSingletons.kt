package chimahon.gemini

/**
 * Process-wide singleton for :gemini. :app sets it once in App.onCreate()
 * before any composable that calls rememberGeminiAnalysisState().
 */
object GeminiSingletons {
    @Volatile
    var geminiService: GeminiService? = null
}
