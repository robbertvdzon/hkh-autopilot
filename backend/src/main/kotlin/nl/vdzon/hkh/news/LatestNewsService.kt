package nl.vdzon.hkh.news

import nl.vdzon.hkh.news.entity.MatchedNewsEntity
import nl.vdzon.hkh.news.entity.NewsEntityMatcher
import nl.vdzon.hkh.news.entity.NewsEntityType
import nl.vdzon.hkh.news.entity.NewsGazetteer
import org.springframework.stereotype.Service

data class NewsSearchItem(val news: LatestNews, val entities: List<MatchedNewsEntity>)

data class AggregatedNewsEntity(val type: NewsEntityType, val canonicalLabel: String, val itemCount: Int)

data class NewsSearchResult(
    val items: List<NewsSearchItem>,
    val total: Int,
    val entities: List<AggregatedNewsEntity>,
)

@Service
class LatestNewsService(private val store: LatestNewsStore, private val gazetteer: NewsGazetteer) {
    fun findAll(): List<LatestNews> = store.findAll()

    fun create(title: String, message: String, createdBy: String): LatestNews = store.create(
        title = title.trim(),
        message = message.trim(),
        createdBy = createdBy,
    )

    fun search(q: String?, entity: String?): NewsSearchResult {
        val normalizedQuery = q?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedEntity = entity?.trim()?.takeIf { it.isNotEmpty() }

        val annotated = store.findAll().map { news -> news to NewsEntityMatcher.match(gazetteer, news.title, news.message) }

        val filtered = annotated.filter { (news, entities) ->
            val matchesQuery = normalizedQuery == null ||
                news.title.contains(normalizedQuery, ignoreCase = true) ||
                news.message.contains(normalizedQuery, ignoreCase = true)
            val matchesEntity = normalizedEntity == null ||
                entities.any { it.canonicalLabel.equals(normalizedEntity, ignoreCase = true) }
            matchesQuery && matchesEntity
        }

        val items = filtered.map { (news, entities) -> NewsSearchItem(news, entities) }
        return NewsSearchResult(
            items = items,
            total = items.size,
            entities = aggregate(annotated.map { it.second }),
        )
    }

    private fun aggregate(entitiesPerItem: List<List<MatchedNewsEntity>>): List<AggregatedNewsEntity> {
        val counts = linkedMapOf<Pair<NewsEntityType, String>, Int>()
        entitiesPerItem.forEach { entities ->
            entities.forEach { entity ->
                val key = entity.type to entity.canonicalLabel
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts.entries
            .map { (key, count) -> AggregatedNewsEntity(key.first, key.second, count) }
            .sortedWith(compareBy({ it.type.ordinal }, { it.canonicalLabel }))
    }
}
