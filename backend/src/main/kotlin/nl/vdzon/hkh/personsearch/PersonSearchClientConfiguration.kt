package nl.vdzon.hkh.personsearch

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Basis-URI voor Open Archieven, standaard het publieke Records-endpoint. Overschrijfbaar via
 * `hkh.personsearch.archives-base-url` (env `HKH_PERSON_SEARCH_ARCHIVES_BASE_URL`), uitsluitend
 * zodat tests tegen een lokale fixture kunnen draaien.
 */
@Configuration
class PersonSearchClientConfiguration(
    @param:Value("\${hkh.personsearch.archives-base-url:https://api.openarchieven.nl/1.1}")
    private val archivesBaseUrl: String,
    @param:Value("\${hkh.personsearch.wikidata-base-url:https://www.wikidata.org}")
    private val wikidataBaseUrl: String,
) {
    @Bean
    fun personSearchRateLimiter(): PersonSearchRateLimiter = PersonSearchRateLimiter(maxPerSecond = 4)

    @Bean
    fun archivesOpenSearchClient(rateLimiter: PersonSearchRateLimiter): ArchivesOpenSearchClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(800)
            setReadTimeout(1200)
        }
        val restClient = RestClient.builder()
            .baseUrl(archivesBaseUrl)
            .requestFactory(requestFactory)
            .requestInterceptor(
                GzipRequestInterceptor(
                    "hkh-autopilot-personsearch/1.0 (+https://hkh-autopilot.example; contact: beheer@hkh-autopilot.example)",
                ),
            )
            .build()
        return RestClientArchivesOpenSearchClient(restClient, rateLimiter)
    }

    @Bean
    fun personSearchContextSource(): PersonSearchContextSource {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(500)
            setReadTimeout(800)
        }
        val restClient = RestClient.builder()
            .baseUrl(wikidataBaseUrl)
            .requestFactory(requestFactory)
            .requestInterceptor(
                GzipRequestInterceptor("hkh-autopilot-personsearch/1.0 (+https://hkh-autopilot.example)"),
            )
            .build()
        return WikidataPersonSearchContextClient(restClient)
    }
}
