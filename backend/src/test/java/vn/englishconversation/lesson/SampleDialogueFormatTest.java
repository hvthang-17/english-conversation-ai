package vn.englishconversation.lesson;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.englishconversation.lesson.dto.LessonMaterials.Line;
import vn.englishconversation.lesson.service.SampleDialogueFormat;

class SampleDialogueFormatTest {
  @Test
  void validatesTurnCountOrderTextAndVoice() {
    var one = new Line(1, "A", "Hello.", "Xin chào.", "en-us-voice-a-v1");
    var two = new Line(2, "B", "Hi.", "Chào bạn.", null);
    var three = new Line(3, "A", "How are you?", "Bạn khỏe không?", null);
    var four = new Line(4, "B", "I'm fine.", "Tôi khỏe.", "en-us-voice-b-v1");
    assertDoesNotThrow(() -> SampleDialogueFormat.validate(List.of(one, two, three, four)));
    assertThrows(IllegalArgumentException.class, () -> SampleDialogueFormat.validate(List.of(one, two, three)));
    assertThrows(IllegalArgumentException.class, () -> SampleDialogueFormat.validate(List.of(one, two, two, four)));
    assertThrows(IllegalArgumentException.class, () -> SampleDialogueFormat.validate(List.of(one, two, three,
        new Line(4, "B", " ", "Chào.", null))));
    assertThrows(IllegalArgumentException.class, () -> SampleDialogueFormat.validate(List.of(one, two, three,
        new Line(4, "B", "Hi.", "Chào.", "remote-voice"))));
  }
}
