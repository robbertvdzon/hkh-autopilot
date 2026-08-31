package nl.vdzon.hkh.placesearch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.client.RestClient

private const val MAX_COMMONS_RESULTS = 12
private const val MAX_DISPLAYED_IMAGES = 6

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CommonsQueryResponse(@param:JsonProperty("query") val query: CommonsQuery? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CommonsQuery(@param:JsonProperty("pages") val pages: Map<String, CommonsPage>? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CommonsPage(
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("imageinfo") val imageinfo: List<CommonsImageInfo>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CommonsImageInfo(
    @param:JsonProperty("url") val url: String? = null,
    @param:JsonProperty("descriptionurl") val descriptionUrl: String? = null,
    @param:JsonProperty("extmetadata") val extmetadata: CommonsExtMetadata? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CommonsExtMetadata(
    @param:JsonProperty("LicenseShortName") val licenseShortName: CommonsMetadataValue? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CommonsMetadataValue(@param:JsonProperty("value") val value: String? = null)

/**
 * Bevraagt live de Wikimedia Commons Action-API voor afbeeldingen, hetzij een categorie
 * ([fetchCategoryImages], P373-pad) hetzij één bestand ([fetchSingleFile], P18-fallbackpad).
 * Toont maximaal [MAX_DISPLAYED_IMAGES] gededupliceerde afbeeldingen (op bestandsnaam). Elke
 * HTTP-fout, timeout of ongeldige JSON gooit door; de aanroeper (`PlaceSearchService`) zet dit fail-
 * closed om in "Wikimedia Commons · niet uitgevoerd" zonder het Wikidata-antwoord te blokkeren. Een
 * legitiem lege respons (geen categorie/P18 of nul resultaten) levert gewoon een lege lijst op.
 */
class PlaceSearchCommonsClient(private val restClient: RestClient) {

    fun fetchCategoryImages(categoryName: String): List<PlaceSearchImage> {
        val response = restClient.get()
            .uri { builder ->
                builder.path("/w/api.php")
                    .queryParam("action", "query")
                    .queryParam("generator", "categorymembers")
                    .queryParam("gcmtitle", "Category:$categoryName")
                    .queryParam("gcmtype", "file")
                    .queryParam("gcmlimit", MAX_COMMONS_RESULTS.toString())
                    .queryParam("prop", "imageinfo")
                    .queryParam("iiprop", "url|extmetadata")
                    .queryParam("format", "json")
                    .build()
            }
            .retrieve()
            .body(CommonsQueryResponse::class.java)
        return response.toImages()
    }

    fun fetchSingleFile(fileName: String): List<PlaceSearchImage> {
        val response = restClient.get()
            .uri { builder ->
                builder.path("/w/api.php")
                    .queryParam("action", "query")
                    .queryParam("titles", "File:$fileName")
                    .queryParam("prop", "imageinfo")
                    .queryParam("iiprop", "url|extmetadata")
                    .queryParam("format", "json")
                    .build()
            }
            .retrieve()
            .body(CommonsQueryResponse::class.java)
        return response.toImages()
    }

    private fun CommonsQueryResponse?.toImages(): List<PlaceSearchImage> =
        this?.query?.pages?.values.orEmpty()
            .mapNotNull { page ->
                val info = page.imageinfo?.firstOrNull() ?: return@mapNotNull null
                val url = info.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = page.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PlaceSearchImage(
                    url = url,
                    fileName = title,
                    license = info.extmetadata?.licenseShortName?.value,
                    filePageUrl = info.descriptionUrl?.takeIf { it.isNotBlank() } ?: url,
                )
            }
            .distinctBy { it.fileName }
            .take(MAX_DISPLAYED_IMAGES)
}
