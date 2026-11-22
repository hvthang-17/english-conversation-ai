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
 CONSTRAINT uk_users_email UNIQUE (email),
 CONSTRAINT ck_users_email CHECK (BINARY email = BINARY LOWER(TRIM(email)) AND NOT REGEXP_LIKE(email, '[[:space:]]')),
 CONSTRAINT ck_users_role CHECK (role IN ('LEARNER','ADMIN')),
 CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
