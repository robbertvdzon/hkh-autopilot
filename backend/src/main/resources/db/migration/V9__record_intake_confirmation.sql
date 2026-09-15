ALTER TABLE record_intake
    ADD COLUMN confirmed_by VARCHAR(320),
    ADD COLUMN confirmed_at TIMESTAMPTZ;
