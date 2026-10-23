package chimahon.gemini

/**
 * Settings that :gemini needs from the host app.
 *
 * Plain-value accessors — the reference version read the app preference
 * layer directly, which forced :gemini to compile against it. :app
 * supplies an implementation that reads from the shared preference store.
 */
interface GeminiPreferences {
    val geminiApiKey: String
    val geminiModel: String
    val geminiCustomEndpoint: String
    val geminiSendScreenshot: Boolean
    val geminiPrompt: String
    val geminiFontSize: Int
    val geminiFontFamily: String
}
