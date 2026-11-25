CREATE TABLE scenarios (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    code VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    learner_role VARCHAR(200) NOT NULL,
    ai_role VARCHAR(200) NOT NULL,
    communication_goal TEXT NOT NULL,
    context_description TEXT NOT NULL,
    ai_instruction TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_scenario_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_scenario_active_content CHECK (status <> 'ACTIVE' OR (
        CHAR_LENGTH(TRIM(title)) > 0 AND CHAR_LENGTH(TRIM(description)) > 0
        AND CHAR_LENGTH(TRIM(learner_role)) > 0 AND CHAR_LENGTH(TRIM(ai_role)) > 0
        AND CHAR_LENGTH(TRIM(communication_goal)) > 0 AND CHAR_LENGTH(TRIM(context_description)) > 0
        AND CHAR_LENGTH(TRIM(ai_instruction)) > 0)),
    CONSTRAINT fk_scenario_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
    CONSTRAINT fk_scenario_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_scenario_editor FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE scenario_levels (
    scenario_id BIGINT NOT NULL,
    level VARCHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (scenario_id, level),
    INDEX ix_scenario_levels_level (level, scenario_id),
    CONSTRAINT ck_scenario_level CHECK (level IN ('A2', 'B1')),
    CONSTRAINT fk_level_scenario FOREIGN KEY (scenario_id) REFERENCES scenarios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE scenario_role_variants (
    scenario_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    learner_role VARCHAR(200) NOT NULL,
    ai_role VARCHAR(200) NOT NULL,
    communication_goal TEXT NOT NULL,
    context_description TEXT NOT NULL,
    ai_instruction TEXT NOT NULL,
    PRIMARY KEY (scenario_id, code),
    CONSTRAINT fk_variant_scenario FOREIGN KEY (scenario_id) REFERENCES scenarios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lessons (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    level VARCHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scenario_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL,
    objective_code VARCHAR(64) NOT NULL,
    learning_objective TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX ix_lesson_catalog (status, topic_id, level, id),
    CONSTRAINT ck_lesson_level CHECK (level IN ('A2', 'B1')),
    CONSTRAINT ck_lesson_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_lesson_active_content CHECK (status <> 'ACTIVE' OR (
        CHAR_LENGTH(TRIM(title)) > 0 AND CHAR_LENGTH(TRIM(summary)) > 0
        AND CHAR_LENGTH(TRIM(objective_code)) > 0 AND CHAR_LENGTH(TRIM(learning_objective)) > 0)),
    CONSTRAINT fk_lesson_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
    CONSTRAINT fk_lesson_scenario_level FOREIGN KEY (scenario_id, level)
        REFERENCES scenario_levels(scenario_id, level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lesson_bookmarks (
    user_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    kind VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, lesson_id, kind),
    INDEX ix_bookmarks_recent (user_id, kind, created_at, lesson_id),
    CONSTRAINT ck_bookmark_kind CHECK (kind IN ('FAVORITE', 'SAVE_FOR_LATER')),
    CONSTRAINT fk_bookmark_owner FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_bookmark_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
