CREATE TABLE language_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT NOT NULL,
    kind VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expression VARCHAR(200) NOT NULL,
    ipa VARCHAR(240) NULL,
    meaning_vi TEXT NOT NULL,
    example_en TEXT NULL,
    example_vi TEXT NULL,
    sort_order INT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX ix_language_items_lesson (lesson_id, status, sort_order, id),
    CONSTRAINT fk_language_item_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT ck_language_item_kind CHECK (kind IN ('WORD', 'PHRASE')),
    CONSTRAINT ck_language_item_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_language_item_order CHECK (sort_order > 0),
    CONSTRAINT ck_language_item_content CHECK (status <> 'ACTIVE' OR
        (CHAR_LENGTH(TRIM(expression)) > 0 AND CHAR_LENGTH(TRIM(meaning_vi)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sample_dialogues (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    lines_json JSON NOT NULL,
    sort_order INT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX ix_sample_dialogues_lesson (lesson_id, status, sort_order, id),
    CONSTRAINT fk_sample_dialogue_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT ck_sample_dialogue_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_sample_dialogue_order CHECK (sort_order > 0),
    CONSTRAINT ck_sample_dialogue_content CHECK (status <> 'ACTIVE' OR
        (CHAR_LENGTH(TRIM(title)) > 0 AND JSON_TYPE(lines_json) = 'ARRAY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE review_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_suggestion_id BIGINT NULL,
    source_language_item_id BIGINT NULL,
    item_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expression VARCHAR(200) NOT NULL,
    meaning_vi TEXT NOT NULL,
    example_en TEXT NULL,
    ipa VARCHAR(240) NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NEEDS_REVIEW',
    last_reviewed_at DATETIME(6) NULL,
    next_review_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE (user_id, source_language_item_id),
    UNIQUE (user_id, source_suggestion_id),
    INDEX ix_review_cards_status (user_id, status, created_at, id),
    CONSTRAINT fk_review_card_owner FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_card_language FOREIGN KEY (source_language_item_id)
        REFERENCES language_items(id) ON DELETE SET NULL,
    CONSTRAINT ck_review_card_status CHECK (status IN ('NEEDS_REVIEW', 'REMEMBERED')),
    CONSTRAINT ck_review_card_type CHECK (item_type IN ('VOCABULARY', 'PHRASE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE review_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_item_id BIGINT NOT NULL,
    review_mode VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'FLASHCARD',
    result VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    client_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reviewed_at DATETIME(6) NOT NULL,
    is_correct BOOLEAN NULL,
    UNIQUE (review_item_id, client_request_id),
    INDEX ix_review_attempts_history (review_item_id, reviewed_at, id),
    CONSTRAINT fk_review_attempt_card FOREIGN KEY (review_item_id) REFERENCES review_items(id) ON DELETE CASCADE,
    CONSTRAINT ck_review_attempt_result CHECK (result IN ('NEEDS_REVIEW', 'REMEMBERED')),
    CONSTRAINT ck_review_attempt_mode CHECK ((review_mode = 'FLASHCARD' AND is_correct IS NULL)
        OR (review_mode = 'QUICK_QUIZ' AND is_correct IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
