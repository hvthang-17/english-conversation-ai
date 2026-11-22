CREATE TABLE audio_submissions (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 session_id BIGINT NULL,
 client_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 ai_request_id BIGINT NOT NULL,
 storage_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 mime_type VARCHAR(32) NOT NULL,
 size_bytes INT NOT NULL,
 duration_ms INT NOT NULL,
 draft_transcript TEXT NULL,
 expires_at DATETIME(6) NOT NULL,
 transcript_expires_at DATETIME(6) NULL,
 confirmed_at DATETIME(6) NULL,
 audio_deleted_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_audio_session FOREIGN KEY(session_id) REFERENCES practice_sessions(id) ON DELETE RESTRICT,
 CONSTRAINT fk_audio_ai FOREIGN KEY(ai_request_id) REFERENCES ai_requests(id) ON DELETE RESTRICT,
 CONSTRAINT uk_audio_session_id UNIQUE(session_id,id),
 CONSTRAINT uk_audio_client UNIQUE(session_id,client_request_id),
 CONSTRAINT uk_audio_ai UNIQUE(ai_request_id),
 CONSTRAINT uk_audio_file UNIQUE(storage_key),
 CONSTRAINT ck_audio_status CHECK(status IN ('TRANSCRIBING','READY','FAILED','CONFIRMED','EXPIRED','DELETION_PENDING')),
 CONSTRAINT ck_audio_draft CHECK(
  (status='READY' AND draft_transcript IS NOT NULL AND transcript_expires_at IS NOT NULL)
  OR (status<>'READY' AND draft_transcript IS NULL AND transcript_expires_at IS NULL)
 ),
 CONSTRAINT ck_audio_bounds CHECK(duration_ms BETWEEN 1 AND 60000 AND size_bytes BETWEEN 46 AND 1920044),
 INDEX ix_audio_raw_expiry(expires_at),
 INDEX ix_audio_draft_expiry(transcript_expires_at),
 INDEX ix_audio_status(status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE messages
 DROP CHECK ck_message_input,
 ADD COLUMN audio_submission_id BIGINT NULL,
 ADD CONSTRAINT uk_message_audio UNIQUE(audio_submission_id),
 ADD CONSTRAINT fk_message_audio FOREIGN KEY(session_id,audio_submission_id)
   REFERENCES audio_submissions(session_id,id) ON DELETE RESTRICT,
 ADD CONSTRAINT ck_message_voice CHECK(
   (sender='LEARNER' AND input_type='AUDIO' AND audio_submission_id IS NOT NULL)
   OR (audio_submission_id IS NULL AND (
     (sender='LEARNER' AND input_type='TEXT') OR (sender='ASSISTANT' AND input_type='GENERATED')
   ))
 );
