package vn.englishconversation.admin.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateAccountAccessRequest(
    @Pattern(regexp = "LEARNER|ADMIN") String role,
    @Pattern(regexp = "ACTIVE|LOCKED") String status) {}
