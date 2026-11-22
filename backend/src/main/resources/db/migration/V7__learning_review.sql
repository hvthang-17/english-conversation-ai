CREATE TABLE review_items (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 source_suggestion_id BIGINT NULL,
 item_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 expression VARCHAR(200) NOT NULL,
 meaning_vi TEXT NOT NULL,
 example_en TEXT NULL,
 status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 last_reviewed_at DATETIME(6) NULL,
 next_review_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_review_owner_source (user_id,source_suggestion_id),
 KEY ix_review_owner_status_id (user_id,status,id),
 CONSTRAINT fk_review_owner FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT fk_review_source FOREIGN KEY (source_suggestion_id) REFERENCES feedback_suggestions(id) ON DELETE SET NULL,
 CONSTRAINT ck_review_type CHECK (item_type IN ('VOCABULARY','PHRASE')),
 CONSTRAINT ck_review_status CHECK (status IN ('NEEDS_REVIEW','REMEMBERED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE review_attempts (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 review_item_id BIGINT NOT NULL,
 review_mode VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 client_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reviewed_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_review_attempt_request (review_item_id,client_request_id),
 CONSTRAINT fk_attempt_card FOREIGN KEY (review_item_id) REFERENCES review_items(id) ON DELETE CASCADE,
 CONSTRAINT ck_review_attempt_mode CHECK (review_mode='FLASHCARD'),
 CONSTRAINT ck_review_attempt_result CHECK (result IN ('NEEDS_REVIEW','REMEMBERED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
