package vn.englishconversation.conversation.service;

import java.util.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import vn.englishconversation.ai.provider.TextProvider.*;
import vn.englishconversation.ai.service.AiRequestCoordinator.Work;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.conversation.dto.*;
import vn.englishconversation.conversation.repository.AssistantHelpRepository;
import vn.englishconversation.conversation.repository.PracticeRepository.Session;
import vn.englishconversation.feedback.dto.*;
import vn.englishconversation.scenario.service.CatalogService;

import vn.englishconversation.conversation.service.PracticeSessionService;

@Service
@FeatureEnabled(FeatureEnabled.Feature.CONVERSATION)
public class HintService {
  private final PracticeSessionService sessions;
  private final AssistantHelpRepository help;
  public HintService(PracticeSessionService sessions, AssistantHelpRepository help) {
    this.sessions = sessions;
    this.help = help;
  }

  public HintResponse hint(AccountPrincipal owner, long id, HintRequest input) {
    long message = CatalogService.id(input.messageId());
    String fingerprint = sessions.prepareFingerprint("hint|" + message);
    Work work = sessions.reserve(() -> {
      sessions.lockAccount(owner);
      Session session = sessions.lock(owner, id);
      sessions.assistantSource(id, message);
      sessions.expire(id);
      var prior = help.priorHint(id, id + ":hint:" + input.clientRequestId());
      if (prior.isPresent()) {
        if (!input.messageId().equals(Objects.toString(prior.get().messageId(), null))) throw sessions.reused();
        return Work.replay(id, prior.get().id());
      }
      var existing = help.hintOperation(id, message);
      if (existing.isPresent()) return Work.replay(id, existing.get().aiRequestId());
      sessions.requireLatestAssistant(session, id, message);
      sessions.eligible(session, id);
      sessions.requireProvider();
      long request = sessions.addRequest(owner, id, "HINT", "hint:" + input.clientRequestId(),
          fingerprint != null ? fingerprint : sessions.hash("hint|" + message));
      help.createHint(id, message, request);
      return sessions.claim(owner, id, request, 0);
    });
    if (work.attempt() != 0) sessions.execute(owner, work);
    return getHint(owner, id, message);
  }

  public HintResponse getHint(AccountPrincipal owner, long id, long message) {
    return sessions.inTransaction().execute(ignored -> {
      Session session = sessions.lock(owner, id);
      sessions.assistantSource(id, message);
      sessions.expire(id);
      return sessions.hintView(id, message, "ACTIVE".equals(session.status()));
    });
  }
}
