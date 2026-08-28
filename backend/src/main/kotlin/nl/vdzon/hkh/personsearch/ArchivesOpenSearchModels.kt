package nl.vdzon.hkh.personsearch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// Reële, geneste api.openarchieven.nl/1.1-responsvorm (geverifieerd via live aanroep), niet het
// zelfbedachte platte schema waarmee dit bestand oorspronkelijk was geschreven.

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesSearchDocDto(
    @param:JsonProperty("archive_code") val archiveCode: String? = null,
    @param:JsonProperty("identifier") val identifier: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesSearchResponseBodyDto(
    @param:JsonProperty("number_found") val numberFound: Int? = null,
    @param:JsonProperty("docs") val docs: List<ArchivesSearchDocDto>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesSearchResponseDto(
    @param:JsonProperty("response") val response: ArchivesSearchResponseBodyDto? = null,
    @param:JsonProperty("error_code") val errorCode: Any? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesPersonNameDto(
    @param:JsonProperty("PersonNameFirstName") val firstName: String? = null,
    @param:JsonProperty("PersonNameLastName") val lastName: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesPersonDto(
    @param:JsonProperty("@pid") val pid: String? = null,
    @param:JsonProperty("PersonName") val personName: ArchivesPersonNameDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesEventDateDto(
    @param:JsonProperty("Year") val year: String? = null,
    @param:JsonProperty("Month") val month: String? = null,
    @param:JsonProperty("Day") val day: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesEventPlaceDto(@param:JsonProperty("Place") val place: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesEventDto(
    @param:JsonProperty("@eid") val eid: String? = null,
    @param:JsonProperty("EventType") val type: String? = null,
    @param:JsonProperty("EventDate") val date: ArchivesEventDateDto? = null,
    @param:JsonProperty("EventPlace") val place: ArchivesEventPlaceDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesRelationDto(
    @param:JsonProperty("PersonKeyRef") val personKeyRef: String? = null,
    @param:JsonProperty("EventKeyRef") val eventKeyRef: String? = null,
    @param:JsonProperty("RelationType") val relationType: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesSourceReferenceDto(
    @param:JsonProperty("InstitutionName") val institutionName: String? = null,
    @param:JsonProperty("Archive") val archive: String? = null,
    @param:JsonProperty("RegistryNumber") val registryNumber: String? = null,
    @param:JsonProperty("DocumentNumber") val documentNumber: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesSourceDto(
    @param:JsonProperty("SourceType") val sourceType: String? = null,
    @param:JsonProperty("SourceReference") val sourceReference: ArchivesSourceReferenceDto? = null,
    @param:JsonProperty("RecordIdentifier") val recordIdentifier: String? = null,
    @param:JsonProperty("SourceDigitalOriginal") val sourceDigitalOriginal: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArchivesShowResponseDto(
    @param:JsonProperty("Person") val person: List<ArchivesPersonDto>? = null,
    @param:JsonProperty("Event") val event: ArchivesEventDto? = null,
    @param:JsonProperty("RelationEP") val relationEP: List<ArchivesRelationDto>? = null,
    @param:JsonProperty("Source") val source: ArchivesSourceDto? = null,
    @param:JsonProperty("error_code") val errorCode: Any? = null,
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
