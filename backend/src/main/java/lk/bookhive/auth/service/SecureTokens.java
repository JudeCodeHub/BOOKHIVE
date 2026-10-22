package lk.bookhive.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random secrets for email links and refresh tokens. The raw value goes to the user (email link,
 * cookie); only {@link #hash} of it is stored, so a stolen database can't be replayed.
 */
public final class SecureTokens {

  private static final SecureRandom RANDOM = new SecureRandom();

  private SecureTokens() {}

  /** 256 random bits, URL-safe (43 characters), e.g. for {@code ?token=...}. */
  public static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * SHA-256, hex (64 characters). A plain fast hash is fine here, unlike for passwords: the token
   * is already 256 random bits, so there's nothing to brute-force.
   */
  public static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available in Java", e);
    }
  }
}
