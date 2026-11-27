CREATE TABLE lesson_completions (
  user_id BIGINT NOT NULL, lesson_id BIGINT NOT NULL, completed_at DATETIME(6) NOT NULL,
  rule_version VARCHAR(64) NOT NULL, PRIMARY KEY(user_id,lesson_id),
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY(lesson_id) REFERENCES lessons(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE study_intervals (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
  client_event_id CHAR(36) NOT NULL, stream_id CHAR(36) NOT NULL,
  started_at DATETIME(6) NOT NULL, ended_at DATETIME(6) NOT NULL, activity_kind VARCHAR(24) NOT NULL,
  UNIQUE(user_id,client_event_id), INDEX(user_id,started_at), INDEX(user_id,stream_id,id),
  CHECK (ended_at>=started_at AND TIMESTAMPDIFF(MICROSECOND,started_at,ended_at)<=30000000),
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_badges (
  user_id BIGINT NOT NULL, badge_code VARCHAR(64) NOT NULL, earned_at DATETIME(6) NOT NULL,
  PRIMARY KEY(user_id,badge_code), FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notifications (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, announcement_id BIGINT NULL,
  kind VARCHAR(24) NOT NULL, dedupe_key VARCHAR(128) NOT NULL, title VARCHAR(200) NOT NULL,
  body VARCHAR(1000) NOT NULL, created_at DATETIME(6) NOT NULL, read_at DATETIME(6) NULL,
  UNIQUE(user_id,dedupe_key), INDEX(user_id,read_at,created_at,id),
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
