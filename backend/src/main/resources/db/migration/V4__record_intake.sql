CREATE TABLE record_intake (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    local_identifier VARCHAR(320) NOT NULL,
    title VARCHAR(500),
    description TEXT,
    dating VARCHAR(320) NOT NULL,
    provenance VARCHAR(500) NOT NULL,
    rights_status VARCHAR(320) NOT NULL,
    privacy_classification VARCHAR(64) NOT NULL,
    access_url TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'intern_concept',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT record_intake_local_identifier_not_blank CHECK (LENGTH(TRIM(local_identifier)) > 0),
    CONSTRAINT record_intake_title_or_description CHECK (
        LENGTH(TRIM(COALESCE(title, ''))) > 0 OR LENGTH(TRIM(COALESCE(description, ''))) > 0
    ),
    CONSTRAINT record_intake_status_check CHECK (status = 'intern_concept'),
    CONSTRAINT record_intake_privacy_classification_check CHECK (privacy_classification = 'geen persoonsgegevens')
);

CREATE TABLE record_intake_external_link (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    record_intake_id BIGINT NOT NULL REFERENCES record_intake (id),
    durable_url TEXT NOT NULL,
    link_rationale TEXT NOT NULL,
    uncertainty VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'concept',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT record_intake_external_link_status_check CHECK (status = 'concept'),
    CONSTRAINT record_intake_external_link_uncertainty_check CHECK (uncertainty IN ('laag', 'middel', 'hoog')),
    CONSTRAINT record_intake_external_link_unique UNIQUE (record_intake_id)
);
