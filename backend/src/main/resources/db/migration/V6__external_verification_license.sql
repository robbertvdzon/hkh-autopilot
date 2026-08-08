ALTER TABLE external_verification
    ADD COLUMN license_status VARCHAR(32) NOT NULL DEFAULT 'LICENSE_UNKNOWN',
    ADD COLUMN license_value TEXT,
    ADD COLUMN license_checked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE external_verification
    ADD CONSTRAINT external_verification_license_status_check
        CHECK (license_status IN ('LICENSE_KNOWN', 'LICENSE_UNKNOWN')),
    ADD CONSTRAINT external_verification_license_value_consistency_check
        CHECK (
            (license_status = 'LICENSE_KNOWN' AND LENGTH(TRIM(license_value)) > 0)
            OR (license_status = 'LICENSE_UNKNOWN' AND license_value IS NULL)
        );
