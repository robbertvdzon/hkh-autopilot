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
        val initialPages = fetchShardedPages(selected, query, bySource)
        val activeSources = initialPages.mapNotNull { shardedPage ->
            shardedPage.page.takeIf {
                it.status == HistoricalTechnicalStatus.AVAILABLE && it.total > 0
            }?.source
        }
        val pagesForResults = if (activeSources.isNotEmpty() && activeSources.size < selected.size) {
            fetchShardedPages(activeSources, query, bySource)
        } else {
            initialPages
        }
        return HistoricalSearchOutcome(
            results = pagesForResults.flatMap { shardedPage ->
                shardedPage.page.results.mapIndexedNotNull { resultIndex, result ->
                    val globalPosition =
                        (shardedPage.sourceStart + resultIndex) * pagesForResults.size + shardedPage.sourceIndex
                    result.takeIf { globalPosition in query.start until query.start + query.limit }
                        ?.let { globalPosition to it }
                }
            }.sortedBy { it.first }.map { it.second }.take(query.limit),
            total = initialPages.sumOf { it.page.total },
            start = query.start,
            limit = query.limit,
            sources = initialPages.map { HistoricalSourceStatus(it.page.source, it.page.status, it.page.message) },
        )
    }

    private fun fetchShardedPages(
        selected: List<HistoricalSearchSource>,
        query: HistoricalSearchQuery,
        bySource: Map<HistoricalSearchSource, HistoricalSearchAdapter>,
    ): List<HistoricalShardedPage> {
        val sourceCount = selected.size.coerceAtLeast(1)
        val pageEndExclusive = query.start + query.limit
        return selected.mapIndexed { sourceIndex, source ->
            val offsetToSource = (sourceIndex - (query.start % sourceCount) + sourceCount) % sourceCount
            val firstGlobalPosition = query.start + offsetToSource
            val shardLimit = if (firstGlobalPosition >= pageEndExclusive) {
                0
            } else {
                ((pageEndExclusive - 1 - firstGlobalPosition) / sourceCount) + 1
            }
            val sourceQuery = query.copy(
                start = firstGlobalPosition / sourceCount,
                limit = shardLimit.coerceAtLeast(1).coerceAtMost(100),
            )
            val page = bySource[source]?.search(sourceQuery) ?: HistoricalSearchPage(
                source, emptyList(), 0, HistoricalTechnicalStatus.DISABLED, "Bronadapter is niet beschikbaar.",
            )
            HistoricalShardedPage(page, sourceIndex, sourceQuery.start)
        }
    }
}

private data class HistoricalShardedPage(
    val page: HistoricalSearchPage,
    val sourceIndex: Int,
    val sourceStart: Int,
)
