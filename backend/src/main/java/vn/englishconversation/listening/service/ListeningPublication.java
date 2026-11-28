package vn.englishconversation.listening.service;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.listening.repository.ListeningRepository.*;
import vn.englishconversation.listening.dto.ListeningData.DraftExercise;
import vn.englishconversation.listening.dto.ListeningData.Question;
import vn.englishconversation.listening.dto.ListeningData.Segment;
import vn.englishconversation.listening.repository.ListeningRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.LISTENING)
public class ListeningPublication {
  private static final TypeReference<List<Question>> QUESTIONS = new TypeReference<>() {};
  private static final TypeReference<List<Segment>> SEGMENTS = new TypeReference<>() {};
  private static final int MAX_BYTES = 2 * 1024 * 1024;
  private final Path directory;
  private final ListeningRepository store;
  private final AzureListeningSpeech speech;
  private final TransactionTemplate transactions;
  private final ObjectMapper json;
  private final AccountService accounts;

  public ListeningPublication(@Value("${app.listening.audio-directory:.runtime/listening}") String directory,
      ListeningRepository store, AzureListeningSpeech speech, TransactionTemplate transactions, ObjectMapper json, AccountService accounts) {
    this.directory = Path.of(directory).toAbsolutePath().normalize();
    this.store = store;
    this.speech = speech;
    this.transactions = transactions;
    this.json = json;
    this.accounts = accounts;
  }

  public List<DraftExercise> versions() {
    return store.versions().stream().map(ListeningPublication::summary).toList();
  }

  public DraftExercise publish(AccountPrincipal actor, long id) {
    transactions.executeWithoutResult(status -> accounts.requireAdminForWrite(actor));
    try {
      Files.createDirectories(directory);
      try (var channel = FileChannel.open(directory.resolve(id + ".lock"), CREATE, WRITE);
           var lock = channel.tryLock()) {
        if (lock == null) throw busy();
        var version = store.exercise(id).orElseThrow(ListeningPublication::missing);
        String ssml = publicationScript(version);
        var output = file(id, ssml);
        if ("ACTIVE".equals(version.status()) && validFile(output)) return summary(version);
        if (!"DRAFT".equals(version.status()))
          throw new ApiException(409, "LISTENING_VERSION_CLOSED", "Hãy khôi phục tệp cũ hoặc chuẩn bị phiên bản đề mới.");
        requirePublicLesson(version.lessonId(), false);
        boolean generated = !validFile(output);
        if (generated) {
          byte[] audio = speech.synthesize(ssml);
          if (!validMp3(audio)) throw new ApiException(502, "LISTENING_INVALID_AUDIO", "Audio không đúng định dạng MP3 đã chọn.");
          var temporary = Files.createTempFile(directory, id + "-", ".part");
          try {
            Files.write(temporary, audio);
            Files.move(temporary, output, ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          } finally {
            Files.deleteIfExists(temporary);
          }
        }
        try {
          return transactions.execute(status -> {
            accounts.requireAdminForWrite(actor);
            store.lockLesson(version.lessonId());
            requirePublicLesson(version.lessonId(), true);
            var current = store.lockExercise(id).orElseThrow(ListeningPublication::missing);
            if (!"DRAFT".equals(current.status()) || current.lessonId() != version.lessonId()
                || !java.util.Objects.equals(current.updatedAt(), version.updatedAt())
                || !ssml.equals(publicationScript(current))
                || !version.questionsJson().equals(current.questionsJson()))
              throw new ApiException(409, "LISTENING_VERSION_CHANGED", "Phiên bản đề đã thay đổi trong lúc tạo audio.");
            store.activate(current.lessonId(), id);
            return summary(store.exercise(id).orElseThrow(ListeningPublication::missing));
          });
        } catch (RuntimeException failure) {
          if (generated && failure instanceof ApiException) {
            try {
              Files.deleteIfExists(output);
            } catch (IOException cleanup) {
              failure.addSuppressed(cleanup);
            }
          }
          throw failure;
        }
      }
    } catch (OverlappingFileLockException conflict) {
      throw busy();
    } catch (IOException failure) {
      throw audioUnavailable();
    }
  }

  private void requirePublicLesson(long lessonId, boolean lock) {
    if (!store.publicLesson(lessonId, lock))
      throw new ApiException(422, "LISTENING_LESSON_HIDDEN", "Cần công bố bài học và chủ đề trước.");
  }

  public Resource audio(long id) {
    var version = store.exercise(id).orElseThrow(ListeningPublication::missing);
    var file = file(id, synthesisScript(version));
    if (!Files.isRegularFile(file) || !Files.isReadable(file)) throw audioUnavailable();
    return new FileSystemResource(file);
  }

  private String publicationScript(ExerciseState version) {
    try {
      String segmentsJson = version.scriptSegmentsJson();
      List<Segment> segments = segmentsJson == null ? null : json.readValue(segmentsJson, SEGMENTS);
      String transcript = version.transcriptEn();
      String voice = version.ttsVoiceKey();
      ListeningRules.validate(version.title(), version.instructions(), voice, transcript, segments,
          json.readValue(version.questionsJson(), QUESTIONS));
      return ListeningRules.ssml(voice, transcript, segments);
    } catch (RuntimeException invalid) {
      throw new ApiException(422, "LISTENING_INVALID_EXERCISE", "Kịch bản, mã giọng hoặc bộ đáp án không hợp lệ.");
    }
  }

  private String synthesisScript(ExerciseState version) {
    String segmentsJson = version.scriptSegmentsJson();
    return ListeningRules.ssml(version.ttsVoiceKey(), version.transcriptEn(),
        segmentsJson == null ? null : json.readValue(segmentsJson, SEGMENTS));
  }

  private Path file(long id, String ssml) {
    try {
      var sha = MessageDigest.getInstance("SHA-256").digest(ssml.getBytes(StandardCharsets.UTF_8));
      return directory.resolve(id + "-" + HexFormat.of().formatHex(sha) + ".mp3");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static boolean validFile(Path file) throws IOException {
    return Files.isRegularFile(file) && Files.size(file) <= MAX_BYTES && validMp3(Files.readAllBytes(file));
  }

  static boolean validMp3(byte[] bytes) {
    if (bytes.length < 288 || bytes.length > MAX_BYTES) return false;
    int offset = 0;
    if (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') {
      for (int index = 6; index < 10; index++) offset = (offset << 7) | (bytes[index] & 0x7f);
      offset += (bytes[5] & 0x10) == 0 ? 10 : 20;
    }
    // MPEG-2 Layer III, 48 kbps, 24 kHz, mono. At least two consecutive frames.
    if (!expectedFrame(bytes, offset)) return false;
    int next = offset + 144 + ((bytes[offset + 2] >> 1) & 1);
    return expectedFrame(bytes, next);
  }

  private static boolean expectedFrame(byte[] bytes, int offset) {
    return offset >= 0 && offset + 144 <= bytes.length && (bytes[offset] & 0xff) == 0xff
        && (bytes[offset + 1] & 0xfe) == 0xf2 && (bytes[offset + 2] & 0xfc) == 0x64
        && (bytes[offset + 3] & 0xc0) == 0xc0;
  }

  private static DraftExercise summary(ExerciseState row) {
    return new DraftExercise(Long.toString(row.id()), Long.toString(row.lessonId()), row.title(), row.level(), row.status());
  }
  private static ApiException missing() {
    return new ApiException(404, "LISTENING_EXERCISE_NOT_FOUND", "Không tìm thấy phiên bản đề.");
  }
  private static ApiException busy() {
    return new ApiException(409, "LISTENING_PUBLISH_BUSY", "Đề đang được chuẩn bị. Hãy thử lại sau.");
  }
  private static ApiException audioUnavailable() {
    return new ApiException(503, "LISTENING_AUDIO_UNAVAILABLE", "Tệp nghe chưa sẵn sàng. Hãy thử lại sau.");
  }
}
