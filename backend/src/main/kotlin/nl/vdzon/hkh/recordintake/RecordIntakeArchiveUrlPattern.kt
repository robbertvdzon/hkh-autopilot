package nl.vdzon.hkh.recordintake

/** Herkende `adtid`/`guid`-verwijzing uit een archieven.nl-URI. */
data class ArchiveUrlReference(val adtid: String, val guid: String)

/**
 * Herkent of een opgegeven duurzame URL het patroon `http://opendata.archieven.nl/id/<adtid>/<guid>`
 * volgt. Volgt de URL het patroon niet, dan levert [parse] `null` op zonder dat er ooit een
 * netwerkaanroep wordt gedaan.
 */
object RecordIntakeArchiveUrlPattern {
    private val PATTERN = Regex("^http://opendata\\.archieven\\.nl/id/([^/]+)/([^/]+)$")

    fun parse(durableUrl: String?): ArchiveUrlReference? {
        val candidate = durableUrl?.trim().orEmpty()
        if (candidate.isEmpty()) return null
        val match = PATTERN.matchEntire(candidate) ?: return null
        return ArchiveUrlReference(adtid = match.groupValues[1], guid = match.groupValues[2])
    }
}
