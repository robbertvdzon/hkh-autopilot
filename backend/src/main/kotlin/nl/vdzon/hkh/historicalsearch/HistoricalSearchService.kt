package nl.vdzon.hkh.historicalsearch

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
        val cursors = selected.map { source ->
            val adapter = bySource[source]
            val firstPage = adapter?.search(query.copy(start = 0, limit = 100))
                ?: HistoricalSearchPage(
                    source = source,
                    results = emptyList(),
                    total = 0,
                    status = HistoricalTechnicalStatus.DISABLED,
                    message = "Bronadapter is niet beschikbaar.",
                )
            HistoricalSearchCursor(adapter, query, firstPage)
        }
        val initialPages = cursors.map { it.initialPage }
        val results = merge(cursors, query.start, query.limit)
        return HistoricalSearchOutcome(
            results = results,
            total = initialPages.sumOf { it.total },
            start = query.start,
            limit = query.limit,
            sources = cursors.map { it.status() },
        )
    }

    private fun merge(
        cursors: List<HistoricalSearchCursor>,
        start: Int,
        limit: Int,
    ): List<HistoricalSearchResult> {
        val active = cursors.filter { it.initialPage.status == HistoricalTechnicalStatus.AVAILABLE }.toMutableList()
        val results = mutableListOf<HistoricalSearchResult>()
        var sourceIndex = 0
        var globalPosition = 0
        val endExclusive = start + limit

        while (globalPosition < endExclusive && active.isNotEmpty()) {
            if (sourceIndex >= active.size) sourceIndex = 0
            val cursor = active[sourceIndex]
            val result = cursor.next()
            if (result == null) {
                active.removeAt(sourceIndex)
                continue
            }
            if (globalPosition >= start) results += result
            globalPosition++
            sourceIndex++
        }

        return results
    }
}

private class HistoricalSearchCursor(
    private val adapter: HistoricalSearchAdapter?,
    private val query: HistoricalSearchQuery,
    val initialPage: HistoricalSearchPage,
) {
    private val bufferedResults = ArrayDeque<HistoricalSearchResult>(initialPage.results)
    private var nextSourceStart = initialPage.consumed.coerceAtLeast(0)
    private var exhausted = initialPage.status != HistoricalTechnicalStatus.AVAILABLE || initialPage.consumed < 100
    private var currentStatus = initialPage.status
    private var currentMessage = initialPage.message

    fun status(): HistoricalSourceStatus = HistoricalSourceStatus(
        source = initialPage.source,
        status = currentStatus,
        message = currentMessage,
    )

    fun next(): HistoricalSearchResult? {
        while (bufferedResults.isEmpty() && !exhausted) {
            val nextPage = adapter?.search(query.copy(start = nextSourceStart, limit = 100))
            if (nextPage == null || nextPage.status != HistoricalTechnicalStatus.AVAILABLE) {
                currentStatus = nextPage?.status ?: HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE
                currentMessage = nextPage?.message ?: "Bronadapter is niet beschikbaar."
                exhausted = true
                break
            }
            val consumed = nextPage.consumed.coerceAtLeast(0)
            nextSourceStart += consumed
            bufferedResults.addAll(nextPage.results)
            exhausted = consumed == 0 || consumed < 100
        }
        return bufferedResults.removeFirstOrNull()
    }
}
