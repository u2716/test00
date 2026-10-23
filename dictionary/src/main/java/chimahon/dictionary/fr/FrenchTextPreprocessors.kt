package chimahon.dictionary.fr

object FrenchTextPreprocessors {

    private val elisionClitics = setOf("l", "d", "j", "m", "t", "s", "n", "c", "qu")

    fun apostropheVariants(text: String): List<String> = listOf(
        text,
        text.replace('\'', '\u2019'),
        text.replace('\u2019', '\''),
    )

    fun decapitalize(text: String): List<String> {
        val lower = text.lowercase()
        return if (lower != text) listOf(text, lower) else listOf(text)
    }

    fun allVariants(text: String): List<String> {
        return buildList {
            for (variant in decapitalize(text).flatMap { apostropheVariants(it) }) {
                add(variant)
                stripLeadingElision(variant)?.let { add(it) }
            }
        }.distinct()
    }

    private fun stripLeadingElision(text: String): String? {
        if (text.length < 2) return null
        val clitic = elisionClitics.firstOrNull {
            it.length < text.length &&
                text.regionMatches(0, it, 0, it.length, ignoreCase = true)
        } ?: return null
        val rest = text.substring(clitic.length)
        val apostrophe = rest.firstOrNull() ?: return null
        if (apostrophe != '\'' && apostrophe != '\u2019') return null
        return rest.substring(1).takeIf { it.isNotEmpty() }
    }
}
