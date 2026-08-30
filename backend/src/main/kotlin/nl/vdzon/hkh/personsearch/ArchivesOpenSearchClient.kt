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
        if (body.errorCode != null) return ArchivesSearchOutcome.Failure
        val numberFound = body.response?.numberFound ?: return ArchivesSearchOutcome.Failure
        val docs = body.response.docs
            ?: return if (numberFound == 0) {
                ArchivesSearchOutcome.Success(numberFound, emptyList())
            } else {
                ArchivesSearchOutcome.Failure
            }

        val items = docs.mapNotNull { doc ->
            val archiveCode = doc.archiveCode?.takeIf { it.isNotBlank() }
            val identifier = doc.identifier?.takeIf { it.isNotBlank() }
            if (archiveCode == null || identifier == null) null else ArchivesSearchResultItem(archiveCode, identifier)
        }
        if (items.size != docs.size) return ArchivesSearchOutcome.Failure

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
        if (body.errorCode != null) return ArchivesShowOutcome.Failure

        val event = body.event ?: return ArchivesShowOutcome.Failure
        val eventType = event.type?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure
        val eventDate = formatIsoDate(event.date) ?: return ArchivesShowOutcome.Failure
        val eventPlace = event.place?.place?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure

        val personNamesByPid = body.person.orEmpty().mapNotNull { person ->
            val pid = person.pid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val fullName = listOfNotNull(person.personName?.firstName, person.personName?.lastName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .takeIf { it.isNotBlank() } ?: return@mapNotNull null
            pid to fullName
        }.toMap()

        val relationEntries = body.relationEP.orEmpty()
            .let { entries -> if (event.eid != null) entries.filter { it.eventKeyRef == event.eid } else entries }
        val mainRelation = relationEntries.firstOrNull() ?: return ArchivesShowOutcome.Failure
        val personName = personNamesByPid[mainRelation.personKeyRef] ?: return ArchivesShowOutcome.Failure

        val relations = relationEntries.drop(1).mapNotNull { relation ->
            val role = relation.relationType?.takeIf { it.isNotBlank() }
            val relatedPersonName = personNamesByPid[relation.personKeyRef]
            if (role == null || relatedPersonName == null) null else ArchivesRelation(role, relatedPersonName)
        }

        val source = body.source ?: return ArchivesShowOutcome.Failure
        val institution = source.sourceReference?.institutionName?.takeIf { it.isNotBlank() }
            ?: return ArchivesShowOutcome.Failure
        val sourceType = source.sourceType?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure
        val recordNumber = source.recordIdentifier?.takeIf { it.isNotBlank() } ?: return ArchivesShowOutcome.Failure

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
                archiveNumber = source.sourceReference?.archive?.takeIf { it.isNotBlank() },
                registerNumber = source.sourceReference?.registryNumber?.takeIf { it.isNotBlank() },
                deedNumber = source.sourceReference?.documentNumber?.takeIf { it.isNotBlank() },
                recordNumber = recordNumber,
                digitalOriginalUrl = source.sourceDigitalOriginal?.takeIf { it.isNotBlank() },
            ),
        )
    }

    /** Bouwt een ISO-datum uit Year/Month/Day; zonder Month/Day blijft het bij het jaartal (geen gefabriceerde dag). */
    private fun formatIsoDate(date: ArchivesEventDateDto?): String? {
        val year = date?.year?.takeIf { it.isNotBlank() } ?: return null
        val month = date.month?.takeIf { it.isNotBlank() }
        val day = date.day?.takeIf { it.isNotBlank() }
        return if (month != null && day != null) "$year-${month.padStart(2, '0')}-${day.padStart(2, '0')}" else year
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
