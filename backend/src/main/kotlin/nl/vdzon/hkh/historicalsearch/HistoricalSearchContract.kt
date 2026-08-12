package nl.vdzon.hkh.historicalsearch

import java.net.URI
import java.time.Instant

enum class HistoricalSearchSource {
    EUROPEANA,
    OPEN_ARCHIEVEN,
}

enum class HistoricalTechnicalStatus {
    AVAILABLE,
    DISABLED,
    TEMPORARILY_UNAVAILABLE,
    INVALID_RESPONSE,
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

data class HistoricalSearchResult(
    val source: HistoricalSearchSource,
    val sourceRecordId: String,
    val stableUrl: String,
    val title: String?,
    val description: String?,
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
)

data class HistoricalSourceStatus(
    val source: HistoricalSearchSource,
    val status: HistoricalTechnicalStatus,
    val message: String? = null,
)

data class HistoricalSearchPage(
    val source: HistoricalSearchSource,
    val results: List<HistoricalSearchResult>,
    val total: Int,
    val status: HistoricalTechnicalStatus,
    val message: String? = null,
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
