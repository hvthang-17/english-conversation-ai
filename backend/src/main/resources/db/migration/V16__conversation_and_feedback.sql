CREATE TABLE practice_sessions (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL, scenario_id BIGINT NOT NULL,
 client_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 selected_level VARCHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 scenario_snapshot JSON NOT NULL,
 role_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DEFAULT',
 status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 started_at DATETIME(6) NOT NULL, ended_at DATETIME(6) NULL, last_activity_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_practice_owner FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT,
 CONSTRAINT fk_practice_scenario FOREIGN KEY(scenario_id) REFERENCES scenarios(id) ON DELETE RESTRICT,
 CONSTRAINT uk_practice_create UNIQUE(user_id,client_request_id),
 CONSTRAINT ck_practice_level CHECK(selected_level IN ('A2','B1')),
 CONSTRAINT ck_practice_status CHECK(status IN ('ACTIVE','COMPLETED','ABANDONED')),
 INDEX ix_practice_owner_time(user_id,started_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


ALTER TABLE ai_requests
 ADD CONSTRAINT fk_ai_session FOREIGN KEY(session_id) REFERENCES practice_sessions(id) ON DELETE SET NULL,
 ADD COLUMN busy_session_id BIGINT GENERATED ALWAYS AS (
   CASE WHEN task_type <> 'PRONUNCIATION' AND status IN ('PENDING','RUNNING','UNKNOWN') THEN session_id ELSE NULL END
 ) VIRTUAL,
 ADD UNIQUE KEY uk_ai_busy_session (busy_session_id),
 ADD INDEX ix_ai_session (session_id),
 ADD INDEX ix_ai_owner_created (user_id,created_at);
ALTER TABLE ai_attempts ALTER COLUMN provider SET DEFAULT 'GEMINI';


CREATE TABLE conversation_turns (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL,
 turn_no INT NOT NULL, client_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 kind VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 ai_request_id BIGINT NOT NULL,
 processing_expires_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL, completed_at DATETIME(6) NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_turn_session FOREIGN KEY(session_id) REFERENCES practice_sessions(id) ON DELETE RESTRICT,
 CONSTRAINT fk_turn_ai FOREIGN KEY(ai_request_id) REFERENCES ai_requests(id) ON DELETE RESTRICT,
 CONSTRAINT uk_turn_number UNIQUE(session_id,turn_no), CONSTRAINT uk_turn_client UNIQUE(session_id,client_request_id),
 CONSTRAINT uk_turn_session_id UNIQUE(session_id,id), CONSTRAINT uk_turn_ai UNIQUE(ai_request_id),
 CONSTRAINT ck_turn_no CHECK(turn_no BETWEEN 0 AND 20),
 CONSTRAINT ck_turn_kind CHECK(kind IN ('OPENING','LEARNER_TEXT','LEARNER_AUDIO')),
 CONSTRAINT ck_turn_status CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','UNKNOWN','SKIPPED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE messages (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL, turn_id BIGINT NOT NULL,
 sender VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 content TEXT NOT NULL, input_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_message_turn FOREIGN KEY(session_id,turn_id) REFERENCES conversation_turns(session_id,id) ON DELETE RESTRICT,
 CONSTRAINT uk_message_sender UNIQUE(turn_id,sender), CONSTRAINT uk_message_session_id UNIQUE(session_id,id),
 CONSTRAINT ck_message_sender CHECK(sender IN ('LEARNER','ASSISTANT')),
 CONSTRAINT ck_message_input CHECK((sender='LEARNER' AND input_type='TEXT') OR (sender='ASSISTANT' AND input_type='GENERATED')),
 INDEX ix_message_session_created(session_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Audio column/FK and AUDIO input validation are deliberately deferred until audio_submissions exists.

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

ALTER TABLE practice_sessions
 ADD COLUMN mode VARCHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'TEXT',
 ADD COLUMN initial_mode VARCHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'TEXT',
 ADD CONSTRAINT ck_practice_mode CHECK (mode IN ('TEXT', 'VOICE')),
 ADD CONSTRAINT ck_practice_initial_mode CHECK (initial_mode IN ('TEXT', 'VOICE')),
 ADD CONSTRAINT ck_practice_mode_transition CHECK (mode = 'TEXT' OR initial_mode = 'VOICE');

CREATE TABLE session_feedback (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 session_id BIGINT NOT NULL UNIQUE, ai_request_id BIGINT NOT NULL UNIQUE,
 status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 summary TEXT NULL, strengths JSON NULL, goal_assessment TEXT NULL, next_practice TEXT NULL,
 generated_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_feedback_session_id (session_id,id),
 CONSTRAINT fk_feedback_session FOREIGN KEY (session_id) REFERENCES practice_sessions(id),
 CONSTRAINT fk_feedback_request FOREIGN KEY (ai_request_id) REFERENCES ai_requests(id),
 CONSTRAINT ck_feedback_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','UNKNOWN')),
 CONSTRAINT ck_feedback_result CHECK (
   (status='SUCCEEDED' AND summary IS NOT NULL AND strengths IS NOT NULL AND goal_assessment IS NOT NULL
    AND next_practice IS NOT NULL AND generated_at IS NOT NULL)
   OR (status<>'SUCCEEDED' AND summary IS NULL AND strengths IS NULL AND goal_assessment IS NULL
    AND next_practice IS NULL AND generated_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE feedback_items (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 feedback_id BIGINT NOT NULL, session_id BIGINT NOT NULL, source_message_id BIGINT NOT NULL,
 item_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 category VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 rule_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 original_text TEXT NOT NULL, suggested_text TEXT NOT NULL, explanation_vi TEXT NOT NULL,
 sort_order INT NOT NULL,
 UNIQUE KEY uk_feedback_item_order (feedback_id,sort_order),
 CONSTRAINT fk_item_review FOREIGN KEY (session_id,feedback_id) REFERENCES session_feedback(session_id,id),
 CONSTRAINT fk_item_source FOREIGN KEY (session_id,source_message_id) REFERENCES messages(session_id,id),
 CONSTRAINT ck_item_type CHECK(item_type IN ('ERROR','IMPROVEMENT')),
 CONSTRAINT ck_item_category CHECK(category IN ('GRAMMAR','VOCABULARY','EXPRESSION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE feedback_suggestions (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, feedback_id BIGINT NOT NULL,
 item_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 expression VARCHAR(200) NOT NULL, meaning_vi TEXT NOT NULL, example_en TEXT NOT NULL,
 sort_order INT NOT NULL,
 UNIQUE KEY uk_feedback_suggestion_order (feedback_id,sort_order),
 CONSTRAINT fk_suggestion_feedback FOREIGN KEY (feedback_id) REFERENCES session_feedback(id),
 CONSTRAINT ck_suggestion_type CHECK(item_type IN ('VOCABULARY','PHRASE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE assistant_explanations (
 session_id BIGINT NOT NULL,
 message_id BIGINT NOT NULL,
 ai_request_id BIGINT NOT NULL UNIQUE,
 simple_english TEXT NULL,
 meaning_vi TEXT NULL,
 PRIMARY KEY(session_id,message_id),
 CONSTRAINT fk_explanation_message FOREIGN KEY(session_id,message_id)
   REFERENCES messages(session_id,id) ON DELETE CASCADE,
 CONSTRAINT fk_explanation_ai FOREIGN KEY(ai_request_id) REFERENCES ai_requests(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE review_items ADD CONSTRAINT fk_review_source
 FOREIGN KEY(source_suggestion_id) REFERENCES feedback_suggestions(id) ON DELETE SET NULL;