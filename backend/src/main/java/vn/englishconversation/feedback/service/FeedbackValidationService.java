package vn.englishconversation.feedback.service;

import java.util.List;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.englishconversation.ai.provider.TextProvider.Prompt;
import vn.englishconversation.conversation.dto.MessageResponse;
import vn.englishconversation.feedback.dto.FeedbackResult;
import vn.englishconversation.feedback.repository.FeedbackStore;

/** Validates provider output and vocabulary evidence before the completion transaction. */
@Service
@FeatureEnabled(FeatureEnabled.Feature.FEEDBACK)
public class FeedbackValidationService {
  private final FeedbackAnalysis analysis;
  private final FeedbackStore feedback;

  public FeedbackValidationService(FeedbackAnalysis analysis, FeedbackStore feedback) {
    this.analysis = analysis;
    this.feedback = feedback;
  }

  public FeedbackResult validate(String output, List<MessageResponse> messages, Prompt prompt) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Feedback analysis must run outside a transaction");
    var result = analysis.validate(output, messages, prompt);
    return FeedbackVocabulary.verify(result, feedback.vocabularyReferences(result.suggestions()));
  }
}
