ALTER TABLE admin_audit_logs
  DROP CHECK ck_audit_target,
  DROP CHECK ck_audit_action,
  ADD CONSTRAINT ck_audit_target CHECK(target_type IN ('USER','TOPIC','SCENARIO','SUPPORT_TICKET','ANNOUNCEMENT')),
  ADD CONSTRAINT ck_audit_action CHECK(action IN (
    'USER_ACCESS_UPDATED','TOPIC_CREATED','TOPIC_UPDATED','SCENARIO_CREATED','SCENARIO_UPDATED',
    'SUPPORT_ASSIGNED','SUPPORT_REPLIED','SUPPORT_STATUS_UPDATED',
    'ANNOUNCEMENT_CREATED','ANNOUNCEMENT_UPDATED','ANNOUNCEMENT_PUBLISHED','ANNOUNCEMENT_ARCHIVED'
  ));

ALTER TABLE announcements
  ADD COLUMN client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN request_fingerprint CHAR(64) CHARACTER SET ascii NULL,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
  ADD UNIQUE KEY uq_announcement_request (created_by,client_request_id);

ALTER TABLE ai_attempts ADD INDEX ix_ai_attempt_started (started_at,id);
