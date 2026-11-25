package vn.englishconversation.account;

import vn.englishconversation.auth.security.TokenCipher;
import vn.englishconversation.profile.service.AvatarService;
import vn.englishconversation.profile.service.PracticeScheduleService;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vn.englishconversation.common.exception.ApiException;

class AccountRulesTest {
  @Test
  void encryptsWithFreshNonceAndRejectsTampering() {
    var cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    String first = cipher.encrypt("synthetic-one-time-token"),
        second = cipher.encrypt("synthetic-one-time-token");
    assertNotEquals(first, second);
    assertEquals("synthetic-one-time-token", cipher.decrypt(first));
    byte[] bytes = Base64.getDecoder().decode(first);
    bytes[13] ^= 1;
    assertThrows(
        IllegalStateException.class,
        () -> cipher.decrypt(Base64.getEncoder().encodeToString(bytes)));
  }

  @Test
  void skipsDstGapAndUsesFirstOverlapOnly() {
    ZoneId zone = ZoneId.of("America/New_York");
    assertNull(PracticeScheduleService.occurrence(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), zone));
    assertEquals(
        Instant.parse("2026-11-01T05:30:00Z"),
        PracticeScheduleService.occurrence(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), zone));
    assertEquals(
        Instant.parse("2026-11-08T06:30:00Z"),
        PracticeScheduleService.next(
            List.of(7), LocalTime.of(1, 30), zone, Instant.parse("2026-11-01T05:31:00Z")));
  }

  @Test
  void normalizesAvatarAndRejectsFakeAndLargeImages() throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var normalized =
        ImageIO.read(new ByteArrayInputStream(AvatarService.normalize(bytes.toByteArray())));
    assertEquals(
        2,
        ImageIO.read(
                new ByteArrayInputStream(
                    AvatarService.normalize(
                        Base64.getDecoder()
                            .decode(
                                "UklGRjQAAABXRUJQVlA4ICgAAACQAQCdASoCAAIAAUAmJaACdLoAA5gA/vUnX27F28hv/kGPYL43bsAA"))))
            .getWidth());
    assertEquals(512, normalized.getWidth());
    assertEquals(384, normalized.getHeight());
    assertThrows(ApiException.class, () -> AvatarService.normalize("<svg>test</svg>".getBytes()));
    assertThrows(ApiException.class, () -> AvatarService.normalize(new byte[2 * 1024 * 1024 + 1]));
    var huge = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", huge);
    assertThrows(ApiException.class, () -> AvatarService.normalize(huge.toByteArray()));
  }
}
