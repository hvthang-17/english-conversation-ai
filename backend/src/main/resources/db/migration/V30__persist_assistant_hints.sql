CREATE TABLE assistant_hints (
 session_id BIGINT NOT NULL,
 message_id BIGINT NOT NULL,
 ai_request_id BIGINT NOT NULL UNIQUE,
 hint_text TEXT NULL,
 PRIMARY KEY(session_id,message_id),
 CONSTRAINT fk_hint_message FOREIGN KEY(session_id,message_id)
   REFERENCES messages(session_id,id) ON DELETE CASCADE,
 CONSTRAINT fk_hint_ai FOREIGN KEY(ai_request_id) REFERENCES ai_requests(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE ai_requests
SET status='FAILED',retryable=FALSE,processing_expires_at=NULL
WHERE task_type='HINT' AND status='UNKNOWN';
