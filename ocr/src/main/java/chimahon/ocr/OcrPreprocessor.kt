package chimahon.ocr

/**
 * Port of Mangatan's cleanOcrText replacement engine.
 * Standardizes punctuation sequences without touching whitespace or newlines.
 */
internal object OcrPreprocessor {

    private val COMPLEX_PUNCTUATION_REGEX = Regex("[!?\\.\\u2026\\u22EE\\uff0e\\uff1f\\uff01\\uff65]{2,}")
    private val PUNCTUATION_SPACE_REGEX = Regex("([。、！？!?…」』])(?=[^\\s。、！？!?…」』])")
    private const val KATAKANA_MIDDLE_DOTS = "･･･"

    /**
     * Collapses 2+ sequences of punctuation into a single standard character.
     * Priority: Ellipsis > Question Mark > Exclamation Mark.
     */
    fun clean(text: String?): String {
        if (text.isNullOrBlank()) return text ?: ""

        // 1. Replace 2+ sequences of punctuation
        val flattened = text.replace(COMPLEX_PUNCTUATION_REGEX) { matchResult ->
            val match = matchResult.value
            val isEllipsis = match.contains('.') ||
                match.contains('\u2026') ||
                match.contains('\u22EE') ||
                match.contains('\uff0e') ||
                match.contains('\uff65')

            if (isEllipsis) {
                "…"
            } else if (match.contains('?') || match.contains('\uff1f')) {
                "?"
            } else {
                "!"
            }
        }

        // 2. Replace the specific katakana triple dot sequence
        val spaced = flattened.replace(KATAKANA_MIDDLE_DOTS, "…")
        
        // 3. Ensure a space after punctuation if it's missing (e.g. "？草薙" -> "？ 草薙")
        return spaced.replace(PUNCTUATION_SPACE_REGEX, "$1 ")
    }
}
