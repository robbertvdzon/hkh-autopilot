package nl.vdzon.hkh.externalverification

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

/**
 * De archiefkernvelden die uit de opgehaalde JSON-LD-respons worden gelezen. [license] is de
 * hergebruikslicentie van dit specifieke record (bijvoorbeeld `CC0`); deze wordt nooit van een
 * ander record binnen dezelfde collectie afgeleid of hergebruikt.
 */
data class ArchiveRecordFields(
    val name: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val license: String? = null,
)

/** Uitkomst van een bevraging van archieven.nl. Er wordt nooit de volledige externe payload bewaard. */
sealed interface ArchiveFetchResult {
    data class Found(val fields: ArchiveRecordFields) : ArchiveFetchResult
    data object NotFound : ArchiveFetchResult
    data object AuthenticationRequired : ArchiveFetchResult
}

fun interface ArchivesNlClient {
    /** Bevraagt de resolvebare archieven.nl-URI voor [adtid]/[guid]. Fail-closed: elke fout levert [ArchiveFetchResult.NotFound] op. */
    fun fetch(adtid: String, guid: String, accessToken: String?): ArchiveFetchResult
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ArchiveJsonLdRecord(
    val name: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val license: String? = null,
)

/**
 * Bevraagt `http://opendata.archieven.nl/id/<adtid>/<guid>` met header `Accept: application/ld+json`.
 * Standaard wordt geen autorisatietoken meegestuurd; alleen wanneer [accessToken] is meegegeven
 * (uitsluitend nodig wanneer het endpoint dat expliciet eist) wordt een `Authorization: Bearer`-
 * header toegevoegd. Content-negotiation en JSON-LD-parsing gebeuren hier serverside; er wordt
 * nooit de volledige externe payload teruggegeven, alleen de kernvelden.
 */
class RestClientArchivesNlClient(private val restClient: RestClient) : ArchivesNlClient {

    override fun fetch(adtid: String, guid: String, accessToken: String?): ArchiveFetchResult = try {
        val record = restClient.get()
            .uri("/id/{adtid}/{guid}", adtid, guid)
            .headers { headers ->
                headers.set(HttpHeaders.ACCEPT, "application/ld+json")
                if (!accessToken.isNullOrBlank()) headers.setBearerAuth(accessToken)
            }
            .retrieve()
            .body(ArchiveJsonLdRecord::class.java)

        if (record == null) {
            ArchiveFetchResult.NotFound
        } else {
            ArchiveFetchResult.Found(
                ArchiveRecordFields(record.name, record.birthDate, record.deathDate, record.license),
            )
        }
    } catch (exception: RestClientResponseException) {
        when (exception.statusCode) {
            HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN -> ArchiveFetchResult.AuthenticationRequired
            else -> ArchiveFetchResult.NotFound
        }
    } catch (_: Exception) {
        ArchiveFetchResult.NotFound
    }
}
