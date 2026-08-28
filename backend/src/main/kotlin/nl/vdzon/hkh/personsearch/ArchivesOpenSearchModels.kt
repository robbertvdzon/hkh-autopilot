package nl.vdzon.hkh.personsearch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesSearchResultDto(
    @param:JsonProperty("archive_code") val archiveCode: String? = null,
    @param:JsonProperty("identifier") val identifier: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesSearchResponseDto(
    @param:JsonProperty("number_found") val numberFound: Int? = null,
    @param:JsonProperty("results") val results: List<ArchivesSearchResultDto>? = null,
    @param:JsonProperty("error_code") val errorCode: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesPersonDto(@param:JsonProperty("name") val name: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesEventDto(
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("date") val date: String? = null,
    @param:JsonProperty("place") val place: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesRelationDto(
    @param:JsonProperty("role") val role: String? = null,
    @param:JsonProperty("person") val person: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesSourceDto(
    @param:JsonProperty("institution") val institution: String? = null,
    @param:JsonProperty("source_type") val sourceType: String? = null,
    @param:JsonProperty("archive_number") val archiveNumber: String? = null,
    @param:JsonProperty("register_number") val registerNumber: String? = null,
    @param:JsonProperty("deed_number") val deedNumber: String? = null,
    @param:JsonProperty("record_number") val recordNumber: String? = null,
    @param:JsonProperty("digital_original_url") val digitalOriginalUrl: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesShowResponseDto(
    @param:JsonProperty("person") val person: ArchivesPersonDto? = null,
    @param:JsonProperty("event") val event: ArchivesEventDto? = null,
    @param:JsonProperty("relationEP") val relationEP: List<ArchivesRelationDto>? = null,
    @param:JsonProperty("source") val source: ArchivesSourceDto? = null,
    @param:JsonProperty("error_code") val errorCode: String? = null,
)

/** Eén gededupliceerd kandidaatrecord (op `archive_code` + `identifier`) uit Records/Search. */
data class ArchivesSearchResultItem(val archiveCode: String, val identifier: String)

sealed interface ArchivesSearchOutcome {
    data class Success(val numberFound: Int, val results: List<ArchivesSearchResultItem>) : ArchivesSearchOutcome
    data object Failure : ArchivesSearchOutcome
}

/** Eén gevalideerd Show-record; alleen deze velden mogen een feitelijke antwoordzin dragen. */
data class ArchivesShowRecord(
    val archiveCode: String,
    val identifier: String,
    val personName: String,
    val eventType: String,
    val eventDate: String,
    val eventPlace: String,
    val relations: List<ArchivesRelation>,
    val institution: String,
    val sourceType: String,
    val archiveNumber: String?,
    val registerNumber: String?,
    val deedNumber: String?,
    val recordNumber: String,
    val digitalOriginalUrl: String?,
)

data class ArchivesRelation(val role: String, val personName: String)

sealed interface ArchivesShowOutcome {
    data class Success(val record: ArchivesShowRecord) : ArchivesShowOutcome
    data object Failure : ArchivesShowOutcome
}

/** Dedupliceert op `archive_code` + `identifier`, met behoud van eerste-voorkomen-volgorde. */
fun List<ArchivesSearchResultItem>.deduplicated(): List<ArchivesSearchResultItem> {
    val seen = LinkedHashSet<Pair<String, String>>()
    val result = mutableListOf<ArchivesSearchResultItem>()
    for (item in this) {
        val key = item.archiveCode to item.identifier
        if (seen.add(key)) result.add(item)
    }
    return result
}
