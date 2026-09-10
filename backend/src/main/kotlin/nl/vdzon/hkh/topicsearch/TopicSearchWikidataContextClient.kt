package nl.vdzon.hkh.topicsearch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.client.RestClient

fun interface TopicSearchWikidataContextSource {
    /**
     * Haalt Wikidata-contextinformatie op voor [term]. Draagt nooit zelfstandig een bewering over
     * Heemskerk. Fail-closed: elke fout, of nul dan wel meer dan één `wbsearchentities`-kandidaat,
     * levert `null` op en blokkeert nooit het Europeana-antwoord.
     */
    fun fetchContext(term: String): TopicSearchContext?
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class WbSearchEntitiesResponse(@param:JsonProperty("search") val search: List<WbSearchEntity>? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class WbSearchEntity(@param:JsonProperty("id") val id: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EntityDataResponse(@param:JsonProperty("entities") val entities: Map<String, EntityData>? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EntityData(
    @param:JsonProperty("labels") val labels: Map<String, LabelValue>? = null,
    @param:JsonProperty("descriptions") val descriptions: Map<String, LabelValue>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class LabelValue(@param:JsonProperty("value") val value: String? = null)

/**
 * Zoekt op Wikidata naar [term], naar het bestaande fail-closed patroon
 * (`PersonSearchWikidataContextClient`/`PlaceSearchWikidataClient`): eerst `wbsearchentities`,
 * vervolgens `Special:EntityData/<qid>.json`. Anders dan die twee clients telt hier de
 * kandidaatcardinaliteit mee: alleen bij precies één `wbsearchentities`-kandidaat wordt het
 * Context-blok gebouwd; bij nul of meer dan één kandidaat levert dit `null` op.
 */
class TopicSearchWikidataContextClient(private val restClient: RestClient) : TopicSearchWikidataContextSource {

    override fun fetchContext(term: String): TopicSearchContext? = try {
        val searchResponse = restClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/w/api.php")
                    .queryParam("action", "wbsearchentities")
                    .queryParam("search", term)
                    .queryParam("language", "nl")
                    .queryParam("type", "item")
                    .queryParam("format", "json")
                    .build()
            }
            .retrieve()
            .body(WbSearchEntitiesResponse::class.java)

        val candidateIds = searchResponse?.search.orEmpty().mapNotNull { it.id?.takeIf { id -> id.isNotBlank() } }
        val qid = candidateIds.singleOrNull() ?: return null

        val entityData = restClient.get()
            .uri("/wiki/Special:EntityData/$qid.json")
            .retrieve()
            .body(EntityDataResponse::class.java)

        val entity = entityData?.entities?.get(qid) ?: return null
        val label = entity.labels?.get("nl")?.value?.takeIf { it.isNotBlank() }
            ?: entity.labels?.get("en")?.value?.takeIf { it.isNotBlank() }
            ?: return null
        val description = entity.descriptions?.get("nl")?.value ?: entity.descriptions?.get("en")?.value

        TopicSearchContext(label, description)
    } catch (_: Exception) {
        null
    }
}
