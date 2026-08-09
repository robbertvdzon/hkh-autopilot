package nl.vdzon.hkh.news.entity

import java.text.Normalizer
import java.util.regex.Pattern

data class MatchedNewsEntity(val type: NewsEntityType, val canonicalLabel: String)

/**
 * Deterministic, case-insensitive, diacritics-normalized whole-word gazetteer matcher.
 * No NLP/NER fallback: text without a gazetteer hit simply yields an empty list.
 */
object NewsEntityMatcher {
    fun match(gazetteer: NewsGazetteer, vararg texts: String): List<MatchedNewsEntity> {
        val normalizedText = normalize(texts.joinToString(" "))
        val ordered = mutableListOf<Triple<NewsEntityType, String, Int>>()

        NewsEntityType.entries.forEach { type ->
            gazetteer.entriesByType[type].orEmpty().forEach { entry ->
                val firstIndex = earliestMatchIndex(entry, normalizedText)
                if (firstIndex >= 0) {
                    ordered += Triple(type, entry.canonicalLabel, firstIndex)
                }
            }
        }

        return ordered
            .sortedWith(compareBy({ it.first.ordinal }, { it.third }))
            .map { MatchedNewsEntity(it.first, it.second) }
    }

    private fun earliestMatchIndex(entry: GazetteerEntry, normalizedText: String): Int {
        var earliest = -1
        entry.aliases.forEach { alias ->
            val normalizedAlias = normalize(alias)
            if (normalizedAlias.isBlank()) return@forEach
            val matcher = Pattern.compile("\\b" + Pattern.quote(normalizedAlias) + "\\b").matcher(normalizedText)
            if (matcher.find()) {
                val index = matcher.start()
                if (earliest == -1 || index < earliest) earliest = index
            }
        }
        return earliest
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return DIACRITIC_MARKS.matcher(decomposed).replaceAll("").lowercase()
    }

    private val DIACRITIC_MARKS: Pattern = Pattern.compile("\\p{Mn}+")
}
