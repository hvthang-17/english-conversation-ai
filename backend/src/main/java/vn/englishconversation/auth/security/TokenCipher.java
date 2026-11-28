package vn.englishconversation.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import vn.englishconversation.config.features.FeatureEnabled;
import org.springframework.stereotype.Component;

@Component
@FeatureEnabled(FeatureEnabled.Feature.CORE)
public class TokenCipher {
  private final byte[] key;

  public TokenCipher(@Value("${app.mail.outbox-key:}") String encoded) {
    key = encoded.isBlank() ? null : Base64.getDecoder().decode(encoded);
    if (key != null && key.length != 32)
      throw new IllegalArgumentException("Outbox key must have 32 bytes");
  }

  public boolean available() {
    return key != null;
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public String encrypt(String text) {
    if (key == null) throw new IllegalStateException("Outbox encryption not configured");
    byte[] iv = new byte[12];
    new SecureRandom().nextBytes(iv);
    try {
      Cipher c = cipher(Cipher.ENCRYPT_MODE, iv);
      byte[] body = c.doFinal(text.getBytes(StandardCharsets.UTF_8));
      byte[] all = Arrays.copyOf(iv, iv.length + body.length);
      System.arraycopy(body, 0, all, iv.length, body.length);
      return Base64.getEncoder().encodeToString(all);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot encrypt outbox", e);
    }
  }

  public String decrypt(String encoded) {
    try {
      byte[] all = Base64.getDecoder().decode(encoded);
      if (all.length < 28) throw new GeneralSecurityException();
      return new String(
          cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(all, 12))
              .doFinal(Arrays.copyOfRange(all, 12, all.length)),
          StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Cannot decrypt outbox");
    }
  }

  private Cipher cipher(int mode, byte[] iv) throws GeneralSecurityException {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
    return c;
  }
}
