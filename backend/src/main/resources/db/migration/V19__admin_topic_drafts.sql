ALTER TABLE topics
    ADD COLUMN admin_draft BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_topics_admin_draft CHECK (NOT admin_draft OR NOT is_active);
