package nl.vdzon.hkh.topicsearch

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Basis-URI's voor Europeana en Wikidata, overschrijfbaar via
 * `hkh.topicsearch.europeana-base-url`/`HKH_TOPICSEARCH_EUROPEANA_BASE_URL` resp.
 * `hkh.topicsearch.wikidata-base-url`/`HKH_TOPICSEARCH_WIKIDATA_BASE_URL`, uitsluitend zodat tests
 * tegen een lokale fixture kunnen draaien. De Europeana-API-key komt uitsluitend uit
 * `HKH_EUROPEANA_API_KEY`; een ontbrekende/lege waarde wordt fail-closed als configuratiefout
 * behandeld door `RestClientArchivesEuropeanaClient` (nooit de gedeelde testkey `api2demo` hier
 * gecommit).
 */
@Configuration
class TopicSearchClientConfiguration(
    @param:Value("\${hkh.topicsearch.europeana-base-url:https://api.europeana.eu}")
    private val europeanaBaseUrl: String,
    @param:Value("\${hkh.topicsearch.wikidata-base-url:https://www.wikidata.org}")
    private val wikidataBaseUrl: String,
    @param:Value("\${HKH_EUROPEANA_API_KEY:}")
    private val europeanaApiKey: String,
) {
    @Bean
    fun archivesEuropeanaClient(): ArchivesEuropeanaClient =
        RestClientArchivesEuropeanaClient(buildRestClient(europeanaBaseUrl), europeanaApiKey)

    @Bean
    fun topicSearchWikidataContextClient(): TopicSearchWikidataContextClient =
        TopicSearchWikidataContextClient(buildRestClient(wikidataBaseUrl))

    private fun buildRestClient(baseUrl: String): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(500)
            setReadTimeout(1200)
        }
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .requestInterceptor(
                TopicSearchGzipRequestInterceptor("hkh-autopilot-topicsearch/1.0 (+https://hkh-autopilot.example)"),
            )
            .build()
    }
}
