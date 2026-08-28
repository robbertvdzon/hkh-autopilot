package nl.vdzon.hkh.personsearch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.client.RestClient

fun interface PersonSearchContextSource {
    /**
     * Haalt Wikidata-contextinformatie op voor [place]. Draagt nooit zelfstandig een geboorte-,
     * huwelijks-, overlijdens-, doop- of bevolkingsregistratiebewering; verschijnt uitsluitend
     * onder een sectie die letterlijk 'Context' heet. Fail-closed: elke fout levert `null` op en
     * blokkeert nooit het archiefantwoord.
     */
    fun fetchContext(place: String): PersonSearchWikidataContext?
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
 * Zoekt op Wikidata naar [place] en haalt van het eerste treffer label en beschrijving op, naar
 * het patroon van de bestaande frontend `WikidataMeaningClient`: eerst `wbsearchentities`,
 * vervolgens `Special:EntityData/<qid>.json`. Nooit blocking voor het archiefantwoord: elke fout
 * (netwerk, timeout, ontbrekend veld) levert `null` op.
 */
class WikidataPersonSearchContextClient(private val restClient: RestClient) : PersonSearchContextSource {

    override fun fetchContext(place: String): PersonSearchWikidataContext? = try {
        val searchResponse = restClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/w/api.php")
                    .queryParam("action", "wbsearchentities")
                    .queryParam("search", place)
                    .queryParam("language", "nl")
                    .queryParam("type", "item")
                    .queryParam("format", "json")
                    .build()
            }
            .retrieve()
            .body(WbSearchEntitiesResponse::class.java)

        val qid = searchResponse?.search.orEmpty().firstOrNull()?.id?.takeIf { it.isNotBlank() } ?: return null

        val entityData = restClient.get()
            .uri("/wiki/Special:EntityData/$qid.json")
            .retrieve()
            .body(EntityDataResponse::class.java)

        val entity = entityData?.entities?.get(qid) ?: return null
        val label = entity.labels?.get("nl")?.value?.takeIf { it.isNotBlank() }
            ?: entity.labels?.get("en")?.value?.takeIf { it.isNotBlank() }
            ?: return null
        val description = entity.descriptions?.get("nl")?.value ?: entity.descriptions?.get("en")?.value

        PersonSearchWikidataContext(label, description)
    } catch (_: Exception) {
        null
    }
}
