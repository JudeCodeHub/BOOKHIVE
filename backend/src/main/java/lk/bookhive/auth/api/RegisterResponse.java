package lk.bookhive.auth.api;

import java.util.UUID;
import lk.bookhive.auth.domain.UserStatus;

/** Response of {@code POST /api/v1/auth/register}: the new account, still waiting for email. */
public record RegisterResponse(UUID id, String email, UserStatus status) {}
