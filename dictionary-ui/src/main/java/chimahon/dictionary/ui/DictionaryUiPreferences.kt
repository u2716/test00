package chimahon.dictionary.ui

/**
 * The few app-level theme settings the dictionary popup cares about.
 *
 * The reference implementation read these straight off UiPreferences.
 * :dictionary-ui cannot depend on :app, so :app supplies an implementation.
 * Values are plain data (no flows) because the popup only reads them at
 * render time; recomposition is driven by the popup's own state.
 */
interface DictionaryUiPreferences {
    /** "system" / "dark" / "light" — lowercased to match resolveDictionaryTheme's expectations. */
    val themeMode: String

    val appAmoled: Boolean

    /** Seed color used when no per-dictionary custom color is set. */
    val appColorTheme: Int
}
