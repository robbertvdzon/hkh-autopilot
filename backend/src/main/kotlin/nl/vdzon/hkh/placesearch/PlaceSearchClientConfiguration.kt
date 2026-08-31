package nl.vdzon.hkh.placesearch

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Basis-URI's voor Wikidata en Wikimedia Commons, overschrijfbaar via
 * `hkh.placesearch.wikidata-base-url`/`HKH_PLACESEARCH_WIKIDATA_BASE_URL` resp.
 * `hkh.placesearch.commons-base-url`/`HKH_PLACESEARCH_COMMONS_BASE_URL`, uitsluitend zodat tests
 * tegen een lokale fixture kunnen draaien. Eigen bean voor `commons.wikimedia.org`, naar het
 * beanpatroon van `PersonSearchClientConfiguration`.
 */
@Configuration
class PlaceSearchClientConfiguration(
    @param:Value("\${hkh.placesearch.wikidata-base-url:https://www.wikidata.org}")
    private val wikidataBaseUrl: String,
    @param:Value("\${hkh.placesearch.commons-base-url:https://commons.wikimedia.org}")
    private val commonsBaseUrl: String,
) {
    @Bean
    fun placeSearchWikidataClient(): PlaceSearchWikidataClient =
        PlaceSearchWikidataClient(buildRestClient(wikidataBaseUrl))

    @Bean
    fun placeSearchCommonsClient(): PlaceSearchCommonsClient =
        PlaceSearchCommonsClient(buildRestClient(commonsBaseUrl))

    private fun buildRestClient(baseUrl: String): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(500)
            setReadTimeout(800)
        }
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .requestInterceptor(
                PlaceSearchGzipRequestInterceptor("hkh-autopilot-placesearch/1.0 (+https://hkh-autopilot.example)"),
            )
            .build()
    }
}
