CREATE TABLE admin_audit_logs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_user_id BIGINT NULL,
 action VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 target_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 target_id BIGINT NOT NULL,
 safe_metadata JSON NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_audit_actor FOREIGN KEY(actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
 CONSTRAINT ck_audit_target CHECK(target_type IN ('USER','TOPIC','SCENARIO')),
 CONSTRAINT ck_audit_action CHECK(action IN (
   'USER_ACCESS_UPDATED','TOPIC_CREATED','TOPIC_UPDATED','SCENARIO_CREATED','SCENARIO_UPDATED'
 )),
 INDEX ix_audit_target(target_type,target_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
