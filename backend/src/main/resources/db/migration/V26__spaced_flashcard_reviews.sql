ALTER TABLE review_items
    ADD COLUMN spaced_interval_days SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN schedule_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN first_spaced_reviewed_at DATETIME(6) NULL,
    ADD INDEX ix_review_cards_due (user_id, next_review_at, id),
    ADD INDEX ix_review_cards_first_spaced (user_id, first_spaced_reviewed_at, created_at, id),
    ADD CONSTRAINT ck_review_interval_days CHECK (spaced_interval_days BETWEEN 0 AND 180);

ALTER TABLE review_attempts
    DROP CHECK ck_review_attempt_mode,
    ADD COLUMN rating VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN algorithm_version VARCHAR(24) NULL,
    ADD COLUMN schedule_version_after BIGINT NULL,
    ADD COLUMN interval_days_after SMALLINT NULL,
    ADD COLUMN next_review_at_after DATETIME(6) NULL,
    ADD CONSTRAINT ck_review_attempt_mode CHECK (
      ((review_mode = 'FLASHCARD' AND is_correct IS NULL)
         OR (review_mode = 'QUICK_QUIZ' AND is_correct IS NOT NULL))
        AND rating IS NULL AND algorithm_version IS NULL AND schedule_version_after IS NULL
        AND interval_days_after IS NULL AND next_review_at_after IS NULL
      OR review_mode = 'SPACED_REVIEW' AND is_correct IS NULL AND rating IS NOT NULL
        AND rating IN ('AGAIN','HARD','GOOD','EASY') AND algorithm_version IS NOT NULL
        AND schedule_version_after IS NOT NULL AND schedule_version_after > 0
        AND interval_days_after IS NOT NULL AND interval_days_after BETWEEN 0 AND 180
        AND next_review_at_after IS NOT NULL
    );
