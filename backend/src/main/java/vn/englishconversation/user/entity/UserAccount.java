package vn.englishconversation.user.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 254)
  public String email;

  @Column(name = "password_hash", nullable = false)
  public String passwordHash;

  @Column(nullable = false, length = 20)
  public String role;

  @Column(nullable = false, length = 20)
  public String status;

  @Column(name = "security_version", nullable = false)
  public long securityVersion;

  public Instant passwordChangedAt;
  public Instant lastLoginAt;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;
}
