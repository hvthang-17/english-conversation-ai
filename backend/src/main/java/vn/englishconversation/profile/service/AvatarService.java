package vn.englishconversation.profile.service;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiException;
import vn.englishconversation.profile.repository.AvatarRepository;
import vn.englishconversation.user.repository.UserRepository;

@Service
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class AvatarService {
  private final AvatarRepository persistence;
  public static final String API_PATH = "/api/v1/profile/me/avatar";
  private final Path root;
  private final Path legacyRoot;
  private final UserRepository users;
  private final TransactionTemplate tx;

  public AvatarService(AvatarRepository persistence,
            @Value("${app.account.avatar-directory:}") String directory,
UserRepository users,
      PlatformTransactionManager manager) {
    this.persistence = persistence;
    Path working = Path.of("").toAbsolutePath().normalize();
    Path backend = Files.isRegularFile(working.resolve("backend/pom.xml"))
        ? working.resolve("backend") : working;
    root = directory.isBlank() ? backend.resolve(".runtime/avatars") : Path.of(directory).toAbsolutePath().normalize();
    Path project = backend.getParent();
    legacyRoot = directory.isBlank() && project != null
        && Files.isRegularFile(project.resolve("backend/pom.xml"))
        && Files.isRegularFile(project.resolve("frontend/package.json"))
        ? project.resolve(".runtime/avatars") : null;
    this.users = users;
    tx = new TransactionTemplate(manager);
  }

  private static Path path(Path directory, String key) {
    return directory.resolve(UUID.fromString(key).toString() + ".png");
  }

  public static String url(String key) {
    return key == null ? null : API_PATH + "/" + key;
  }

  private String currentKey(long owner) {
    return persistence.currentKey(owner)
        .map(row -> row.avatarStorageKey())
        .orElse(null);
  }

  public static byte[] normalize(byte[] source) {
    if (source.length == 0 || source.length > 2 * 1024 * 1024)
      throw new ApiException(413, "AVATAR_TOO_LARGE", "Ảnh tối đa 2 MiB.");
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw invalid();
      var reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!Set.of("jpeg", "png", "webp").contains(format))
          throw new ApiException(415, "AVATAR_FORMAT_INVALID", "Chọn ảnh JPEG, PNG hoặc WebP.");
        reader.setInput(stream, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || width > 4096 || height > 4096) throw invalid();
        var image = reader.read(0);
        double scale = Math.min(1, 512.0 / Math.max(width, height));
        var output =
            new BufferedImage(
                Math.max(1, (int) (width * scale)),
                Math.max(1, (int) (height * scale)),
                BufferedImage.TYPE_INT_ARGB);
        var graphics = output.createGraphics();
        try {
          graphics.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          graphics.drawImage(image, 0, 0, output.getWidth(), output.getHeight(), null);
        } finally {
          graphics.dispose();
        }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(output, "png", bytes);
        return bytes.toByteArray();
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException failure) {
      throw invalid();
    }
  }

  public String save(AccountPrincipal owner, MultipartFile file) {
    if (file.getSize() > 2 * 1024 * 1024)
      throw new ApiException(413, "AVATAR_TOO_LARGE", "Ảnh tối đa 2 MiB.");
    String key = UUID.randomUUID().toString();
    try {
      byte[] source = file.getBytes();
      String expected = file.getContentType();
      boolean png =
          source.length >= 8
              && source[0] == (byte) 137
              && source[1] == 80
              && source[2] == 78
              && source[3] == 71;
      boolean jpeg =
          source.length >= 3
              && source[0] == (byte) 255
              && source[1] == (byte) 216
              && source[2] == (byte) 255;
      boolean webp =
          source.length >= 12
              && new String(source, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
              && new String(source, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                  .equals("WEBP");
      if (!(png && "image/png".equals(expected)
          || jpeg && "image/jpeg".equals(expected)
          || webp && "image/webp".equals(expected)))
        throw new ApiException(
            415, "AVATAR_FORMAT_INVALID", "Nội dung ảnh không khớp định dạng JPEG, PNG hoặc WebP.");
      byte[] normalized = normalize(source);
      Files.createDirectories(root);
      Files.write(path(root, key), normalized, StandardOpenOption.CREATE_NEW);
      try {
        String previous =
            tx.execute(
                s -> {
                  require(owner);
                  String prior = currentKey(owner.id());
                  if (persistence.profileCount(owner.id())
                      == 0)
                    throw new ApiException(
                        404, "PROFILE_NOT_FOUND", "Hãy lưu hồ sơ trước khi thêm ảnh.");
                  persistence.setKey(key, owner.id());
                  return prior;
                });
        remove(previous);
        return url(key);
      } catch (RuntimeException failure) {
        remove(key);
        throw failure;
      }
    } catch (IOException failure) {
      remove(key);
      throw new ApiException(503, "AVATAR_STORAGE_UNAVAILABLE", "Chưa thể lưu ảnh. Hãy thử lại.");
    }
  }

  public byte[] read(long owner) {
    return read(owner, null);
  }

  public byte[] read(long owner, String requestedKey) {
    String key = currentKey(owner);
    if (key == null || requestedKey != null && !key.equals(requestedKey))
      throw new ApiException(404, "AVATAR_NOT_FOUND", "Không tìm thấy ảnh đại diện.");
    try {
      Path file = path(root, key);
      if (legacyRoot != null && !Files.isRegularFile(file)) file = path(legacyRoot, key);
      return Files.readAllBytes(file);
    } catch (IOException | IllegalArgumentException e) {
      throw new ApiException(404, "AVATAR_NOT_FOUND", "Không tìm thấy ảnh đại diện.");
    }
  }

  public void delete(AccountPrincipal owner) {
    String previous =
        tx.execute(
            s -> {
              require(owner);
              String prior = currentKey(owner.id());
              persistence.clearKey(owner.id());
              return prior;
            });
    remove(previous);
  }

  private void require(AccountPrincipal owner) {
    if (!users.lockById(owner.id()).map(u -> AccountService.valid(u, owner)).orElse(false))
      throw new ApiException(401, "SESSION_REVOKED", "Vui lòng đăng nhập lại.");
  }

  public boolean remove(String key) {
    if (key == null) return true;
    try {
      Files.deleteIfExists(path(root, key));
      if (legacyRoot != null) Files.deleteIfExists(path(legacyRoot, key));
      return true;
    } catch (IOException failure) {
      return false;
    }
  }

  @Scheduled(fixedDelayString = "${app.account.cleanup-ms:300000}")
  public void cleanup() {
    if (!Files.isDirectory(root)) return;
    try (var files = Files.list(root)) {
      for (var file : files.limit(1000).toList()) {
        String name = file.getFileName().toString();
        if (!name.matches("[a-f0-9-]{36}\\.png")) continue;
        String key = name.substring(0, 36);
        if (Files.getLastModifiedTime(file).toInstant().isBefore(Instant.now().minusSeconds(3600))
            && persistence.referenceCount(key)
                == 0) remove(key);
      }
    } catch (IOException ignored) {
      /* Retry at the next sweep; never log personal file data. */
    }
  }

  private static ApiException invalid() {
    return new ApiException(
        400, "AVATAR_INVALID", "Ảnh không hợp lệ hoặc vượt quá 4096 × 4096 pixel.");
  }
}
