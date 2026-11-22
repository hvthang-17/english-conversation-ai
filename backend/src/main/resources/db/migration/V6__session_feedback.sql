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
