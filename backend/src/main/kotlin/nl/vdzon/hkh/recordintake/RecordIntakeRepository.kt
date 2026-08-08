package nl.vdzon.hkh.recordintake

import java.sql.ResultSet
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

interface RecordIntakeStore {
    fun create(intake: RecordIntake): RecordIntakeRecord
    fun createExternalLink(recordIntakeId: Long, link: RecordIntakeExternalLinkInput): RecordIntakeExternalLink
}

@Repository
class RecordIntakeRepository(private val jdbcTemplate: JdbcTemplate) : RecordIntakeStore {
    override fun create(intake: RecordIntake): RecordIntakeRecord = jdbcTemplate.query(
        """
        INSERT INTO record_intake (
            local_identifier, title, description, dating, provenance, rights_status,
            privacy_classification, access_url
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING id, local_identifier, status, created_at
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

    private companion object {
        val recordRowMapper = RowMapper { result: ResultSet, _: Int ->
            RecordIntakeRecord(
                id = result.getLong("id"),
                localIdentifier = result.getString("local_identifier"),
                status = result.getString("status"),
                createdAt = result.getTimestamp("created_at").toInstant(),
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
