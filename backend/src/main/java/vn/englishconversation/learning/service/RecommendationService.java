package vn.englishconversation.learning.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.common.repository.JdbcStore.Row;
import vn.englishconversation.learning.dto.LearningRecommendation;
import vn.englishconversation.learning.dto.DifficultyAdvice;
import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.learning.dto.LearningRecommendation.Focus;
import vn.englishconversation.learning.repository.RecommendationRepository;

@Service
@Profile("g6 | (!bootstrap & !g1 & !g2 & !g3 & !g4 & !g5)")
public class RecommendationService {
  private final RecommendationRepository repository;

  private final int promotion;
  private final int remediation;

  public RecommendationService(RecommendationRepository repository,
      @Value("${app.learning.difficulty.promotion-percent:80}") int promotion,
      @Value("${app.learning.difficulty.remediation-percent:60}") int remediation) {
    if (remediation < 1 || promotion > 100 || remediation >= promotion)
      throw new IllegalArgumentException("Invalid difficulty threshold configuration");
    this.promotion = promotion;
    this.remediation = remediation;
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public DifficultyAdvice difficulty(long owner) {
    var suggestion = repository.difficulty(owner, promotion, remediation).map(row -> {
      String level = row.text("level");
      boolean a2 = "A2".equals(level);
      return new DifficultyAdvice.Suggestion(level, a2 ? "B1" : "A2", 3, a2 ? promotion : remediation);
    });
    return new DifficultyAdvice(suggestion.orElse(null));
  }

  @Transactional(readOnly = true)
  public List<LearningRecommendation> suggest(long owner, Focus focus) {
    var result = new ArrayList<LearningRecommendation>();
    for (var card : repository.cards(owner)) {
      result.add(new LearningRecommendation("REVIEW_ITEM", card.text("id"), card.text("expression"),
          null, null, "REVIEW_NEEDED", "Thẻ này đang ở trạng thái Cần ôn trong sổ tay."));
    }
    for (var lesson : repository.lessons(owner, focus, 12 - result.size())) {
      result.add(recommendLesson(lesson));
    }
    return List.copyOf(result);
  }

  private static LearningRecommendation recommendLesson(Row lesson) {
    String code;
    String reason;
    if (lesson.flag("listening_gap")) {
      code = "SKILL_GAP";
      reason = "Hai lượt gần nhất trên cùng một đề nghe của bài này đều dưới 60%.";
    } else if (lesson.flag("conversation_gap")) {
      code = "SKILL_GAP";
      reason = "Nhận xét hội thoại ghi nhận cùng một lỗi ở nhiều câu trong chủ đề này.";
    } else if (lesson.flag("focus_match")) {
      code = "FOCUS_MATCH";
      reason = "Bài có nội dung phù hợp với trọng tâm bạn chọn.";
    } else if (lesson.flag("interested")) {
      code = "TOPIC_INTEREST";
      reason = "Bài thuộc chủ đề bạn quan tâm.";
    } else {
      code = "LEVEL_MATCH";
      reason = "Bài phù hợp với trình độ " + lesson.text("level") + " bạn đã chọn.";
    }
    return new LearningRecommendation("LESSON", lesson.text("id"), lesson.text("title"),
        lesson.text("level"), lesson.text("topic"), code, reason);
  }
}
