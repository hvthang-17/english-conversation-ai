package vn.englishconversation.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CredentialsRequest(
    @NotBlank @Size(max = 254) String email, @NotNull @Size(max = 128) String password) {}
