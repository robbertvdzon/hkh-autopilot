package nl.vdzon.hkh.externalverification

/**
 * Eén ruw, ongevalideerd verificatieverzoek voor precies één lokaal genealogisch record.
 *
 * De velden zijn met opzet als ruwe tekst gemodelleerd, naar het patroon van `RecordIntake`: zo kan
 * een verzoek zowel een ontbrekende (`null`) als een niet-herkende waarde bevatten zonder dat
 * constructie of deserialisatie faalt; de validator keurt zulke waarden af in plaats van te klappen.
 * Dit record is bewust niet gekoppeld aan een bestaand persistent record (`recordintake` of
 * `privacyclassification`).
 */
data class ExternalVerificationRequest(
    val localIdentifier: String? = null,
    val name: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val adtid: String? = null,
    val guid: String? = null,
    /**
     * Optioneel toegangstoken. Alleen relevant wanneer het archiefendpoint expliciet een token
     * eist; wordt versleuteld opgeslagen en nooit in leesbare vorm teruggegeven of gelogd.
     */
    val accessToken: String? = null,
)

/** De resolvebare archieven.nl-URI die uit `adtid` en `guid` wordt opgebouwd. */
fun archivesNlUri(adtid: String, guid: String): String = "http://opendata.archieven.nl/id/$adtid/$guid"
