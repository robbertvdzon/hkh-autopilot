package nl.vdzon.hkh.historicalsearch

import java.time.Instant
import org.springframework.stereotype.Service

data class HistoricalSearchOutcome(
    val results: List<HistoricalSearchResult>,
    val total: Int,
    val start: Int,
    val limit: Int,
    val sources: List<HistoricalSourceStatus>,
)

@Service
class HistoricalSearchService(
    private val adapters: List<HistoricalSearchAdapter>,
) {
    fun search(query: HistoricalSearchQuery): HistoricalSearchOutcome {
        val selected = query.source?.let { listOf(it) } ?: HistoricalSearchSource.entries.toList()
        val bySource = adapters.associateBy(HistoricalSearchAdapter::source)
        val pages = selected.map { source ->
            bySource[source]?.search(query) ?: HistoricalSearchPage(
                source, emptyList(), 0, HistoricalTechnicalStatus.DISABLED, "Bronadapter is niet beschikbaar.",
            )
        }
        return HistoricalSearchOutcome(
            results = pages.flatMap(HistoricalSearchPage::results),
            total = pages.sumOf(HistoricalSearchPage::total),
            start = query.start,
            limit = query.limit,
            sources = pages.map { HistoricalSourceStatus(it.source, it.status, it.message) },
        )
    }
}
