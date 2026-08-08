package nl.vdzon.hkh.externalverification

import java.sql.ResultSet
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

interface ExternalVerificationStore {
    fun create(
        localIdentifier: String,
        externalUri: String,
        matchedFields: List<String>,
        status: ExternalVerificationStatus,
        encryptedAccessToken: String?,
    ): ExternalVerificationRecord
}

@Repository
class ExternalVerificationRepository(private val jdbcTemplate: JdbcTemplate) : ExternalVerificationStore {

    override fun create(
        localIdentifier: String,
        externalUri: String,
        matchedFields: List<String>,
        status: ExternalVerificationStatus,
        encryptedAccessToken: String?,
    ): ExternalVerificationRecord = jdbcTemplate.query(
        """
        INSERT INTO external_verification (
            local_identifier, external_uri, matched_fields, status, encrypted_access_token
        )
        VALUES (?, ?, ?, ?, ?)
        RETURNING id, local_identifier, external_uri, matched_fields, status, checked_at
        """.trimIndent(),
        rowMapper,
        localIdentifier.trim(),
        externalUri,
        matchedFields.joinToString(","),
        status.name,
        encryptedAccessToken,
    ).single()

    private companion object {
        val rowMapper = RowMapper { result: ResultSet, _: Int ->
            ExternalVerificationRecord(
                id = result.getLong("id"),
                localIdentifier = result.getString("local_identifier"),
                externalUri = result.getString("external_uri"),
                matchedFields = result.getString("matched_fields")
                    .split(",")
                    .filter { it.isNotBlank() },
                status = result.getString("status"),
                checkedAt = result.getTimestamp("checked_at").toInstant(),
            )
        }
    }
}
