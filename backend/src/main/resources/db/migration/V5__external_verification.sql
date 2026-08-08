CREATE TABLE external_verification (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    local_identifier VARCHAR(320) NOT NULL,
    external_uri TEXT NOT NULL,
    matched_fields TEXT NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    encrypted_access_token TEXT,
    CONSTRAINT external_verification_local_identifier_not_blank CHECK (LENGTH(TRIM(local_identifier)) > 0),
    CONSTRAINT external_verification_external_uri_not_blank CHECK (LENGTH(TRIM(external_uri)) > 0),
    CONSTRAINT external_verification_status_check CHECK (status IN ('VERIFIED', 'UNVERIFIED'))
);
