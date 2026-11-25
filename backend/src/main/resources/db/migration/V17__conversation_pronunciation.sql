ALTER TABLE pronunciation_attempts
 DROP CHECK ck_pronunciation_mode,
 MODIFY drill_id BIGINT NULL,
 MODIFY drill_mode VARCHAR(20) NULL,
 MODIFY reference_text_snapshot TEXT NULL,
 ADD COLUMN audio_submission_id BIGINT NULL,
 ADD UNIQUE KEY uq_pronunciation_voice (audio_submission_id),
 ADD CONSTRAINT fk_pronunciation_voice FOREIGN KEY (audio_submission_id) REFERENCES audio_submissions(id) ON DELETE CASCADE,
 ADD CONSTRAINT ck_pronunciation_mode CHECK (
   (mode='SCRIPTED' AND drill_id IS NOT NULL
    AND drill_mode IS NOT NULL AND drill_mode IN ('READ_ALOUD','SHADOWING') AND reference_text_snapshot IS NOT NULL)
   OR (mode='UNSCRIPTED' AND drill_id IS NULL
    AND drill_mode IS NULL AND reference_text_snapshot IS NULL AND completeness IS NULL)
 );
