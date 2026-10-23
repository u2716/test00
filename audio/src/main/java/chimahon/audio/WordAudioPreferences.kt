package chimahon.audio

/**
 * Settings that :audio needs from the host app.
 *
 * The reference version exposed the app-level preference layer directly,
 * which forced :audio to compile against it. Plain value accessors keep
 * :audio standalone: the app supplies an implementation backed by
 * SharedPreferences (or anything else), and readers inside this module
 * just see a Boolean or a String.
 *
 * The app should cache the underlying preference objects once and read
 * through them inside the getters — allocating a wrapper on every
 * word-audio lookup would be wasteful.
 */
interface WordAudioPreferences {
    val enabled: Boolean
    val autoplay: Boolean
    val sourcesJson: String
    var localPath: String
    var localUri: String
    val localEnabled: Boolean
}
