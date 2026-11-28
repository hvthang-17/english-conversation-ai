ALTER TABLE learner_profiles
    ADD COLUMN avatar_url VARCHAR(100)
        GENERATED ALWAYS AS (CONCAT('/api/v1/profile/me/avatar/', avatar_storage_key)) STORED
        AFTER avatar_storage_key;
