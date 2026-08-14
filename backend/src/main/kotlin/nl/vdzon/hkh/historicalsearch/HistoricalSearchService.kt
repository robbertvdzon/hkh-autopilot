package nl.vdzon.hkh.historicalsearch

import org.springframework.stereotype.Service

data class HistoricalSearchOutcome(
    val results: List<HistoricalSearchResult>,
    val total: Int,
    val start: Int,
    val limit: Int,
    val sources: List<HistoricalSourceStatus>,
    val state: HistoricalSearchState,
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
            val firstPage = try {
                adapter?.search(query.copy(start = 0, limit = 100))
            } catch (exception: HistoricalSearchRequestBudgetExceededException) {
                throw exception
            } ?: HistoricalSearchPage(
                    source = source,
                    results = emptyList(),
                    total = 0,
                    status = if (adapter == null) {
                        HistoricalTechnicalStatus.DISABLED
                    } else {
                        HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE
                    },
                    message = null,
                )
            HistoricalSearchCursor(adapter, query, firstPage)
        }
        val initialPages = cursors.map { it.initialPage }
        val merged = merge(cursors, query.start, query.limit)
        val sourceStatuses = cursors.map { it.status() }
        val availableSources = sourceStatuses.filter { it.status == HistoricalTechnicalStatus.AVAILABLE }
        val total = cursors.sumOf { it.totalContribution() }
        val results = if (availableSources.isEmpty()) {
            emptyList()
        } else {
            merged.results.take((total - merged.start).coerceAtLeast(0))
        }
        val sources = sourceStatuses.map { status ->
            if (status.status != HistoricalTechnicalStatus.AVAILABLE) {
                status
            } else {
                val sourceResults = results.filter {
                    it.source == status.source &&
                        it.technicalStatus == HistoricalTechnicalStatus.AVAILABLE
                }
                status.copy(
                    resultCount = sourceResults.size,
                    heemskerkCount = sourceResults.count { it.isHeemskerkPlaceIndicator() },
                )
            }
        }
        val state = when {
            availableSources.isEmpty() -> HistoricalSearchState.SOURCE_FAILURE
            sources.any { it.status != HistoricalTechnicalStatus.AVAILABLE } ->
                HistoricalSearchState.PARTIAL_AVAILABILITY
            initialPages.sumOf { it.total.coerceAtLeast(0) } == 0 && results.isEmpty() ->
                HistoricalSearchState.NO_RESULTS
            else -> HistoricalSearchState.RESULTS
        }
        return HistoricalSearchOutcome(
            results = results,
            total = total,
            start = merged.start,
            limit = query.limit,
            sources = sources,
            state = state,
        )
    }

    private data class MergeResult(
        val results: List<HistoricalSearchResult>,
        val start: Int,
    )

    private fun merge(
        cursors: List<HistoricalSearchCursor>,
        start: Int,
        limit: Int,
    ): MergeResult {
        var effectiveStart = start

        while (true) {
            cursors.filter { it.isAvailable() }.forEach(HistoricalSearchCursor::resetReadPosition)
            val active = cursors.filter { it.isAvailable() }.toMutableList()
            val results = mutableListOf<HistoricalSearchResult>()
            var sourceIndex = 0
            var globalPosition = 0
            var sourceFailed = false

            val endExclusive = effectiveStart + limit
            while (globalPosition < endExclusive && active.isNotEmpty()) {
                if (sourceIndex >= active.size) sourceIndex = 0
                val cursor = active[sourceIndex]
                val result = cursor.next()
                if (result == null) {
                    if (!cursor.isAvailable()) {
                        // The failed source no longer contributes to the merged stream. Rebase
                        // the requested offset over the normalized records already read from it,
                        // so the next available source page remains reachable.
                        effectiveStart =
                            (effectiveStart - cursor.fetchedResultCount()).coerceAtLeast(0)
                        sourceFailed = true
                        break
                    }
                    active.removeAt(sourceIndex)
                    continue
                }
                if (globalPosition >= effectiveStart) results += result
                globalPosition++
                sourceIndex++
            }

            if (!sourceFailed) return MergeResult(results, effectiveStart)
            if (cursors.none { it.isAvailable() }) return MergeResult(emptyList(), effectiveStart)
        }
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

    fun isAvailable(): Boolean = currentStatus == HistoricalTechnicalStatus.AVAILABLE

    fun resetReadPosition() {
        readPosition = 0
    }

    fun fetchedResultCount(): Int = bufferedResults.size

    private var readPosition = 0

    fun status(): HistoricalSourceStatus = HistoricalSourceStatus(
        source = initialPage.source,
        status = currentStatus,
        message = HistoricalSourceMessages.safe(currentStatus, currentMessage),
    )

    fun totalContribution(): Int = if (currentStatus == HistoricalTechnicalStatus.AVAILABLE) {
        initialPage.total.coerceAtLeast(0)
    } else {
        0
    }

    fun next(): HistoricalSearchResult? {
        while (readPosition >= bufferedResults.size && !exhausted) {
            if (nextSourceStart >= initialPage.total.coerceAtLeast(0)) {
                exhausted = true
                break
            }
            val nextPage = try {
                adapter?.search(query.copy(start = nextSourceStart, limit = 100))
            } catch (exception: HistoricalSearchRequestBudgetExceededException) {
                throw exception
            }
            if (nextPage == null || nextPage.status != HistoricalTechnicalStatus.AVAILABLE) {
                currentStatus = nextPage?.status ?: HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE
                currentMessage = nextPage?.message
                exhausted = true
                break
            }
            val consumed = nextPage.consumed.coerceAtLeast(0)
            nextSourceStart += consumed
            bufferedResults.addAll(nextPage.results)
            exhausted = consumed == 0 || consumed < 100
        }
        return bufferedResults.getOrNull(readPosition++)
    }
}
