package vn.englishconversation.conversation.dto;

import java.util.List;

public record PracticeResponse(
    String id,
    String status,
    String mode,
    String selectedLevel,
    String version,
    String title,
    String learnerRole,
    String aiRole,
    String communicationGoal,
    String contextDescription,
    int learnerTurns,
    int maxLearnerTurns,
    List<MessageResponse> messages,
    List<TurnResponse> turns,
    OperationResponse operation,
    String hint,
    boolean hintAlreadyConsumed) {}
