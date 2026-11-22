package vn.englishconversation.profile.service;

import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.profile.dto.*;
import vn.englishconversation.profile.entity.LearnerProfile;
import vn.englishconversation.profile.repository.ProfileRepository;
import vn.englishconversation.scenario.repository.CatalogRepository;
import vn.englishconversation.scenario.service.CatalogService;
import vn.englishconversation.user.repository.UserRepository;

@Service
@Profile("!bootstrap")
public class ProfileService {
  private final ProfileRepository profiles;
  private final CatalogRepository catalog;
  private final UserRepository users;

  public ProfileService(
      ProfileRepository profiles, CatalogRepository catalog, UserRepository users) {
    this.profiles = profiles;
    this.catalog = catalog;
    this.users = users;
  }

  @Transactional
  public void createDefault(long userId) {
    profiles.saveAndFlush(initial(userId));
  }

  private LearnerProfile initial(long id) {
    var p = new LearnerProfile();
    p.userId = id;
    p.displayName = "Người học";
    p.selectedLevel = "A2";
    p.createdAt = p.updatedAt = Instant.now();
    return p;
  }

  @Transactional(readOnly = true)
  public ProfileResponse get(long userId) {
    return response(
        profiles
            .findById(userId)
            .orElseThrow(
                () -> new ApiException(404, "PROFILE_NOT_FOUND", "Bạn chưa thiết lập hồ sơ.")));
  }

  @Transactional
  public ProfileResponse update(long userId, UpdateProfileRequest request) {
    // Serialize full replacements for one owner; never accept a user id from the body.
    users
        .lockById(userId)
        .orElseThrow(() -> new ApiException(401, "AUTHENTICATION_REQUIRED", "Vui lòng đăng nhập."));
    var ids = request.topicIds().stream().map(CatalogService::id).toList();
    if (new HashSet<>(ids).size() != ids.size())
      throw new ApiException(400, "DUPLICATE_TOPICS", "Mỗi chủ đề chỉ được chọn một lần.");
    if (catalog.lockActiveTopics(ids) != ids.size())
      throw new ApiException(
          400, "INVALID_TOPICS", "Chủ đề đã ẩn hoặc không tồn tại. Tải lại danh sách và chọn lại.");
    var p = profiles.findById(userId).orElseGet(() -> initial(userId));
    p.displayName = request.displayName().strip();
    p.selectedLevel = request.selectedLevel();
    p.learningGoal = request.learningGoal() == null ? null : request.learningGoal().strip();
    p.dailyGoalMinutes = request.dailyGoalMinutes();
    p.updatedAt = Instant.now();
    profiles.saveAndFlush(p);
    catalog.replaceInterests(userId, ids);
    return response(p);
  }

  private ProfileResponse response(LearnerProfile p) {
    return new ProfileResponse(
        p.userId.toString(),
        p.displayName,
        p.selectedLevel,
        p.learningGoal,
        p.dailyGoalMinutes,
        catalog.interests(p.userId));
  }
}
