package nl.vdzon.hkh.recordintake

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import nl.vdzon.hkh.privacyclassification.DeceasedStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

interface RecordIntakeStore {
    fun create(intake: RecordIntake, archiveData: RecordIntakeExternalArchiveDataToStore?): RecordIntakeRecord
    fun createExternalLink(recordIntakeId: Long, link: RecordIntakeExternalLinkInput): RecordIntakeExternalLink

    /** Levert het meest recente record voor deze `localIdentifier` op, of `null` als dat er geen is. */
    fun findByLocalIdentifier(localIdentifier: String): RecordIntakeRecord?

    /**
     * Zet `confirmedBy`/`confirmedAt` op het meest recente record voor deze `localIdentifier`.
     * Levert `null` op wanneer er geen record met deze `localIdentifier` bestaat.
     */
    fun confirm(localIdentifier: String, confirmedBy: String, confirmedAt: Instant): RecordIntakeRecord?
}

@Repository
class RecordIntakeRepository(private val jdbcTemplate: JdbcTemplate) : RecordIntakeStore {
    override fun create(
        intake: RecordIntake,
        archiveData: RecordIntakeExternalArchiveDataToStore?,
    ): RecordIntakeRecord = jdbcTemplate.query(
        """
        INSERT INTO record_intake (
            local_identifier, title, description, dating, provenance, rights_status,
            privacy_classification, access_url, deceased_status, next_of_kin_confirmed,
            archive_name, archive_birth_date, archive_death_date, archive_license,
            archive_source_uri, archive_fetched_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING id, local_identifier, status, created_at, deceased_status, next_of_kin_confirmed,
            archive_name, archive_birth_date, archive_death_date, archive_license,
            archive_source_uri, archive_fetched_at, confirmed_by, confirmed_at
        """.trimIndent(),
        recordRowMapper,
        intake.localIdentifier?.trim(),
        intake.title?.trim(),
        intake.description?.trim(),
        intake.dating?.trim(),
        intake.provenance?.trim(),
        intake.rightsStatus?.trim(),
        intake.privacyClassification?.trim(),
        intake.accessUrl?.trim(),
        DeceasedStatus.parse(intake.deceasedStatus).wireValue,
        intake.nextOfKinConfirmed,
        archiveData?.name,
        archiveData?.birthDate,
        archiveData?.deathDate,
        archiveData?.license,
        archiveData?.sourceUri,
        archiveData?.fetchedAt?.let { Timestamp.from(it) },
    ).single()

    override fun createExternalLink(
        recordIntakeId: Long,
        link: RecordIntakeExternalLinkInput,
    ): RecordIntakeExternalLink = jdbcTemplate.query(
        """
        INSERT INTO record_intake_external_link (record_intake_id, durable_url, link_rationale, uncertainty)
        VALUES (?, ?, ?, ?)
        RETURNING id, record_intake_id, status, created_at
        """.trimIndent(),
        externalLinkRowMapper,
        recordIntakeId,
        link.durableUrl?.trim(),
        link.linkRationale?.trim(),
        link.uncertainty?.trim()?.lowercase(),
    ).single()

    override fun findByLocalIdentifier(localIdentifier: String): RecordIntakeRecord? = jdbcTemplate.query(
        """
        SELECT id, local_identifier, status, created_at, deceased_status, next_of_kin_confirmed,
            archive_name, archive_birth_date, archive_death_date, archive_license,
            archive_source_uri, archive_fetched_at, confirmed_by, confirmed_at
        FROM record_intake
        WHERE local_identifier = ?
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        """.trimIndent(),
        recordRowMapper,
        localIdentifier.trim(),
    ).singleOrNull()

    override fun confirm(localIdentifier: String, confirmedBy: String, confirmedAt: Instant): RecordIntakeRecord? = jdbcTemplate.query(
        """
        UPDATE record_intake
        SET confirmed_by = ?, confirmed_at = ?
        WHERE id = (
            SELECT id FROM record_intake WHERE local_identifier = ? ORDER BY created_at DESC, id DESC LIMIT 1
        )
        RETURNING id, local_identifier, status, created_at, deceased_status, next_of_kin_confirmed,
            archive_name, archive_birth_date, archive_death_date, archive_license,
            archive_source_uri, archive_fetched_at, confirmed_by, confirmed_at
        """.trimIndent(),
        recordRowMapper,
        confirmedBy.trim(),
        Timestamp.from(confirmedAt),
        localIdentifier.trim(),
    ).singleOrNull()

    private companion object {
        val recordRowMapper = RowMapper { result: ResultSet, _: Int ->
            RecordIntakeRecord(
                id = result.getLong("id"),
                localIdentifier = result.getString("local_identifier"),
                status = result.getString("status"),
                createdAt = result.getTimestamp("created_at").toInstant(),
                deceasedStatus = result.getString("deceased_status"),
                nextOfKinConfirmed = result.getObject("next_of_kin_confirmed") as Boolean?,
                archiveName = result.getString("archive_name"),
                archiveBirthDate = result.getString("archive_birth_date"),
                archiveDeathDate = result.getString("archive_death_date"),
                archiveLicense = result.getString("archive_license"),
                archiveSourceUri = result.getString("archive_source_uri"),
                archiveFetchedAt = result.getTimestamp("archive_fetched_at")?.toInstant(),
                confirmedBy = result.getString("confirmed_by"),
                confirmedAt = result.getTimestamp("confirmed_at")?.toInstant(),
            )
        }
        val externalLinkRowMapper = RowMapper { result: ResultSet, _: Int ->
            RecordIntakeExternalLink(
                id = result.getLong("id"),
                recordIntakeId = result.getLong("record_intake_id"),
                status = result.getString("status"),
                createdAt = result.getTimestamp("created_at").toInstant(),
            )
        }
    }
}
