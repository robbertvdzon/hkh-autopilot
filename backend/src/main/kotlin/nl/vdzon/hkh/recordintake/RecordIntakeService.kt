package nl.vdzon.hkh.recordintake

import java.time.Clock
import java.time.Instant
import nl.vdzon.hkh.externalverification.ArchiveFetchResult
import nl.vdzon.hkh.externalverification.ArchiveRecordFields
import nl.vdzon.hkh.externalverification.ArchivesNlClient
import nl.vdzon.hkh.externalverification.archivesNlUri
import nl.vdzon.hkh.privacyclassification.GenealogicalRecord
import nl.vdzon.hkh.privacyclassification.LivingNextOfKinFields
import nl.vdzon.hkh.privacyclassification.PrivacyClassifier
import org.springframework.stereotype.Service

data class RecordIntakeCreationResult(
    val record: RecordIntakeRecord,
    val externalLink: RecordIntakeExternalLink?,
    val externalArchiveOutcome: RecordIntakeExternalArchiveOutcome?,
)

/** Wat daadwerkelijk in `record_intake` wordt opgeslagen voor de externe archiefbron. */
data class RecordIntakeExternalArchiveDataToStore(
    val name: String?,
    val birthDate: String?,
    val deathDate: String?,
    val license: String?,
    val sourceUri: String?,
    val fetchedAt: Instant?,
)

/**
 * Uitkomst van de externe-archiefdatabeoordeling, naar het patroon van
 * `PrivacyClassificationResult`: [stored] geeft aan of naam/geboorte-/sterftedatum daadwerkelijk
 * opgeslagen zijn, [reason] is altijd een niet-lege, leesbare toelichting. [license]/[sourceUri]/
 * [fetchedAt] zijn niet-persoonsgebonden en gezet zodra de externe bron succesvol bevraagd is,
 * ongeacht [stored]. [name]/[birthDate]/[deathDate] zijn de opgehaalde kernvelden en zijn alleen
 * relevant wanneer [stored] `true` is.
 */
data class RecordIntakeExternalArchiveOutcome(
    val stored: Boolean,
    val reason: String,
    val license: String?,
    val sourceUri: String?,
    val fetchedAt: Instant?,
    val name: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
)

/** Vaste, leesbare redenen voor de uitkomst van de externe-archiefdatabeoordeling. */
object RecordIntakeExternalArchiveReasons {
    const val FETCH_FAILED =
        "Externe brongegevens konden niet worden opgehaald: de koppeling volgt niet het " +
            "archieven.nl-patroon, er is geen match of het archiefendpoint is niet bereikbaar."
    const val BOTH_PROCESSABLE =
        "Lokale en externe classificatie zijn beide verwerkbaar; naam, geboortedatum en " +
            "sterftedatum zijn opgeslagen."

    fun localBlocked(localReason: String): String =
        "Lokale classificatie is geblokkeerd ($localReason); naam, geboortedatum en sterftedatum " +
            "zijn niet opgeslagen."

    fun externalBlocked(externalReason: String): String =
        "Externe classificatie is geblokkeerd ($externalReason); naam, geboortedatum en " +
            "sterftedatum zijn niet opgeslagen."
}

/**
 * Slaat een gevalideerd intakeverzoek op als intern concept en maakt de externe conceptkoppeling
 * alleen aan wanneer die volledig geldig is. Deze service veronderstelt dat de aanroeper het
 * verzoek al met [RecordIntakeValidator] heeft goedgekeurd: er wordt hier niet opnieuw op
 * verplichte velden of de privacyregel gecontroleerd.
 *
 * Wanneer [RecordIntake.confirmExternalArchiveData] `true` is en `externalLink.durableUrl` het
 * archieven.nl-patroon volgt, wordt de externe bron hier servergezijdig opnieuw bevraagd (de door
 * de client eerder getoonde preview-data wordt nooit vertrouwd of hergebruikt). Naam, geboorte- en
 * sterftedatum worden alleen opgeslagen wanneer zowel de lokale als de externe
 * [PrivacyClassifier]-uitkomst `PROCESSABLE` is; licentie, bron-URI en ophaaldatum worden bij een
 * geslaagde bevraging altijd opgeslagen, ongeacht die classificatie-uitkomst. De ruwe externe
 * respons wordt nooit opgeslagen.
 */
@Service
class RecordIntakeService(
    private val store: RecordIntakeStore,
    private val validator: RecordIntakeValidator,
    private val archivesNlClient: ArchivesNlClient,
    private val privacyClassifier: PrivacyClassifier = PrivacyClassifier(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(intake: RecordIntake): RecordIntakeCreationResult {
        val outcome = resolveExternalArchiveData(intake)
        val record = store.create(intake, outcome?.toDataToStore())
        val externalLink = intake.externalLink
            ?.takeIf { validator.isValidExternalLink(it) }
            ?.let { store.createExternalLink(record.id, it) }

        return RecordIntakeCreationResult(record, externalLink, outcome)
    }

    private fun RecordIntakeExternalArchiveOutcome.toDataToStore(): RecordIntakeExternalArchiveDataToStore? {
        if (license == null && sourceUri == null && fetchedAt == null) return null
        return RecordIntakeExternalArchiveDataToStore(
            name = if (stored) name else null,
            birthDate = if (stored) birthDate else null,
            deathDate = if (stored) deathDate else null,
            license = license,
            sourceUri = sourceUri,
            fetchedAt = fetchedAt,
        )
    }

    private fun resolveExternalArchiveData(intake: RecordIntake): RecordIntakeExternalArchiveOutcome? {
        if (intake.confirmExternalArchiveData != true) return null

        val reference = RecordIntakeArchiveUrlPattern.parse(intake.externalLink?.durableUrl)
            ?: return failedOutcome()

        val fetchResult = runCatching { archivesNlClient.fetch(reference.adtid, reference.guid, null) }
            .getOrElse { ArchiveFetchResult.NotFound }
        val found = fetchResult as? ArchiveFetchResult.Found ?: return failedOutcome()

        val localResult = privacyClassifier.classify(buildLocalGenealogicalRecord(intake))
        val externalResult = privacyClassifier.classify(buildExternalGenealogicalRecord(found.fields))

        val stored = localResult.processable && externalResult.processable
        val reason = when {
            stored -> RecordIntakeExternalArchiveReasons.BOTH_PROCESSABLE
            !localResult.processable -> RecordIntakeExternalArchiveReasons.localBlocked(localResult.reason)
            else -> RecordIntakeExternalArchiveReasons.externalBlocked(externalResult.reason)
        }

        return RecordIntakeExternalArchiveOutcome(
            stored = stored,
            reason = reason,
            license = found.fields.license,
            sourceUri = archivesNlUri(reference.adtid, reference.guid),
            fetchedAt = Instant.now(clock),
            name = found.fields.name,
            birthDate = found.fields.birthDate,
            deathDate = found.fields.deathDate,
        )
    }

    private fun failedOutcome(): RecordIntakeExternalArchiveOutcome = RecordIntakeExternalArchiveOutcome(
        stored = false,
        reason = RecordIntakeExternalArchiveReasons.FETCH_FAILED,
        license = null,
        sourceUri = null,
        fetchedAt = null,
    )

    private fun buildLocalGenealogicalRecord(intake: RecordIntake): GenealogicalRecord = GenealogicalRecord(
        deceasedStatus = intake.deceasedStatus,
        nextOfKin = LivingNextOfKinFields(contactName = if (intake.nextOfKinConfirmed == true) "bevestigd" else null),
    )

    private fun buildExternalGenealogicalRecord(fields: ArchiveRecordFields): GenealogicalRecord = GenealogicalRecord(
        deceasedStatus = if (fields.deathDate.isNullOrBlank()) null else "overleden",
    )
}
