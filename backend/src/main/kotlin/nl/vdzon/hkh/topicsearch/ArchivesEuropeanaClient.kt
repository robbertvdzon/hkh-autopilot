package nl.vdzon.hkh.topicsearch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

fun interface ArchivesEuropeanaClient {
    /** `GET /record/v2/search.json` met `query`, `rows=8`, `profile=rich` en de eigen API-key. */
    fun search(query: String): EuropeanaSearchOutcome
}

sealed interface EuropeanaSearchOutcome {
    /** Reeds gevalideerde en gefilterde records; ongeldige items zijn genegeerd, ook voor het totaal. */
    data class Success(val validRecords: List<TopicSearchRecord>) : EuropeanaSearchOutcome
    data object Failure : EuropeanaSearchOutcome
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EuropeanaSearchResponseDto(@param:JsonProperty("items") val items: List<EuropeanaItemDto>? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EuropeanaItemDto(
    @param:JsonProperty("title") val title: List<String>? = null,
    @param:JsonProperty("dcDescription") val dcDescription: List<String>? = null,
    @param:JsonProperty("dataProvider") val dataProvider: List<String>? = null,
    @param:JsonProperty("edmIsShownAt") val edmIsShownAt: List<String>? = null,
    @param:JsonProperty("guid") val guid: String? = null,
    @param:JsonProperty("rights") val rights: List<String>? = null,
)

/**
 * Bevraagt de Europeana Record/Search API v2. Fail-closed: een ontbrekende/lege API-key wordt
 * behandeld als configuratiefout (dezelfde uitkomst als een echte storing, zonder aanroep). Elke
 * HTTP-fout, niet-2xx-status of ongeldige JSON levert [EuropeanaSearchOutcome.Failure] op. De
 * gedeelde testkey `api2demo` wordt hier nergens gecommit; de echte key komt uitsluitend uit de
 * omgevingsconfiguratie (`HKH_EUROPEANA_API_KEY`).
 */
class RestClientArchivesEuropeanaClient(
    private val restClient: RestClient,
    private val apiKey: String,
) : ArchivesEuropeanaClient {

    override fun search(query: String): EuropeanaSearchOutcome {
        if (apiKey.isBlank()) return EuropeanaSearchOutcome.Failure

        return try {
            val entity = restClient.get()
                .uri { uriBuilder ->
                    uriBuilder.path("/record/v2/search.json")
                        .queryParam("query", query)
                        .queryParam("rows", 8)
                        .queryParam("profile", "rich")
                        .queryParam("wskey", apiKey)
                        .build()
                }
                .retrieve()
                .toEntity(EuropeanaSearchResponseDto::class.java)

            val body = entity.body
            if (!entity.statusCode.is2xxSuccessful || body == null) return EuropeanaSearchOutcome.Failure

            val validRecords = body.items.orEmpty().mapNotNull { item ->
                buildTopicSearchRecordOrNull(
                    titles = item.title,
                    descriptions = item.dcDescription,
                    dataProviders = item.dataProvider,
                    edmIsShownAt = item.edmIsShownAt,
                    guid = item.guid,
                    rights = item.rights,
                )
            }
            EuropeanaSearchOutcome.Success(validRecords)
        } catch (_: RestClientException) {
            EuropeanaSearchOutcome.Failure
        } catch (_: Exception) {
            EuropeanaSearchOutcome.Failure
        }
    }
}
