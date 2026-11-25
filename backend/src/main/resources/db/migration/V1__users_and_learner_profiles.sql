CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    security_version BIGINT NOT NULL DEFAULT 0,
    password_changed_at DATETIME(6) NULL,
    last_login_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deletion_requested_at DATETIME(6) NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email CHECK (
        BINARY email = BINARY LOWER(TRIM(email)) AND NOT REGEXP_LIKE(email, '[[:space:]]')
    ),
    CONSTRAINT ck_users_role CHECK (role IN ('LEARNER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE learner_profiles (
    user_id BIGINT NOT NULL PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    selected_level VARCHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    learning_goal VARCHAR(1000) NULL,
    daily_goal_minutes SMALLINT NULL,
    avatar_storage_key CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_profile_level CHECK (selected_level IN ('A2', 'B1')),
    CONSTRAINT ck_profile_minutes CHECK (daily_goal_minutes BETWEEN 1 AND 1440)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
