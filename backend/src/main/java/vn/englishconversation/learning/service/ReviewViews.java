package vn.englishconversation.learning.service;

import vn.englishconversation.learning.dto.NotebookData;
import vn.englishconversation.learning.dto.LearningData;
import vn.englishconversation.review.model.ReviewData;

/** Maps review values to the two established learning HTTP contracts. */
final class ReviewViews {
  private ReviewViews() {}
  static NotebookData.Card notebook(ReviewData.Card c) {
    return new NotebookData.Card(c.id(), c.sourceLanguageItemId(), c.itemType(), c.expression(),
        c.meaningVi(), c.exampleEn(), c.ipa(), NotebookData.Status.valueOf(c.status().name()),
        c.lastReviewedAt(), c.partOfSpeech());
  }
  static LearningData.Card legacy(ReviewData.Card c) {
    return new LearningData.Card(c.id(), c.sourceSuggestionId(), c.itemType(), c.expression(),
        c.meaningVi(), c.exampleEn(), c.status().name(), c.lastReviewedAt(), c.partOfSpeech());
  }
  static NotebookData.Attempt attempt(ReviewData.Attempt a) {
    return new NotebookData.Attempt(a.id(), NotebookData.Mode.valueOf(a.mode().name()),
        NotebookData.Status.valueOf(a.result().name()), a.correct(), a.reviewedAt(),
        a.rating() == null ? null : NotebookData.Rating.valueOf(a.rating().name()), a.algorithmVersion(),
        a.scheduleVersion(), a.intervalDays(), a.nextReviewAt());
  }
  static ReviewData.ReviewInput input(NotebookData.ReviewInput i) {
    return new ReviewData.ReviewInput(i.mode() == null ? null : ReviewData.Mode.valueOf(i.mode().name()),
        i.result() == null ? null : ReviewData.Status.valueOf(i.result().name()), i.clientRequestId(),
        i.optionId(), i.rating() == null ? null : ReviewData.Rating.valueOf(i.rating().name()), i.scheduleVersion());
  }
}
