package vn.englishconversation.conversation.dto;

import java.util.List;

public record PracticeResponse(
    String id,
    String status,
    String mode,
    String roleCode,
    String selectedLevel,
    String version,
    String title,
    String learnerRole,
    String aiRole,
    String communicationGoal,
    String contextDescription,
    int learnerTurns,
    int maxLearnerTurns,
    boolean pronunciationAvailable,
    List<MessageResponse> messages,
    List<TurnResponse> turns,
    OperationResponse operation,
    HintResponse hint) {}
