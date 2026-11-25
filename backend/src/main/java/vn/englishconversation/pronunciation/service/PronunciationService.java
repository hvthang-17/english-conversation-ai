package vn.englishconversation.pronunciation.service;

import static vn.englishconversation.pronunciation.dto.PronunciationData.*;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.pronunciation.repository.PronunciationRepository;
import vn.englishconversation.voice.service.WaveAudio;

@Service
@Profile({"g5", "g6"})
public class PronunciationService {
  private final PronunciationRepository store;
  private final PronunciationAudio audio;
  private final PronunciationProvider provider;
  private final TransactionTemplate tx;
  private final byte[] fingerprintKey;
  private final int dailyLimit;
  private final int providerDailyLimit;

  public PronunciationService(PronunciationRepository store, PronunciationAudio audio,
      PronunciationProvider provider, TransactionTemplate tx,
      @Value("${app.pronunciation.fingerprint-key:}") String fingerprintKey,
      @Value("${app.pronunciation.daily-request-limit:6}") int dailyLimit,
      @Value("${app.pronunciation.provider-daily-limit:6}") int providerDailyLimit) {
    this.store = store;
    this.audio = audio;
    this.provider = provider;
    this.tx = tx;
    this.fingerprintKey = fingerprintKey.getBytes(StandardCharsets.UTF_8);
    this.dailyLimit = dailyLimit;
    this.providerDailyLimit = providerDailyLimit;
  }

  public List<Drill> drills() { return store.drills(); }

  public boolean scoringAvailable() {
    return provider.available() && fingerprintKey.length >= 32 && dailyLimit > 0 && providerDailyLimit > 0;
  }

  public Attempt submit(long user, long drillId, String key, MultipartFile file) {
    if (key == null || !key.matches("[a-zA-Z0-9_-]{16,64}"))
      throw new ApiException(400, "INVALID_REQUEST_ID", "Mã lần gửi không hợp lệ.");
    if (fingerprintKey.length < 32)
      throw new ApiException(503, "PRONUNCIATION_DISABLED", "Chấm phát âm chưa khả dụng.");
    var wav = WaveAudio.read(file);
    if (wav.durationMillis() > 30_000)
      throw new ApiException(422, "PRONUNCIATION_SHORT_AUDIO_REQUIRED", "Hãy thu lại trong tối đa 30 giây để chấm.");
    String fingerprint = fingerprint(user, drillId, wav.digest());
    return tx.execute(transaction -> {
      if (!store.lockActiveUser(user))
        throw new ApiException(403, "ACCOUNT_UNAVAILABLE", "Tài khoản không khả dụng.");
      var previous = store.submitted(user, key);
      if (previous.isPresent()) {
        var row = previous.get();
        if (!MessageDigest.isEqual(fingerprint.getBytes(StandardCharsets.US_ASCII),
            row.text("request_fingerprint").getBytes(StandardCharsets.US_ASCII)))
          throw new ApiException(409, "PRONUNCIATION_REQUEST_CONFLICT", "Mã lần gửi đã dùng cho bản ghi khác.");
        return get(user, row.number("id"));
      }
      if (!scoringAvailable())
        throw new ApiException(503, "PRONUNCIATION_DISABLED", "Chấm phát âm chưa khả dụng.");
      var drill = store.activeDrill(drillId).orElseThrow(PronunciationService::missing);
      if (store.busy(user))
        throw new ApiException(409, "PRONUNCIATION_BUSY", "Một bản ghi đang được chấm. Hãy kiểm tra lịch sử.");
      if (!store.reserveDaily(hash("pronunciation:user:" + user), dailyLimit))
        throw new ApiException(429, "PRONUNCIATION_QUOTA", "Bạn đã dùng hết lượt chấm hôm nay.");
      String fileKey;
      try {
        fileKey = audio.save(wav.bytes());
      } catch (IllegalStateException unavailable) {
        throw new ApiException(503, "PRONUNCIATION_STORAGE_UNAVAILABLE", "Chưa lưu được bản ghi để chấm.");
      }
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status == STATUS_ROLLED_BACK) audio.remove(fileKey);
        }
      });
      return get(user, store.stage(user, drill, key, fingerprint, fileKey));
    });
  }

  public Attempt get(long user, long id) {
    return store.find(user, id).orElseThrow(PronunciationService::missing);
  }

  public History history(long user, int page) {
    if (page < 0 || page > 10_000)
      throw new ApiException(400, "INVALID_PAGE", "Trang lịch sử không hợp lệ.");
    return store.history(user, page);
  }

  // Caller holds the account lock when permanently deleting an account.
  public void clearAccount(long user) {
    for (var key : store.accountAudio(user)) {
      if (!audio.remove(key))
        throw new ApiException(503, "PRONUNCIATION_CLEANUP_PENDING", "Chưa dọn được bản ghi. Hãy thử lại.");
    }
    store.clear(user);
  }

  public void clearLearningData(long user) {
    tx.executeWithoutResult(transaction -> {
      if (!store.lockActiveUser(user))
        throw new ApiException(403, "ACCOUNT_UNAVAILABLE", "Tài khoản không khả dụng.");
      clearAccount(user);
    });
  }

  public boolean reserveProviderCall() {
    return store.reserveDaily(hash("pronunciation:azure:global"), providerDailyLimit);
  }

  private String fingerprint(long user, long drill, String digest) {
    return hash("pronunciation:audio:" + user + ":" + drill + ":" + digest);
  }

  private String hash(String input) {
    try {
      var hmac = Mac.getInstance("HmacSHA256");
      hmac.init(new SecretKeySpec(fingerprintKey, "HmacSHA256"));
      return HexFormat.of().formatHex(hmac.doFinal(
          input.getBytes(StandardCharsets.US_ASCII)));
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("HMAC unavailable");
    }
  }

  private static ApiException missing() {
    return new ApiException(404, "PRONUNCIATION_NOT_FOUND", "Không tìm thấy bài hoặc lượt phát âm.");
  }
}
