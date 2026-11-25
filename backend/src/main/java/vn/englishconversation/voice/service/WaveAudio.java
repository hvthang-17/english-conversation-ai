package vn.englishconversation.voice.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.web.multipart.MultipartFile;
import vn.englishconversation.common.exception.ApiException;

public record WaveAudio(byte[] bytes, int durationMillis, String digest) {
  public static final String MIME = "audio/wav";
  private static final int SAMPLE_RATE = 16000;
  private static final int HEADER = 44;
  private static final int MAX_BYTES = HEADER + SAMPLE_RATE * 2 * 60;

  public static WaveAudio read(MultipartFile file) {
    if (!MIME.equalsIgnoreCase(file.getContentType()) || file.getSize() <= HEADER)
      throw invalid();
    if (file.getSize() > MAX_BYTES)
      throw new ApiException(413, "AUDIO_TOO_LARGE", "Bản ghi tối đa 60 giây.");
    try {
      byte[] bytes = file.getBytes();
      int data = bytes.length - HEADER;
      if (data <= 0 || data % 2 != 0 || bytes.length > MAX_BYTES) throw invalid();
      ByteBuffer pcm = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      if (pcm.getInt(0) != 0x46464952 || pcm.getInt(4) != bytes.length - 8
          || pcm.getInt(8) != 0x45564157 || pcm.getInt(12) != 0x20746d66
          || pcm.getInt(16) != 16 || pcm.getShort(20) != 1 || pcm.getShort(22) != 1
          || pcm.getInt(24) != SAMPLE_RATE || pcm.getInt(28) != SAMPLE_RATE * 2
          || pcm.getShort(32) != 2 || pcm.getShort(34) != 16
          || pcm.getInt(36) != 0x61746164 || pcm.getInt(40) != data) throw invalid();
      for (int offset = HEADER; offset < bytes.length; offset += 2) {
        if (pcm.getShort(offset) != 0) {
          var digest = MessageDigest.getInstance("SHA-256").digest(bytes);
          return new WaveAudio(bytes, Math.ceilDiv(data, 32), HexFormat.of().formatHex(digest));
        }
      }
      throw invalid();
    } catch (IOException failure) {
      throw invalid();
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static ApiException invalid() {
    return new ApiException(400, "INVALID_AUDIO", "Bản ghi trống hoặc sai định dạng WAV PCM.");
  }
}
