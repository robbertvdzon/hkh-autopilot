ALTER TABLE record_intake
    ADD COLUMN deceased_status VARCHAR(32),
    ADD COLUMN next_of_kin_confirmed BOOLEAN,
    ADD COLUMN archive_name TEXT,
    ADD COLUMN archive_birth_date VARCHAR(64),
    ADD COLUMN archive_death_date VARCHAR(64),
    ADD COLUMN archive_license VARCHAR(128),
    ADD COLUMN archive_source_uri TEXT,
    ADD COLUMN archive_fetched_at TIMESTAMPTZ;
