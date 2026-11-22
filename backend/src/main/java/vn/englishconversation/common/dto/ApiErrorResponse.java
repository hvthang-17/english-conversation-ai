package vn.englishconversation.common.dto;

import java.util.Map;

public record ApiErrorResponse(
    String code, String message, Map<String, String> fieldErrors, String requestId) {}
