package vn.englishconversation.admin.repository;

import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AdminAudit {
  private final JdbcTemplate db;

  public AdminAudit(JdbcTemplate db) {
    this.db = db;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void account(long actor, long target, String previousRole, String previousStatus,
      String role, String status) {
    db.update("""
        INSERT INTO admin_audit_logs(actor_user_id,action,target_type,target_id,safe_metadata,created_at)
        VALUES (?,'USER_ACCESS_UPDATED','USER',?,
          JSON_OBJECT('previousRole',?,'previousStatus',?,'role',?,'status',?),UTC_TIMESTAMP(6))
        """, actor, target, previousRole, previousStatus, role, status);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void catalog(long actor, String targetType, long target, boolean created) {
    record(actor,targetType + (created ? "_CREATED" : "_UPDATED"),targetType,target);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(long actor, String action, String targetType, long target) {
    db.update("""
        INSERT INTO admin_audit_logs(actor_user_id,action,target_type,target_id,created_at)
        VALUES (?,?,?,?,UTC_TIMESTAMP(6))
        """, actor, action, targetType, target);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void catalogDeleted(long actor, String targetType, long target) {
    db.update("""
        INSERT INTO admin_audit_logs(actor_user_id,action,target_type,target_id,safe_metadata,created_at)
        VALUES (?,?,?,?,JSON_OBJECT('operation','DELETE'),UTC_TIMESTAMP(6))
        """, actor, targetType + "_UPDATED", targetType, target);
  }
}
