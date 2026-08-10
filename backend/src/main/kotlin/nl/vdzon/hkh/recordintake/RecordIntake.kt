package nl.vdzon.hkh.recordintake

/**
 * Eén ruw, ongevalideerd intakeverzoek voor precies één lokaal collectierecord.
 *
 * Gecontroleerde waarden zijn met opzet als ruwe tekst gemodelleerd. Zo kan een verzoek zowel een
 * ontbrekende (`null`) als een niet-herkende waarde bevatten zonder dat constructie of
 * deserialisatie faalt; de validator keurt zulke waarden af in plaats van te klappen.
 */
data class RecordIntake(
    val localIdentifier: String? = null,
    val title: String? = null,
    val description: String? = null,
    val dating: String? = null,
    val provenance: String? = null,
    val rightsStatus: String? = null,
    val privacyClassification: String? = null,
    val accessUrl: String? = null,
    val externalLink: RecordIntakeExternalLinkInput? = null,
    /** Overlijdensstatus van de hoofdpersoon; fail-closed `ONBEKEND` zonder expliciete invoer. */
    val deceasedStatus: String? = null,
    /** Bevestigt of het record gegevens van een nog levende nabestaande bevat. */
    val nextOfKinConfirmed: Boolean? = null,
    /**
     * Vraagt op bij opslaan een servergezijdige herhaalde bevraging van `externalLink.durableUrl`
     * aan; de eventueel eerder door de client getoonde preview-data wordt hiervoor nooit vertrouwd.
     */
    val confirmExternalArchiveData: Boolean? = null,
)

/** Optionele koppeling naar een extern archiefrecord. Alleen geldig als alle drie de velden geldig zijn. */
data class RecordIntakeExternalLinkInput(
    val durableUrl: String? = null,
    val linkRationale: String? = null,
    val uncertainty: String? = null,
)

/** Gecontroleerde waarden voor de privacyclassificatie. Alleen `GEEN_PERSOONSGEGEVENS` wordt toegelaten. */
enum class PrivacyClassification(val wireValue: String) {
    GEEN_PERSOONSGEGEVENS("geen persoonsgegevens"),
    MOGELIJK_PERSOONSGEGEVENS("mogelijk persoonsgegevens"),
    PERSOONSGEGEVENS("persoonsgegevens"),
    ;

    companion object {
        fun parse(raw: String?): PrivacyClassification? = entries.firstOrNull { it.wireValue == raw.normalize() }
    }
}

/** Gecontroleerde waarden voor de onzekerheid van de externe conceptkoppeling. */
enum class LinkUncertainty(val wireValue: String) {
    LAAG("laag"),
    MIDDEL("middel"),
    HOOG("hoog"),
    ;

    companion object {
        fun parse(raw: String?): LinkUncertainty? = entries.firstOrNull { it.wireValue == raw.normalize() }
    }
}

private fun String?.normalize(): String? = this?.trim()?.lowercase()
