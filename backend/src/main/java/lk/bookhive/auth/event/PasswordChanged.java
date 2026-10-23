package lk.bookhive.auth.event;

import java.util.UUID;
import lk.bookhive.common.domain.Language;

/**
 * Published after a password was reset or changed. The user gets a "your password was changed"
 * email: if it wasn't them, they know their account is at risk.
 */
public record PasswordChanged(UUID userId, String email, String fullName, Language language) {}
