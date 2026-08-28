package nl.vdzon.hkh.personsearch

import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

interface ArchivesOpenSearchClient {
    /** `GET /records/search.json` met de exacte queryparameters uit de story. */
    fun search(name: String, start: Int, numberShow: Int): ArchivesSearchOutcome

    /** `GET /records/show.json` voor één kandidaatrecord. */
    fun show(archiveCode: String, identifier: String): ArchivesShowOutcome
}

/**
 * Bevraagt Open Archieven Records/Search en Records/Show v1.1. Fail-closed: HTTP niet-2xx,
 * time-outs, ongeldige JSON, ontbrekende verplichte velden en een gevuld `error_code` (ook bij
 * HTTP 200) worden altijd als mislukte bronraadpleging behandeld. Begrensde retries (geen
 * onbegrensde back-off) op transiënte fouten; rate limiting gebeurt vóór elke aanroep.
 */
class RestClientArchivesOpenSearchClient(
    private val restClient: RestClient,
    private val rateLimiter: PersonSearchRateLimiter,
    private val maxAttempts: Int = 3,
    private val retryBaseDelayMillis: Long = 200,
    private val sleep: (Long) -> Unit = { millis -> if (millis > 0) Thread.sleep(millis) },
) : ArchivesOpenSearchClient {

    override fun search(name: String, start: Int, numberShow: Int): ArchivesSearchOutcome {
        val entity = executeWithRetries {
            restClient.get()
                .uri { uriBuilder ->
                    uriBuilder.path("/records/search.json")
                        .queryParam("name", name)
                        .queryParam("archive_code", "nha")
                        .queryParam("eventplace", "Heemskerk")
                        .queryParam("lang", "nl")
                        .queryParam("number_show", numberShow)
                        .queryParam("start", start)
                        .build()
                }
                .retrieve()
                .toEntity(ArchivesSearchResponseDto::class.java)
        } ?: return ArchivesSearchOutcome.Failure

        val body = entity.body
        if (!entity.statusCode.is2xxSuccessful || body == null) return ArchivesSearchOutcome.Failure
        if (!body.errorCode.isNullOrBlank()) return ArchivesSearchOutcome.Failure
        val numberFound = body.numberFound ?: return ArchivesSearchOutcome.Failure
        val results = body.results ?: return ArchivesSearchOutcome.Failure

        val items = results.mapNotNull { result ->
            val archiveCode = result.archiveCode?.takeIf { it.isNotBlank() }
            val identifier = result.identifier?.takeIf { it.isNotBlank() }
            if (archiveCode == null || identifier == null) null else ArchivesSearchResultItem(archiveCode, identifier)
        }
        if (items.size != results.size) return ArchivesSearchOutcome.Failure

        return ArchivesSearchOutcome.Success(numberFound, items)
    }

    override fun show(archiveCode: String, identifier: String): ArchivesShowOutcome {
        val entity = executeWithRetries {
            restClient.get()
                .uri { uriBuilder ->
                    uriBuilder.path("/records/show.json")
                        .queryParam("archive", archiveCode)
                        .queryParam("identifier", identifier)
                        .queryParam("lang", "nl")
                        .build()
                }
                .retrieve()
                .toEntity(ArchivesShowResponseDto::class.java)
        } ?: return ArchivesShowOutcome.Failure

        val body = entity.body
        if (!entity.statusCode.is2xxSuccessful || body == null) return ArchivesShowOutcome.Failure
        if (!body.errorCode.isNullOrBlank()) return ArchivesShowOutcome.Failure

        val personName = body.person?.name?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure
        val eventType = body.event?.type?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure
        val eventDate = body.event.date?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure
        val eventPlace = body.event.place?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure
        val institution = body.source?.institution?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure
        val sourceType = body.source.sourceType?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure
        val recordNumber = body.source.recordNumber?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure

        val relations = body.relationEP.orEmpty().mapNotNull { relation ->
            val role = relation.role?.takeIf { it.isNotBlank() }
            val person = relation.person?.takeIf { it.isNotBlank() }
            if (role == null || person == null) null else ArchivesRelation(role, person)
        }

        return ArchivesShowOutcome.Success(
            ArchivesShowRecord(
                archiveCode = archiveCode,
                identifier = identifier,
                personName = personName,
                eventType = eventType,
                eventDate = eventDate,
                eventPlace = eventPlace,
                relations = relations,
                institution = institution,
                sourceType = sourceType,
                archiveNumber = body.source.archiveNumber?.takeIf { it.isNotBlank() },
                registerNumber = body.source.registerNumber?.takeIf { it.isNotBlank() },
                deedNumber = body.source.deedNumber?.takeIf { it.isNotBlank() },
                recordNumber = recordNumber,
                digitalOriginalUrl = body.source.digitalOriginalUrl?.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun <T : Any> executeWithRetries(call: () -> ResponseEntity<T>): ResponseEntity<T>? {
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            rateLimiter.acquire()
            try {
                val response = call()
                if (response.statusCode.is5xxServerError() && attempt < maxAttempts) {
                    sleep(retryBaseDelayMillis * attempt)
                    continue
                }
                return response
            } catch (exception: RestClientException) {
                if (attempt >= maxAttempts) return null
                sleep(retryBaseDelayMillis * attempt)
            }
        }
        return null
    }
}
