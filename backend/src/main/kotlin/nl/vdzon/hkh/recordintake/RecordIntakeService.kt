package nl.vdzon.hkh.recordintake

import org.springframework.stereotype.Service

data class RecordIntakeCreationResult(
    val record: RecordIntakeRecord,
    val externalLink: RecordIntakeExternalLink?,
)

/**
 * Slaat een gevalideerd intakeverzoek op als intern concept en maakt de externe conceptkoppeling
 * alleen aan wanneer die volledig geldig is. Deze service veronderstelt dat de aanroeper het
 * verzoek al met [RecordIntakeValidator] heeft goedgekeurd: er wordt hier niet opnieuw op
 * verplichte velden of de privacyregel gecontroleerd.
 */
@Service
class RecordIntakeService(
    private val store: RecordIntakeStore,
    private val validator: RecordIntakeValidator,
) {
    fun create(intake: RecordIntake): RecordIntakeCreationResult {
        val record = store.create(intake)
        val externalLink = intake.externalLink
            ?.takeIf { validator.isValidExternalLink(it) }
            ?.let { store.createExternalLink(record.id, it) }
        return RecordIntakeCreationResult(record, externalLink)
    }
}
