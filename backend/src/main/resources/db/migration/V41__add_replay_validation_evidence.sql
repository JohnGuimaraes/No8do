ALTER TABLE replays
    ADD COLUMN validation_evidence jsonb;

ALTER TABLE replay_versions
    ADD COLUMN validation_evidence jsonb;
