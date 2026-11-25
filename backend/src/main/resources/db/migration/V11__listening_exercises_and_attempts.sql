CREATE TABLE listening_exercises (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    instructions TEXT NOT NULL,
    tts_voice_key VARCHAR(64) NOT NULL,
    transcript_en TEXT NOT NULL,
    translation_vi TEXT NULL,
    script_segments_json JSON NULL,
    transcript_policy VARCHAR(20) NOT NULL DEFAULT 'AFTER_SUBMIT',
    questions_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    sort_order INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX ix_listening_exercise_lesson (lesson_id,status,sort_order,id),
    CONSTRAINT fk_listening_exercise_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT ck_listening_exercise_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    CONSTRAINT ck_listening_exercise_policy CHECK (transcript_policy = 'AFTER_SUBMIT'),
    CONSTRAINT ck_listening_exercise_order CHECK (sort_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE listening_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    exercise_id BIGINT NOT NULL,
    client_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    score INT NULL,
    max_score INT NOT NULL,
    answers_json JSON NOT NULL,
    draft_version INT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NULL,
    UNIQUE (user_id,client_request_id),
    INDEX ix_listening_attempt_active (user_id,exercise_id,status),
    INDEX ix_listening_attempt_history (user_id,started_at,id),
    CONSTRAINT fk_listening_attempt_owner FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_listening_attempt_exercise FOREIGN KEY (exercise_id) REFERENCES listening_exercises(id),
    CONSTRAINT ck_listening_attempt_version CHECK (draft_version >= 0),
    CONSTRAINT ck_listening_attempt_score CHECK (max_score > 0 AND (
        (status='SUBMITTED' AND score IS NOT NULL AND score BETWEEN 0 AND max_score AND submitted_at IS NOT NULL)
        OR (status IN ('IN_PROGRESS','ABANDONED') AND score IS NULL AND submitted_at IS NULL)
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
