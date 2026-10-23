package lk.bookhive.auth.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lk.bookhive.common.exception.FieldValidationException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Rules every new password must follow (register, reset, change), as recommended by NIST SP
 * 800-63B: long enough, not a known common password, not built from the user's own email. No "must
 * contain a symbol" rules: they make passwords harder to remember, not harder to guess.
 *
 * <p>Minimum length (8) is also checked on the request DTOs.
 */
@Component
public class PasswordPolicy {

  /** BCrypt only looks at the first 72 bytes; longer passwords would be silently cut off. */
  static final int MAX_BYTES = 72;

  private static final String LIST = "security/common-passwords.txt";

  private static final Pattern TRAILING_DIGITS_AND_SYMBOLS = Pattern.compile("[\\d\\p{Punct}]+$");

  private final Set<String> commonPasswords;

  public PasswordPolicy() {
    this.commonPasswords = loadCommonPasswords();
  }

  /**
   * @param field the request field holding the password ("password" or "newPassword"), so the error
   *     is shown under the right input
   * @param email the account's email: its name part may not be the password
   */
  public void check(String password, String field, String email) {
    // 72 bytes is 72 English letters but only 24 Sinhala/Tamil letters (3 bytes each in UTF-8)
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new FieldValidationException(
          field, "PASSWORD_TOO_LONG", "Please choose a shorter password.");
    }

    String lower = password.toLowerCase(Locale.ROOT);
    // "Password123!" and "monkey2026" are just a common word with digits/symbols added at the end
    String withoutSuffix = TRAILING_DIGITS_AND_SYMBOLS.matcher(lower).replaceFirst("");
    if (commonPasswords.contains(lower) || commonPasswords.contains(withoutSuffix)) {
      throw new FieldValidationException(
          field,
          "PASSWORD_TOO_COMMON",
          "This password is too easy to guess. Please choose a less common one.");
    }

    // e.g. "nimali.perera@mail.lk" -> "nimali.perera" must not appear in the password
    String emailName = email == null ? "" : email.split("@", 2)[0].toLowerCase(Locale.ROOT);
    if (emailName.length() >= 4 && lower.contains(emailName)) {
      throw new FieldValidationException(
          field, "PASSWORD_CONTAINS_EMAIL", "Your password must not contain your email address.");
    }
  }

  private static Set<String> loadCommonPasswords() {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new ClassPathResource(LIST).getInputStream(), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::strip)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .map(line -> line.toLowerCase(Locale.ROOT))
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + LIST, e);
    }
  }
}
