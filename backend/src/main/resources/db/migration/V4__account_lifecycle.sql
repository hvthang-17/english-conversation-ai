CREATE TABLE account_action_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX ix_account_tokens_owner (user_id, consumed_at),
    CONSTRAINT fk_account_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE account_mail_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action_token_id BIGINT NOT NULL UNIQUE,
    encrypted_payload TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6) NULL,
    claim_token CHAR(36) NULL,
    expires_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    error_code VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX ix_account_mail_pending (status, next_attempt_at),
    CONSTRAINT fk_outbox_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_outbox_token FOREIGN KEY (action_token_id) REFERENCES account_action_tokens(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE account_rate_limits (
    bucket_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    window_start DATETIME(6) NOT NULL,
    last_request_at DATETIME(6) NOT NULL,
    request_count INT NOT NULL,
    INDEX ix_account_limits_window (window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE practice_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    local_time TIME NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_schedule_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_schedule_clock CHECK (local_time BETWEEN '00:00:00' AND '23:59:59')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE practice_schedule_days (
    schedule_id BIGINT NOT NULL,
    day_of_week INT NOT NULL,
    PRIMARY KEY (schedule_id, day_of_week),
    CONSTRAINT fk_day_schedule FOREIGN KEY (schedule_id) REFERENCES practice_schedules(id) ON DELETE CASCADE,
    CONSTRAINT ck_schedule_day CHECK (day_of_week BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE practice_reminder_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    schedule_version BIGINT NOT NULL,
    scheduled_for DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6) NULL,
    claim_token CHAR(36) NULL,
    sent_at DATETIME(6) NULL,
    error_code VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE (schedule_id, scheduled_for),
    INDEX ix_reminder_pending (status, next_attempt_at),
    CONSTRAINT fk_delivery_schedule FOREIGN KEY (schedule_id) REFERENCES practice_schedules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE account_deletion_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_user_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    completed_at DATETIME(6) NULL,
    error_code VARCHAR(50) NULL,
    INDEX ix_account_deletion_pending (completed_at, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
