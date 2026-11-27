CREATE TABLE support_tickets (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
  kind VARCHAR(24) NOT NULL, title VARCHAR(200) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  assigned_admin_id BIGINT NULL, lesson_id BIGINT NULL, message_id BIGINT NULL, feedback_id BIGINT NULL,
  reason_code VARCHAR(40) NULL, redacted_excerpt TEXT NULL, source_removed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, closed_at DATETIME(6) NULL,
  client_request_id CHAR(36) NOT NULL, request_fingerprint CHAR(64) NOT NULL,
  UNIQUE (user_id,client_request_id), INDEX (user_id,updated_at,id), INDEX (status,updated_at,id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (assigned_admin_id) REFERENCES users(id) ON DELETE SET NULL,
  FOREIGN KEY (lesson_id) REFERENCES lessons(id),
  FOREIGN KEY (message_id) REFERENCES messages(id),
  FOREIGN KEY (feedback_id) REFERENCES session_feedback(id),
  CHECK (status IN ('OPEN','IN_REVIEW','RESOLVED','CLOSED')),
  CHECK ((status='CLOSED') = (closed_at IS NOT NULL)),
  CHECK (
    (kind IN ('TECHNICAL','FEEDBACK') AND lesson_id IS NULL AND message_id IS NULL AND feedback_id IS NULL
      AND redacted_excerpt IS NULL AND source_removed_at IS NULL)
    OR (kind='CONTENT_REPORT' AND (
      (source_removed_at IS NULL AND redacted_excerpt IS NOT NULL
        AND ((lesson_id IS NOT NULL)+(message_id IS NOT NULL)+(feedback_id IS NOT NULL))=1)
      OR (source_removed_at IS NOT NULL AND lesson_id IS NULL AND message_id IS NULL
        AND feedback_id IS NULL AND redacted_excerpt IS NULL)
    ))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_ticket_messages (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, ticket_id BIGINT NOT NULL,
  author_id BIGINT NULL, author_role VARCHAR(16) NOT NULL,
  body TEXT NOT NULL, client_request_id CHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL,
  FOREIGN KEY (ticket_id) REFERENCES support_tickets(id) ON DELETE CASCADE,
  FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE(ticket_id,author_id,client_request_id), INDEX(ticket_id,created_at,id),
  CHECK (author_role IN ('LEARNER','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE announcements (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, created_by BIGINT NULL,
  title VARCHAR(200) NOT NULL, body TEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  audience_role VARCHAR(16) NULL, audience_level VARCHAR(2) NULL,
  created_at DATETIME(6) NOT NULL, published_at DATETIME(6) NULL, expires_at DATETIME(6) NULL,
  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
  CHECK (status<>'PUBLISHED' OR published_at IS NOT NULL),
  CHECK (audience_role IS NULL OR audience_role IN ('LEARNER','ADMIN')),
  CHECK (audience_level IS NULL OR audience_level IN ('A2','B1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE notifications
  ADD COLUMN support_ticket_id BIGINT NULL,
  ADD FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
  ADD FOREIGN KEY (support_ticket_id) REFERENCES support_tickets(id) ON DELETE CASCADE;
