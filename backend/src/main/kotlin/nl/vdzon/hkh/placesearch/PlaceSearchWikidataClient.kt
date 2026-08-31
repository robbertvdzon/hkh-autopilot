package nl.vdzon.hkh.placesearch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.JsonNode
import org.springframework.web.client.RestClient

/** Vaste QID/label van de gemeente Heemskerk (analoog aan de frontend `WikidataMeaningIds.place`). */
const val PLACE_SEARCH_HEEMSKERK_QID = "Q9926"
const val PLACE_SEARCH_HEEMSKERK_LABEL = "Heemskerk"

/**
 * Eigen, eenvoudige geometrische aanname voor de Heemskerkse gemeentegrens als P625-fallback (geen
 * officiële Wikidata-geometrie, geen SPARQL/Query Service-aanroep): een ruime rechthoekige bounding
 * box rond de bebouwde kom en het buitengebied van de gemeente Heemskerk, Noord-Holland. Bewust
 * ruim gekozen zodat randgevallen (bv. kuststrook, Assumburg/Oud Haerlem) er binnen vallen, zonder
 * naburige gemeenten als Beverwijk of Uitgeest te dekken.
 */
private const val HEEMSKERK_MIN_LATITUDE = 52.47
private const val HEEMSKERK_MAX_LATITUDE = 52.56
private const val HEEMSKERK_MIN_LONGITUDE = 4.60
private const val HEEMSKERK_MAX_LONGITUDE = 4.75

private const val PROPERTY_LOCATED_IN = "P131"
private const val PROPERTY_COORDINATES = "P625"
private const val PROPERTY_INCEPTION = "P571"
private const val PROPERTY_ARCHITECTURAL_STYLE = "P149"
private const val PROPERTY_ARCHITECT = "P84"
private const val PROPERTY_HERITAGE_DESIGNATION = "P1435"
private const val PROPERTY_COMMONS_CATEGORY = "P373"
private const val PROPERTY_IMAGE = "P18"

fun wikidataItemLink(qid: String) = "https://www.wikidata.org/wiki/$qid"

@JsonIgnoreProperties(ignoreUnknown = true)
private data class WbSearchEntitiesResponse(@param:JsonProperty("search") val search: List<WbSearchEntity>? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class WbSearchEntity(@param:JsonProperty("id") val id: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class WikidataEntityDataResponse(
    @param:JsonProperty("entities") val entities: Map<String, WikidataEntity>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WikidataEntity(
    @param:JsonProperty("labels") val labels: Map<String, WikidataTextValue>? = null,
    @param:JsonProperty("descriptions") val descriptions: Map<String, WikidataTextValue>? = null,
    @param:JsonProperty("claims") val claims: Map<String, List<WikidataClaim>>? = null,
) {
    fun labelOrNull(): String? = labels?.get("nl")?.value?.takeIf { it.isNotBlank() }
        ?: labels?.get("en")?.value?.takeIf { it.isNotBlank() }

    fun descriptionOrNull(): String? = descriptions?.get("nl")?.value?.takeIf { it.isNotBlank() }
        ?: descriptions?.get("en")?.value?.takeIf { it.isNotBlank() }

    private fun claimValues(property: String): List<JsonNode> =
        claims?.get(property).orEmpty()
            .mapNotNull { it.mainsnak }
            .filter { it.snaktype == "value" }
            .mapNotNull { it.datavalue?.value }

    fun entityIds(property: String): List<String> = claimValues(property).mapNotNull { it.get("id")?.asText() }

    fun coordinates(): List<Pair<Double, Double>> = claimValues(PROPERTY_COORDINATES).mapNotNull { value ->
        val latitude = value.get("latitude")?.asDouble()
        val longitude = value.get("longitude")?.asDouble()
        if (latitude != null && longitude != null) latitude to longitude else null
    }

    fun firstYear(property: String): String? = claimValues(property).firstNotNullOfOrNull { value ->
        value.get("time")?.asText()?.let(::extractYear)
    }

    fun firstString(property: String): String? = claimValues(property).firstNotNullOfOrNull { value ->
        value.asText()?.takeIf { it.isNotBlank() }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class WikidataTextValue(@param:JsonProperty("value") val value: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WikidataClaim(@param:JsonProperty("mainsnak") val mainsnak: WikidataSnak? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WikidataSnak(
    @param:JsonProperty("snaktype") val snaktype: String? = null,
    @param:JsonProperty("datavalue") val datavalue: WikidataDataValue? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WikidataDataValue(@param:JsonProperty("value") val value: JsonNode? = null)

private fun extractYear(time: String): String? = Regex("^[+-]?(\\d{1,4})").find(time)?.groupValues?.get(1)

private fun isWithinHeemskerkBoundingBox(latitude: Double, longitude: Double): Boolean =
    latitude in HEEMSKERK_MIN_LATITUDE..HEEMSKERK_MAX_LATITUDE && longitude in HEEMSKERK_MIN_LONGITUDE..HEEMSKERK_MAX_LONGITUDE

/** Uitkomst van de Heemskerk-lidmaatschapstoets voor één kandidaat-item. */
data class PlaceSearchHeemskerkMatch(val matched: Boolean, val viaMunicipalityLink: Boolean)

/**
 * Zoekt kandidaat-QID's op Wikidata en haalt per QID `Special:EntityData` op, naar het patroon van
 * de bestaande `WikidataPersonSearchContextClient`. Toetst per kandidaat het Heemskerk-lidmaatschap:
 * P131 (eventueel één niveau doorverwezen) gelijk aan Q9926, of P625-coördinaten binnen de
 * hierboven gedocumenteerde bounding box. Geen SPARQL/Query Service-aanroep. Elke HTTP-fout,
 * timeout of ongeldige JSON gooit door (fail-closed op serviceniveau, dat elke fout omzet naar
 * "Wikidata is tijdelijk niet geraadpleegd" zonder claims te tonen).
 */
class PlaceSearchWikidataClient(private val restClient: RestClient) {

    fun searchCandidateIds(term: String): List<String> {
        val response = restClient.get()
            .uri { builder ->
                builder.path("/w/api.php")
                    .queryParam("action", "wbsearchentities")
                    .queryParam("search", term)
                    .queryParam("language", "nl")
                    .queryParam("type", "item")
                    .queryParam("format", "json")
                    .queryParam("limit", "5")
                    .build()
            }
            .retrieve()
            .body(WbSearchEntitiesResponse::class.java)
        return response?.search.orEmpty().mapNotNull { it.id?.takeIf { id -> id.isNotBlank() } }
    }

    fun fetchEntity(qid: String): WikidataEntity? {
        val response = restClient.get()
            .uri("/wiki/Special:EntityData/$qid.json")
            .retrieve()
            .body(WikidataEntityDataResponse::class.java)
        return response?.entities?.get(qid)
    }

    fun evaluateHeemskerkMatch(entity: WikidataEntity): PlaceSearchHeemskerkMatch {
        val direct = entity.entityIds(PROPERTY_LOCATED_IN)
        if (direct.contains(PLACE_SEARCH_HEEMSKERK_QID)) return PlaceSearchHeemskerkMatch(true, true)

        val indirect = direct.any { qid ->
            fetchEntity(qid)?.entityIds(PROPERTY_LOCATED_IN)?.contains(PLACE_SEARCH_HEEMSKERK_QID) == true
        }
        if (indirect) return PlaceSearchHeemskerkMatch(true, true)

        val coordinateMatch = entity.coordinates().any { (latitude, longitude) ->
            isWithinHeemskerkBoundingBox(latitude, longitude)
        }
        return PlaceSearchHeemskerkMatch(coordinateMatch, false)
    }

    fun locatedInQids(entity: WikidataEntity): List<String> = entity.entityIds(PROPERTY_LOCATED_IN)

    fun inceptionYear(entity: WikidataEntity): String? = entity.firstYear(PROPERTY_INCEPTION)

    fun architecturalStyleQid(entity: WikidataEntity): String? = entity.entityIds(PROPERTY_ARCHITECTURAL_STYLE).firstOrNull()

    fun architectQid(entity: WikidataEntity): String? = entity.entityIds(PROPERTY_ARCHITECT).firstOrNull()

    fun heritageDesignationQid(entity: WikidataEntity): String? = entity.entityIds(PROPERTY_HERITAGE_DESIGNATION).firstOrNull()

    fun commonsCategory(entity: WikidataEntity): String? = entity.firstString(PROPERTY_COMMONS_CATEGORY)

    fun imageFileName(entity: WikidataEntity): String? = entity.firstString(PROPERTY_IMAGE)
}
