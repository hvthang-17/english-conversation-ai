ALTER TABLE audio_submissions
  ADD COLUMN assessment_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN assessment_error_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD INDEX ix_voice_assessment_pending (assessment_status,id),
  ADD CONSTRAINT ck_voice_assessment_status CHECK (
    assessment_status IN ('PENDING_PREPARATION','PREPARED','SKIPPED','FAILED')
  );
