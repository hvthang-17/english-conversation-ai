package vn.englishconversation.user.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.*;
import vn.englishconversation.user.entity.UserAccount;

@Profile("!bootstrap")
public interface UserRepository extends JpaRepository<UserAccount, Long> {
  Optional<UserAccount> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.email = :email")
  Optional<UserAccount> lockByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.id = :id")
  Optional<UserAccount> lockById(Long id);

  boolean existsByRole(String role);
}
