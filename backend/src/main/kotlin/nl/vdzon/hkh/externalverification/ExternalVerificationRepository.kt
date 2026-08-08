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
        licenseStatus: ExternalVerificationLicenseStatus,
        licenseValue: String?,
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
        licenseStatus: ExternalVerificationLicenseStatus,
        licenseValue: String?,
    ): ExternalVerificationRecord = jdbcTemplate.query(
        """
        INSERT INTO external_verification (
            local_identifier, external_uri, matched_fields, status, encrypted_access_token,
            license_status, license_value
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        RETURNING id, local_identifier, external_uri, matched_fields, status, checked_at,
            license_status, license_value, license_checked_at
        """.trimIndent(),
        rowMapper,
        localIdentifier.trim(),
        externalUri,
        matchedFields.joinToString(","),
        status.name,
        encryptedAccessToken,
        licenseStatus.name,
        licenseValue,
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
                licenseStatus = result.getString("license_status"),
                licenseValue = result.getString("license_value"),
                licenseCheckedAt = result.getTimestamp("license_checked_at").toInstant(),
            )
        }
    }
}
