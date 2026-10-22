package lk.bookhive.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/verify-email}: the token from the email link. */
public record VerifyEmailRequest(@NotBlank @Size(max = 100) String token) {}
