package nl.vdzon.hkh.externalverification

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * Basis-URI voor archieven.nl, standaard het publieke opendata-endpoint. Overschrijfbaar via
 * `hkh.externalverification.archives-base-url` (env `HKH_EXTERNAL_VERIFICATION_ARCHIVES_BASE_URL`),
 * uitsluitend zodat tests tegen een lokale fixture/mock-endpoint kunnen draaien.
 */
@Configuration
class ExternalVerificationClientConfiguration(
    @param:Value("\${hkh.externalverification.archives-base-url:http://opendata.archieven.nl}")
    private val archivesBaseUrl: String,
) {
    @Bean
    fun archivesNlClient(): ArchivesNlClient =
        RestClientArchivesNlClient(RestClient.builder().baseUrl(archivesBaseUrl).build())

    @Bean
    fun historicalMetadataAdapter(): HistoricalMetadataAdapter =
        OpenArchievenMetadataAdapter(
            restClient = RestClient.builder().baseUrl(archivesBaseUrl).build(),
            serverKey = resolveServerRateLimitKey(archivesBaseUrl),
        )
}
