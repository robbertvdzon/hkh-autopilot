package nl.vdzon.hkh.externalverification

import java.time.Clock
import java.time.Instant
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

/** Adaptercontract voor toekomstige zoekfunctionaliteit; er is bewust nog geen publieke zoekroute. */
fun interface HistoricalMetadataAdapter {
    fun fetch(adtid: String, guid: String): HistoricalMetadataResult
}

/**
 * Leest uitsluitend een allowlist van beschrijvende JSON-LD-velden van Open Archieven/NHA. De ruwe
 * respons wordt niet opgeslagen, geretourneerd of gelogd. Iedere aanroep haalt opnieuw de actuele
 * bronversie op; er is geen cache die een ouder resultaat als actueel kan presenteren.
 */
class OpenArchievenMetadataAdapter(
    private val restClient: RestClient,
    private val serverKey: String = "opendata.archieven.nl",
    private val clock: Clock = Clock.systemUTC(),
    private val rateLimiter: HistoricalMetadataRateLimiter = FourPerSecondRateLimiter(),
    private val objectMapper: ObjectMapper = JsonMapper.builder().build(),
) : HistoricalMetadataAdapter {

    override fun fetch(adtid: String, guid: String): HistoricalMetadataResult {
        val fetchedAt = Instant.now(clock)
        val fallbackIdentifier = "$adtid/$guid"
        val fallbackLink = "http://opendata.archieven.nl/id/$adtid/$guid"
        if (!isSafePathPart(adtid) || !isSafePathPart(guid)) {
            return minimal(
                fetchedAt,
                fallbackIdentifier.takeIf { isSafePathPart(adtid) && isSafePathPart(guid) },
                fallbackLink.takeIf { isSafePathPart(adtid) && isSafePathPart(guid) },
                HistoricalMetadataAvailabilityStatus.INVALID_RESPONSE,
                HistoricalMetadataVerificationReasons.INVALID_REQUIRED_FIELD,
            )
        }

        rateLimiter.awaitPermit(serverKey)
        val response = runCatching {
            restClient.get()
                .uri("/id/{adtid}/{guid}", adtid, guid)
                .headers { headers ->
                    headers.set(HttpHeaders.ACCEPT, "application/ld+json")
                    headers.set(HttpHeaders.USER_AGENT, USER_AGENT)
                }
                .exchange { _, clientResponse ->
                    val body = clientResponse.bodyTo(String::class.java).orEmpty()
                    AdapterHttpResponse(
                        statusCode = clientResponse.statusCode.value(),
                        body = body,
                        etag = clientResponse.headers.getFirst(HttpHeaders.ETAG),
                        lastModified = clientResponse.headers.lastModified.takeIf { it >= 0L },
                    )
                }
        }.getOrNull()

        if (response == null) {
            return minimal(
                fetchedAt,
                fallbackIdentifier,
                fallbackLink,
                HistoricalMetadataAvailabilityStatus.TEMPORARILY_UNAVAILABLE,
                HistoricalMetadataVerificationReasons.SOURCE_TEMPORARILY_UNAVAILABLE,
            )
        }
        if (response.statusCode !in 200..299) {
            val temporary = response.statusCode == 408 || response.statusCode == 429 || response.statusCode >= 500
            val availability = if (temporary) {
                HistoricalMetadataAvailabilityStatus.TEMPORARILY_UNAVAILABLE
            } else {
                HistoricalMetadataAvailabilityStatus.INVALID_RESPONSE
            }
            return minimal(
                fetchedAt,
                fallbackIdentifier,
                fallbackLink,
                availability,
                if (temporary) HistoricalMetadataVerificationReasons.SOURCE_TEMPORARILY_UNAVAILABLE
                else HistoricalMetadataVerificationReasons.INVALID_SOURCE_RESPONSE,
            )
        }
        if (response.body.isBlank()) {
            return minimal(
                fetchedAt,
                fallbackIdentifier,
                fallbackLink,
                HistoricalMetadataAvailabilityStatus.EMPTY_RESPONSE,
                HistoricalMetadataVerificationReasons.EMPTY_SOURCE_RESPONSE,
            )
        }

        val parsed = runCatching { parse(response, adtid, guid) }.getOrNull()
            ?: return minimal(
                fetchedAt,
                fallbackIdentifier,
                fallbackLink,
                HistoricalMetadataAvailabilityStatus.INVALID_RESPONSE,
                HistoricalMetadataVerificationReasons.INVALID_SOURCE_RESPONSE,
            )

        return HistoricalMetadataContract.evaluate(
            HistoricalMetadataCandidate(
                sourceIdentifier = parsed.sourceIdentifier,
                sourceLink = fallbackLink,
                holder = parsed.holder,
                title = parsed.title,
                description = parsed.description,
                dating = parsed.dating,
                sourceVersion = parsed.sourceVersion,
                snapshotId = parsed.snapshotId,
                fetchedAt = fetchedAt,
                metadataRightsStatus = parsed.metadataRightsStatus,
                objectMediaRightsStatus = parsed.objectMediaRightsStatus,
                privacyStatus = parsed.privacyStatus,
                availabilityStatus = parsed.availabilityStatus,
                containsUnclearedPersonalData = parsed.containsUnclearedPersonalData,
                containsContradictorySourceData = parsed.containsContradictorySourceData,
                fallbackSourceIdentifier = fallbackIdentifier,
                fallbackSourceLink = fallbackLink,
            ),
        )
    }

    private fun parse(response: AdapterHttpResponse, adtid: String, guid: String): ParsedMetadata {
        val root = objectMapper.readTree(response.body)
        val nodes = when {
            root.isObject && root.get("@graph")?.isArray == true -> root.get("@graph").asIterable().filter(JsonNode::isObject)
            root.isArray -> root.asIterable().filter(JsonNode::isObject)
            root.isObject -> listOf(root)
            else -> emptyList()
        }
        require(nodes.isNotEmpty()) { "source response has no metadata nodes" }

        val identifier = nodes.pick("sourceIdentifier", "identifier", "dcterms:identifier", "id", "@id")
        val holder = nodes.pick("holder", "rightsHolder", "publisher", "dcterms:publisher", "provider")
        val title = nodes.pick("title", "dcterms:title")
        val description = nodes.pick("description", "dcterms:description", "summary")
        val dating = nodes.pick("dating", "date", "dcterms:date", "temporalCoverage", "sdo:dateCreated", "dateCreated")
        val bodyVersion = nodes.pick("sourceVersion", "version", "dcterms:hasVersion", "dcterms:modified", "sdo:dateModified")
        val etag = response.etag.cleanHeaderValue()
        val lastModified = response.lastModified?.let { Instant.ofEpochMilli(it).toString() }
        val sourceVersion = bodyVersion ?: etag ?: lastModified
        val versionConflict = bodyVersion != null && etag != null && bodyVersion != etag

        val snapshot = nodes.pick("snapshotId", "snapshot", "momentopnameId")
        val explicitAvailability = nodes.pick("technicalAvailability", "availability")?.let(::parseAvailability)
        val httpAvailability = HistoricalMetadataAvailabilityStatus.AVAILABLE
        val availability = explicitAvailability ?: httpAvailability
        val availabilityConflict = explicitAvailability != null && explicitAvailability != httpAvailability

        val identifierConflict = nodes.hasConflictingValues("sourceIdentifier", "identifier", "dcterms:identifier")
        val holderConflict = nodes.hasConflictingValues("holder", "rightsHolder", "publisher", "dcterms:publisher", "provider")
        val privacy = parsePrivacy(nodes)

        return ParsedMetadata(
            sourceIdentifier = identifier?.takeIf { it != fallbackIdentifier(adtid, guid) },
            holder = holder,
            title = title,
            description = description,
            dating = dating,
            sourceVersion = sourceVersion,
            snapshotId = snapshot,
            metadataRightsStatus = nodes.pick("metadataRightsStatus", "metadataRights", "metadataLicenseStatus")
                ?.let(::parseMetadataRights) ?: MetadataRightsStatus.UNKNOWN,
            objectMediaRightsStatus = nodes.pick("objectMediaRightsStatus", "objectRights", "mediaRightsStatus", "mediaRights")
                ?.let(::parseObjectRights) ?: ObjectMediaRightsStatus.UNKNOWN,
            privacyStatus = privacy.status,
            availabilityStatus = availability,
            containsUnclearedPersonalData = privacy.containsUnclearedPersonalData,
            containsContradictorySourceData = versionConflict || availabilityConflict || identifierConflict || holderConflict,
        )
    }

    private fun parsePrivacy(nodes: List<JsonNode>): ParsedPrivacy {
        val explicit = nodes.pick("privacyStatus", "privacy", "personalDataStatus")?.let(::parsePrivacyStatus)
        val sensitiveFieldPresent = listOf(
            "name", "givenName", "familyName", "person", "persons", "birthDate", "deathDate",
            "personalData", "containsPersonalData", "livingPerson", "possibleLivingPerson",
        ).any { field -> nodes.any { node -> node.get(field)?.let { value -> value.asBoolean(false) || value.isObject || value.isArray || value.isString } == true } }
        val flagged = listOf("personalData", "containsPersonalData", "livingPerson", "possibleLivingPerson")
            .any { field -> nodes.any { it.get(field)?.asBoolean(false) == true } }
        return ParsedPrivacy(
            status = if (flagged) HistoricalMetadataPrivacyStatus.BLOCKED else explicit ?: HistoricalMetadataPrivacyStatus.UNKNOWN,
            containsUnclearedPersonalData = sensitiveFieldPresent,
        )
    }

    private fun parseMetadataRights(value: String): MetadataRightsStatus = when (value.normalized()) {
        "ALLOWED", "PUBLIC", "PERMITTED", "OPEN" -> MetadataRightsStatus.ALLOWED
        "RESTRICTED", "CLOSED", "LIMITED" -> MetadataRightsStatus.RESTRICTED
        else -> MetadataRightsStatus.UNKNOWN
    }

    private fun parseObjectRights(value: String): ObjectMediaRightsStatus = when (value.normalized()) {
        "ALLOWED", "PUBLIC", "PERMITTED", "OPEN" -> ObjectMediaRightsStatus.ALLOWED
        "RESTRICTED", "CLOSED", "LIMITED" -> ObjectMediaRightsStatus.RESTRICTED
        else -> ObjectMediaRightsStatus.UNKNOWN
    }

    private fun parsePrivacyStatus(value: String): HistoricalMetadataPrivacyStatus = when (value.normalized()) {
        "CLEAR", "NO_PERSONAL_DATA", "NO_PERSONS", "PUBLIC" -> HistoricalMetadataPrivacyStatus.CLEAR
        "BLOCKED", "PERSONAL_DATA", "LIVING_PERSON", "RESTRICTED" -> HistoricalMetadataPrivacyStatus.BLOCKED
        else -> HistoricalMetadataPrivacyStatus.UNKNOWN
    }

    private fun parseAvailability(value: String): HistoricalMetadataAvailabilityStatus = when (value.normalized()) {
        "AVAILABLE", "OK" -> HistoricalMetadataAvailabilityStatus.AVAILABLE
        "TEMPORARILY_UNAVAILABLE", "UNAVAILABLE", "TIMEOUT" -> HistoricalMetadataAvailabilityStatus.TEMPORARILY_UNAVAILABLE
        "EMPTY", "EMPTY_RESPONSE" -> HistoricalMetadataAvailabilityStatus.EMPTY_RESPONSE
        else -> HistoricalMetadataAvailabilityStatus.INVALID_RESPONSE
    }

    private fun List<JsonNode>.pick(vararg names: String): String? = names
        .asSequence()
        .flatMap { name -> asSequence().flatMap { node -> scalarValues(node.get(name)).asSequence() } }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .firstOrNull()

    private fun List<JsonNode>.hasConflictingValues(vararg names: String): Boolean = names
        .asSequence()
        .flatMap { name -> asSequence().flatMap { node -> scalarValues(node.get(name)).asSequence() } }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .count() > 1

    private fun scalarValues(node: JsonNode?): List<String> = when {
        node == null || node.isNull -> emptyList()
        node.isArray -> node.asIterable().flatMap(::scalarValues)
        node.isString || node.isNumber || node.isBoolean -> listOf(node.asString())
        node.isObject -> scalarValues(node.get("@value") ?: node.get("value") ?: node.get("@id"))
        else -> emptyList()
    }

    private fun String.normalized(): String = trim().uppercase().replace('-', '_').replace(' ', '_')

    private fun String?.cleanHeaderValue(): String? = this?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }

    private fun minimal(
        fetchedAt: Instant,
        sourceIdentifier: String?,
        sourceLink: String?,
        availability: HistoricalMetadataAvailabilityStatus,
        reason: String,
    ) = HistoricalMetadataResult(
        sourceIdentifier = sourceIdentifier,
        sourceLink = sourceLink,
        fetchedAt = fetchedAt,
        metadataRightsStatus = MetadataRightsStatus.UNKNOWN,
        objectMediaRightsStatus = ObjectMediaRightsStatus.UNKNOWN,
        privacyStatus = HistoricalMetadataPrivacyStatus.UNKNOWN,
        availabilityStatus = availability,
        verificationStatus = HistoricalMetadataVerificationStatus.UNVERIFIED,
        verificationReason = reason,
    )

    private fun isSafePathPart(value: String): Boolean = value.isNotBlank() && value.length <= 200 &&
        value.none { it.isWhitespace() || it.isISOControl() || it == '/' || it == '?' || it == '#' }

    private fun fallbackIdentifier(adtid: String, guid: String): String = "$adtid/$guid"

    private data class AdapterHttpResponse(
        val statusCode: Int,
        val body: String,
        val etag: String?,
        val lastModified: Long?,
    )

    private data class ParsedMetadata(
        val sourceIdentifier: String?,
        val holder: String?,
        val title: String?,
        val description: String?,
        val dating: String?,
        val sourceVersion: String?,
        val snapshotId: String?,
        val metadataRightsStatus: MetadataRightsStatus,
        val objectMediaRightsStatus: ObjectMediaRightsStatus,
        val privacyStatus: HistoricalMetadataPrivacyStatus,
        val availabilityStatus: HistoricalMetadataAvailabilityStatus,
        val containsUnclearedPersonalData: Boolean,
        val containsContradictorySourceData: Boolean,
    )

    private data class ParsedPrivacy(
        val status: HistoricalMetadataPrivacyStatus,
        val containsUnclearedPersonalData: Boolean,
    )

    private companion object {
        const val USER_AGENT = "HKH-Autopilot-HistoricalMetadata/1.0"
    }
}
