package vn.englishconversation.learning.service;

import static vn.englishconversation.learning.dto.NotebookData.*;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Service;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.review.model.ReviewData;
import vn.englishconversation.review.service.ReviewService;

/** Adapter for learning/notebook. ReviewService owns transactions and review rules. */
@Service
@FeatureEnabled(FeatureEnabled.Feature.MATERIALS)
public class NotebookService {
  private final ReviewService reviews;
  public NotebookService(ReviewService reviews) { this.reviews = reviews; }
  public Page<Card> cards(AccountPrincipal owner, Status status, int page, int size) {
    var result = reviews.cards(owner, status == null ? null : ReviewData.Status.valueOf(status.name()), page, size);
    return new Page<>(result.items().stream().map(ReviewViews::notebook).toList(), page, size, result.total());
  }
  public Card detail(AccountPrincipal owner, long id) { return ReviewViews.notebook(reviews.detail(owner, id)); }
  public Card save(AccountPrincipal owner, long sourceId) { return ReviewViews.notebook(reviews.save(owner, sourceId)); }
  public void delete(AccountPrincipal owner, long id) { reviews.delete(owner, id); }
  public void clear(AccountPrincipal owner) { reviews.clear(owner); }
  public Attempt review(AccountPrincipal owner, long id, ReviewInput input) {
    return ReviewViews.attempt(reviews.review(owner, id, ReviewViews.input(input)));
  }
  public ReviewQueue queue(AccountPrincipal owner, int limit) {
    var q = reviews.queue(owner, limit);
    return new ReviewQueue(q.dueCount(), q.newCount(), q.newEligibleCount(), q.newRemainingToday(),
        q.nextDueAt(), q.nextLocalDayAt(), q.timeZone(), q.items().stream()
            .map(c -> new ScheduledCard(ReviewViews.notebook(c.card()), c.nextReviewAt(), c.scheduleVersion())).toList());
  }
}
