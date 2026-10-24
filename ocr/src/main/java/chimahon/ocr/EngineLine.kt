package chimahon.ocr

import kotlin.math.abs

data class NormalizedBBox(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val rotation: Double = 0.0,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
}

enum class WritingDirection { LTR, TTB, RTL }

/**
 * A single character recognized by an OCR engine, with its bbox.
 * Coordinates are normalized 0.0–1.0 relative to the original image.
 */
data class EngineChar(
    val char: Char,
    val bbox: NormalizedBBox,
    val conf: Float,
)

/**
 * Shared intermediate representation between any OCR engine and the merger.
 * All coordinates are normalized 0.0–1.0 and axis-aligned.
 */
data class EngineLine(
    val text: String,
    val bbox: NormalizedBBox,
    val writingDirection: WritingDirection?,
    val characterSize: Double,
    val hasJpText: Boolean,
    val hasKanji: Boolean,
    val rotation: Double = 0.0,
    val chars: List<EngineChar>? = null,
)

private val CJ_REGEX = Regex("[\u3041-\u3096\u30A1-\u30FA\u4E01-\u9FFF]")
private val KANJI_REGEX_ENGINE = Regex("[\u4E00-\u9FFF]")

fun EngineLine(
    text: String,
    bbox: NormalizedBBox,
    writingDirection: WritingDirection?,
    language: OcrLanguage,
    chars: List<EngineChar>? = null,
): EngineLine {
    // When per-character boxes are supplied and aligned with `text`, skip
    // text cleaning so chars stay indexable against `text`. OcrPreprocessor.clean
    // can insert spaces after punctuation and collapse runs — either would
    // desync `chars` from `text` and silently force every consumer onto the
    // weighted-offset fallback. Engines without char boxes still get the
    // cleaned text (unchanged behavior).
    val charsAligned = chars != null && chars.size == text.length
    val finalText = if (charsAligned) text else OcrPreprocessor.clean(text)
    val normalizedText = CJ_REGEX.findAll(finalText).map { it.value }.joinToString("")
    val hasJpText = normalizedText.isNotEmpty()
    val hasKanji = hasJpText && KANJI_REGEX_ENGINE.containsMatchIn(normalizedText)
    val charCount = finalText.length.coerceAtLeast(1)
    val dimension = if (writingDirection == WritingDirection.TTB) bbox.height else bbox.width
    val characterSize = dimension / charCount

    return EngineLine(
        text = finalText,
        bbox = bbox,
        writingDirection = writingDirection,
        characterSize = characterSize,
        hasJpText = hasJpText,
        hasKanji = hasKanji,
        rotation = bbox.rotation,
        chars = if (charsAligned) chars else null,
    )
}
