package vn.englishconversation.conversation.service;
import org.springframework.stereotype.Service;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.user.repository.UserRepository;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.conversation.repository.ConversationHistoryRepository;
import vn.englishconversation.learning.dto.LearningData.*;
@Service
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class ConversationHistoryService {
 private final ConversationHistoryRepository repository;
 private final UserRepository users;
 public ConversationHistoryService(ConversationHistoryRepository repository,UserRepository users) { this.repository=repository; this.users=users; }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Page<HistoryEntry> history(AccountPrincipal owner,int page,int size) {
  if(page<0 || page>1000000 || size<1 || size>50) throw new ApiException(400,"INVALID_PAGINATION","Trang hoặc kích thước trang không hợp lệ.");
  return repository.history(owner.id(),page,size);
 }
 @Transactional
 public void delete(AccountPrincipal owner,long id) {
  if(users.lockById(owner.id()).filter(user -> AccountService.valid(user,owner)).isEmpty()) throw new ApiException(401,"SESSION_REVOKED","Vui lòng đăng nhập lại.");
  if(!repository.lockSession(owner.id(),id)) throw new ApiException(404,"PRACTICE_NOT_FOUND","Không tìm thấy buổi luyện.");
  repository.deletePractice(owner.id(),id);
 }
 // Caller already holds the account lock, including during permanent account deletion.
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public void clearLocked(long owner) {
  for(long id:repository.lockAllSessions(owner)) repository.deletePractice(owner,id);
 }
}
