package lk.bookhive.auth.service;

import java.nio.charset.StandardCharsets;
import lk.bookhive.common.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

/**
 * Rules every new password must follow (register, reset, change). Length 8+ is also checked on the
 * request DTOs; the common-password list comes in checklist step 101.
 */
@Component
public class PasswordPolicy {

  /** BCrypt only looks at the first 72 bytes; longer passwords would be silently cut off. */
  static final int MAX_BYTES = 72;

  public void check(String password) {
    // 72 bytes is 72 English letters but only 24 Sinhala/Tamil letters (3 bytes each in UTF-8)
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new BusinessRuleException("PASSWORD_TOO_LONG", "Please choose a shorter password.");
    }
  }
}
