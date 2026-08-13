package nl.vdzon.hkh.historicalsearch

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

enum class HistoricalContextStatus {
    AVAILABLE,
    MISSING,
    UNCERTAIN,
    UNAVAILABLE,
}

data class HistoricalContextField(
    val value: String?,
    val status: HistoricalContextStatus,
)

data class HistoricalSharedContext(
    val field: String,
    val value: String,
)

data class HistoricalSearchRelation(
    val source: HistoricalSearchSource,
    val sourceRecordId: String,
    val stableUrl: String,
    val sharedFields: List<HistoricalSharedContext>,
    val periodOverlaps: Boolean = false,
)

/**
 * Resolves relations without broadening the visible result set. Only explicit,
 * certain context fields participate; dates can annotate an existing relation,
 * but can never create one.
 */
object HistoricalSearchRelations {
    fun find(
        opened: HistoricalSearchResult,
        visibleResults: List<HistoricalSearchResult>,
        maximum: Int = 3,
    ): List<HistoricalSearchRelation> = visibleResults.asSequence()
        .filterNot { it.sameRecordAs(opened) }
        .mapNotNull { candidate -> relation(opened, candidate) }
        .take(maximum.coerceAtLeast(0))
        .toList()

    private fun relation(
        opened: HistoricalSearchResult,
        candidate: HistoricalSearchResult,
    ): HistoricalSearchRelation? {
        val shared = listOfNotNull(
            shared("Plaats", opened.place, opened.placeStatus, candidate.place, candidate.placeStatus),
            shared("Persoon", opened.person, opened.personStatus, candidate.person, candidate.personStatus),
            shared("Gebeurtenis", opened.event, opened.eventStatus, candidate.event, candidate.eventStatus),
        )
        if (shared.isEmpty()) return null
        return HistoricalSearchRelation(
            source = candidate.source,
            sourceRecordId = candidate.sourceRecordId,
            stableUrl = candidate.stableUrl,
            sharedFields = shared,
            periodOverlaps = periodsOverlap(opened, candidate),
        )
    }

    private fun shared(
        field: String,
        left: String?,
        leftStatus: HistoricalContextStatus,
        right: String?,
        rightStatus: HistoricalContextStatus,
    ): HistoricalSharedContext? {
        if (leftStatus != HistoricalContextStatus.AVAILABLE || rightStatus != HistoricalContextStatus.AVAILABLE) return null
        val normalizedLeft = normalize(left) ?: return null
        val normalizedRight = normalize(right) ?: return null
        return normalizedLeft.takeIf { it == normalizedRight }?.let {
            HistoricalSharedContext(field, left!!.trim().replace(Regex("\\s+"), " "))
        }
    }

    private fun normalize(value: String?): String? = value?.let {
        Normalizer.normalize(it.trim(), Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf(String::isNotEmpty)
            ?.lowercase(Locale.ROOT)
    }

    private fun HistoricalSearchResult.sameRecordAs(other: HistoricalSearchResult): Boolean =
        (source == other.source && sourceRecordId == other.sourceRecordId) || stableUrl == other.stableUrl

    private fun periodsOverlap(left: HistoricalSearchResult, right: HistoricalSearchResult): Boolean {
        val leftRange = dateRange(left) ?: return false
        val rightRange = dateRange(right) ?: return false
        return !leftRange.first.isAfter(rightRange.second) && !rightRange.first.isAfter(leftRange.second)
    }

    private fun dateRange(result: HistoricalSearchResult): Pair<LocalDate, LocalDate>? {
        val start = result.dateStart?.let(::parseDate) ?: return null
        val end = result.dateEnd?.let(::parseDate) ?: start
        return if (start <= end) start to end else null
    }

    private fun parseDate(value: String): LocalDate? = runCatching {
        when {
            Regex("\\d{4}").matches(value) -> LocalDate.of(value.toInt(), 1, 1)
            Regex("\\d{4}-\\d{2}-\\d{2}").matches(value) -> LocalDate.parse(value)
            else -> null
        }
    }.getOrNull()
}
