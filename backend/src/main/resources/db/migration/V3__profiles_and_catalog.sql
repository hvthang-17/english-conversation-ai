CREATE TABLE learner_profiles (
 user_id BIGINT NOT NULL PRIMARY KEY,
 display_name VARCHAR(100) NOT NULL,
 selected_level VARCHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 learning_goal VARCHAR(1000) NULL,
 daily_goal_minutes SMALLINT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT ck_profile_level CHECK (selected_level IN ('A2','B1')),
 CONSTRAINT ck_profile_minutes CHECK (daily_goal_minutes BETWEEN 1 AND 1440)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE topics (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(150) NOT NULL,
 description TEXT NULL,
 is_active BOOLEAN NOT NULL DEFAULT TRUE,
 sort_order INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_topics_code UNIQUE (code),
 INDEX ix_topics_active_order (is_active, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE user_topic_interests (
 user_id BIGINT NOT NULL,
 topic_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY (user_id, topic_id),
 CONSTRAINT fk_interest_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT fk_interest_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE RESTRICT,
 INDEX ix_interests_topic_user (topic_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE scenarios (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 topic_id BIGINT NOT NULL,
 code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 title VARCHAR(200) NOT NULL,
 description TEXT NOT NULL,
 learner_role VARCHAR(200) NOT NULL,
 ai_role VARCHAR(200) NOT NULL,
 communication_goal TEXT NOT NULL,
 context_description TEXT NOT NULL,
 ai_instruction TEXT NOT NULL,
 status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_by BIGINT NULL,
 updated_by BIGINT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_scenarios_code UNIQUE (code),
 CONSTRAINT fk_scenario_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE RESTRICT,
 CONSTRAINT fk_scenario_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
 CONSTRAINT fk_scenario_updater FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL,
 CONSTRAINT ck_scenario_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
 INDEX ix_scenarios_topic_status (topic_id, status, id),
 INDEX ix_scenarios_status_id (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE scenario_levels (
 scenario_id BIGINT NOT NULL,
 level VARCHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY (scenario_id, level),
 CONSTRAINT fk_level_scenario FOREIGN KEY (scenario_id) REFERENCES scenarios(id) ON DELETE CASCADE,
 CONSTRAINT ck_scenario_level CHECK (level IN ('A2','B1')),
 INDEX ix_scenario_levels_level_id (level, scenario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Preserve existing accounts; admin profiles remain optional.
INSERT INTO learner_profiles (user_id, display_name, selected_level, created_at, updated_at)
SELECT id, 'Người học', 'A2', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM users WHERE role='LEARNER';
