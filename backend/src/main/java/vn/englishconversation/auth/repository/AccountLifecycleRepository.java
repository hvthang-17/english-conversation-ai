package vn.englishconversation.auth.repository;

import org.springframework.stereotype.Repository;
import vn.englishconversation.config.features.FeatureEnabled;
import vn.englishconversation.common.repository.JdbcStore;

/** Persistence only: caller retains transaction, lock ordering, claim/version and cleanup policy. */
@Repository
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AccountLifecycleRepository {
  private final JdbcStore db;
  public AccountLifecycleRepository(JdbcStore db) { this.db = db; }
  public record TokenOwner(long userId) {}
  public record TokenEmail(String email) {}
  public void consumeActiveTokens(long owner) {
    db.update("UPDATE account_action_tokens SET consumed_at=UTC_TIMESTAMP(6) WHERE user_id=? AND"
            + " consumed_at IS NULL", owner);
  }

  public void cancelPendingMail(long owner) {
    db.update("UPDATE account_mail_outbox SET status='CANCELLED',encrypted_payload=NULL"
            + " WHERE user_id=? AND status<>'SENT'", owner);
  }

  public long createToken(long owner,String email,String hash,java.sql.Timestamp expires) {
    return db.insert("INSERT INTO"
                + " account_action_tokens(user_id,email,token_hash,expires_at,created_at)"
                + " VALUES (?,?,?,?,UTC_TIMESTAMP(6))", owner, email, hash, expires);
  }

  public long queueResetMail(long owner,long token,String payload,java.sql.Timestamp expires) {
    return db.insert("INSERT INTO"
            + " account_mail_outbox(user_id,action_token_id,encrypted_payload,next_attempt_at,expires_at,created_at)"
            + " VALUES (?,?,?,UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6))", owner, token, payload, expires);
  }

  public java.util.Optional<TokenOwner> tokenOwner(String hash) {
    return db.find("SELECT user_id FROM account_action_tokens WHERE token_hash=?", hash).map(row -> new TokenOwner(row.number("user_id")));
  }

  public java.util.Optional<TokenEmail> lockValidToken(String hash) {
    return db.find("SELECT * FROM account_action_tokens WHERE token_hash=? AND"
                          + " consumed_at IS NULL AND expires_at>UTC_TIMESTAMP(6) FOR UPDATE", hash).map(row -> new TokenEmail(row.text("email")));
  }

  public void consumeAllTokens(long owner) {
    db.update("UPDATE account_action_tokens SET consumed_at=UTC_TIMESTAMP(6) WHERE user_id=?", owner);
  }

  public void cancelAllMail(long owner) {
    db.update("UPDATE account_mail_outbox SET status='CANCELLED',encrypted_payload=NULL WHERE"
                  + " user_id=?", owner);
  }

  public void disableSchedule(long owner) {
    db.update("UPDATE practice_schedules SET"
                  + " enabled=FALSE,email_reminder_enabled=FALSE,version=version+1 WHERE user_id=?", owner);
  }

  public void cancelReminders(long owner) {
    db.update("UPDATE practice_reminder_deliveries d JOIN practice_schedules s ON"
                  + " s.id=d.schedule_id SET d.status='CANCELLED' WHERE s.user_id=? AND"
                  + " d.status<>'SENT'", owner);
  }

  public long requestDeletion(long owner) {
    return db.insert("INSERT INTO account_deletion_jobs(subject_user_id,requested_at,next_attempt_at)"
                  + " VALUES (?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", owner);
  }
}
