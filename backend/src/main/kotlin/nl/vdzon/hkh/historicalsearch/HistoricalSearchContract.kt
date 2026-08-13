package nl.vdzon.hkh.historicalsearch

import java.net.URI
import java.time.Instant
import java.text.Normalizer
import java.util.Locale

enum class HistoricalSearchSource {
    EUROPEANA,
    OPEN_ARCHIEVEN,
}

enum class HistoricalTechnicalStatus {
    AVAILABLE,
    DISABLED,
    TEMPORARILY_UNAVAILABLE,
    INVALID_RESPONSE,
    TIMEOUT,
    HTTP_ERROR,
    INVALID_JSON,
    MISSING_REQUIRED_FIELDS,
}

enum class HistoricalRightsStatus {
    ALLOWED,
    RESTRICTED,
    UNKNOWN,
}

enum class HistoricalPrivacyStatus {
    CLEAR,
    BLOCKED,
    UNKNOWN,
}

enum class HistoricalSearchState {
    RESULTS,
    NO_RESULTS,
    PARTIAL_AVAILABILITY,
    SOURCE_FAILURE,
}

data class HistoricalSearchQuery(
    val text: String? = null,
    val place: String? = null,
    val person: String? = null,
    val event: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val source: HistoricalSearchSource? = null,
    val start: Int = 0,
    val limit: Int = 100,
)

data class HistoricalRelationshipSource(
    val name: String,
)

data class HistoricalRelationshipTarget(
    val name: String,
    val uri: String,
    val link: String,
)

/** A relationship explicitly asserted by the provider for this source record. */
data class HistoricalSearchRelationship(
    val type: String,
    val source: HistoricalRelationshipSource,
    val target: HistoricalRelationshipTarget,
)

data class HistoricalSearchResult(
    val source: HistoricalSearchSource,
    val sourceRecordId: String,
    val stableUrl: String,
    val title: String?,
    val description: String?,
    val place: String? = null,
    val person: String?,
    val event: String?,
    val dateStart: String?,
    val dateEnd: String?,
    val institution: String?,
    val rights: String?,
    val privacy: String?,
    val retrievedAt: Instant,
    val technicalStatus: HistoricalTechnicalStatus = HistoricalTechnicalStatus.AVAILABLE,
    val metadataRights: HistoricalRightsStatus = HistoricalRightsStatus.UNKNOWN,
    val objectMediaRights: HistoricalRightsStatus = HistoricalRightsStatus.UNKNOWN,
    val privacyStatus: HistoricalPrivacyStatus = HistoricalPrivacyStatus.UNKNOWN,
    val placeStatus: HistoricalContextStatus = if (place == null) HistoricalContextStatus.MISSING else HistoricalContextStatus.AVAILABLE,
    val personStatus: HistoricalContextStatus = if (person == null) HistoricalContextStatus.MISSING else HistoricalContextStatus.AVAILABLE,
    val eventStatus: HistoricalContextStatus = if (event == null) HistoricalContextStatus.MISSING else HistoricalContextStatus.AVAILABLE,
    val relationships: List<HistoricalSearchRelationship> = emptyList(),
    /** Provider-supplied normalized source name, when the provider exposes one. */
    val sourceName: String? = null,
    /** Full provider reference, for example `hee:uuid`, when available. */
    val stableIdentifier: String? = null,
    /** Provider-supplied original result URL; never constructed locally. */
    val originalSourceUrl: String? = null,
)

/**
 * Keeps the safe source reference while withholding content metadata unless both
 * metadata rights and privacy are explicitly safe.
 */
fun HistoricalSearchResult.failClosedMetadata(): HistoricalSearchResult =
    if (metadataRights == HistoricalRightsStatus.ALLOWED && privacyStatus == HistoricalPrivacyStatus.CLEAR) {
        this
    } else {
        copy(
            title = null,
            description = null,
            place = null,
            person = null,
            event = null,
            dateStart = null,
            dateEnd = null,
            institution = null,
            rights = null,
            privacy = null,
            placeStatus = HistoricalContextStatus.UNAVAILABLE,
            personStatus = HistoricalContextStatus.UNAVAILABLE,
            eventStatus = HistoricalContextStatus.UNAVAILABLE,
            relationships = emptyList(),
        )
    }

data class HistoricalSourceStatus(
    val source: HistoricalSearchSource,
    val status: HistoricalTechnicalStatus,
    val message: String? = null,
    /** Count of safely normalized results visible on the current result page. */
    val resultCount: Int? = null,
    /** Count of visible results with a certain, normalized Heemskerk place value. */
    val heemskerkCount: Int? = null,
)

fun HistoricalSearchResult.isHeemskerkPlaceIndicator(): Boolean =
    placeStatus == HistoricalContextStatus.AVAILABLE &&
        normalizeHistoricalPlace(place) == "heemskerk"

/** Normalizes only explicit place metadata for the local indication. */
fun normalizeHistoricalPlace(value: String?): String? {
    val normalized = value?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) } ?: return null
    val collapsed = buildString(normalized.length) {
        var whitespace = false
        normalized.forEach { character ->
            if (character.isWhitespace() || Character.isSpaceChar(character)) {
                whitespace = true
            } else {
                if (whitespace && isNotEmpty()) append(' ')
                append(character)
                whitespace = false
            }
        }
    }
    return collapsed.trim().lowercase(Locale.ROOT).takeIf(String::isNotEmpty)
}

object HistoricalSourceMessages {
    const val NOT_CONFIGURED = "Bron is niet geconfigureerd."
    const val TEMPORARILY_UNAVAILABLE = "Bron is tijdelijk niet beschikbaar."
    const val INVALID_RESPONSE = "Ongeldige bronrespons."
    const val OPEN_ARCHIEVEN_TIMEOUT = "Open Archieven reageerde niet op tijd."
    const val OPEN_ARCHIEVEN_HTTP_ERROR = "Open Archieven gaf een fout bij het opvragen."
    const val OPEN_ARCHIEVEN_INVALID_JSON = "Open Archieven stuurde een onleesbaar antwoord."
    const val OPEN_ARCHIEVEN_MISSING_REQUIRED_FIELDS = "Open Archieven stuurde een onvolledig antwoord."

    private val knownSafeMessages = setOf(
        "Europeana vereist een vrije zoekterm, persoon, plek of gebeurtenis.",
        "Europeana kon niet worden bevraagd.",
        "Europeana is niet geconfigureerd.",
        "Open Archieven vereist een zoekterm, persoon of gebeurtenis.",
        "Open Archieven kon niet worden bevraagd.",
        "Open Archieven retourneerde een foutrespons.",
        "Vervolgpagina niet beschikbaar.",
    )

    /** Keeps adapter diagnostics short and prevents provider payloads reaching the client. */
    fun safe(status: HistoricalTechnicalStatus, message: String?): String? = when (status) {
        HistoricalTechnicalStatus.AVAILABLE -> null
        HistoricalTechnicalStatus.DISABLED -> NOT_CONFIGURED
        HistoricalTechnicalStatus.TEMPORARILY_UNAVAILABLE ->
            message?.takeIf(knownSafeMessages::contains) ?: TEMPORARILY_UNAVAILABLE
        HistoricalTechnicalStatus.INVALID_RESPONSE ->
            message?.takeIf(knownSafeMessages::contains) ?: INVALID_RESPONSE
        HistoricalTechnicalStatus.TIMEOUT -> OPEN_ARCHIEVEN_TIMEOUT
        HistoricalTechnicalStatus.HTTP_ERROR -> OPEN_ARCHIEVEN_HTTP_ERROR
        HistoricalTechnicalStatus.INVALID_JSON -> OPEN_ARCHIEVEN_INVALID_JSON
        HistoricalTechnicalStatus.MISSING_REQUIRED_FIELDS -> OPEN_ARCHIEVEN_MISSING_REQUIRED_FIELDS
    }
}

data class HistoricalSearchPage(
    val source: HistoricalSearchSource,
    val results: List<HistoricalSearchResult>,
    val total: Int,
    val status: HistoricalTechnicalStatus,
    val message: String? = null,
    /** Number of provider records consumed by this page, including filtered records. */
    val consumed: Int = results.size,
)

interface HistoricalSearchAdapter {
    val source: HistoricalSearchSource

    fun search(query: HistoricalSearchQuery): HistoricalSearchPage
}

object HistoricalSearchValidation {
    private val YEAR = Regex("\\d{4}")

    fun normalize(
        text: String?,
        place: String?,
        person: String?,
        event: String?,
        fromYear: String?,
        toYear: String?,
        source: String?,
        start: Int,
        limit: Int,
    ): HistoricalSearchQuery {
        require(start >= 0) { "start moet nul of hoger zijn" }
        require(limit in 1..100) { "limit moet tussen 1 en 100 liggen" }
        val from = parseYear(fromYear, "vanafjaar")
        val to = parseYear(toYear, "eindjaar")
        require((from == null) == (to == null)) {
            "vanafjaar en eindjaar moeten samen worden opgegeven"
        }
        require(from == null || to == null || from <= to) {
            "vanafjaar mag niet na eindjaar liggen"
        }
        val parsedSource = source?.trim()?.takeIf(String::isNotEmpty)?.let {
            when (it.uppercase().replace('-', '_').replace(' ', '_')) {
                "EUROPEANA" -> HistoricalSearchSource.EUROPEANA
                "OPEN_ARCHIEVEN", "OPENARCHIEVEN" -> HistoricalSearchSource.OPEN_ARCHIEVEN
                else -> throw IllegalArgumentException("onbekende bronkeuze")
            }
        }
        return HistoricalSearchQuery(
            text = clean(text),
            place = clean(place),
            person = clean(person),
            event = clean(event),
            fromYear = from,
            toYear = to,
            source = parsedSource,
            start = start,
            limit = limit,
        )
    }

    private fun parseYear(value: String?, field: String): Int? {
        val cleaned = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        require(YEAR.matches(cleaned)) { "$field moet een viercijferig jaar zijn" }
        return cleaned.toInt()
    }

    private fun clean(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)
}

fun String?.asSafeText(maxLength: Int = 2_000): String? = this?.trim()?.takeIf {
    it.isNotEmpty() && it.length <= maxLength && it.none(Char::isISOControl)
}

fun String?.asHttpUrl(): String? = asSafeText(2_000)?.takeIf {
    runCatching { URI(it) }.getOrNull()?.let { uri ->
        uri.isAbsolute && (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            !uri.host.isNullOrBlank()
    } == true
}
