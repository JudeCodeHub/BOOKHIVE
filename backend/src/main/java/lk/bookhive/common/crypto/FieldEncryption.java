package lk.bookhive.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Encrypts sensitive columns such as NIC numbers (PDPA): if the database leaks, the values are
 * unreadable without the key, which lives outside the database (env / AWS Secrets Manager).
 *
 * <ul>
 *   <li><b>AES-256-GCM</b>: encrypts and also detects tampering. A new random 12-byte IV per value,
 *       so the same NIC encrypts differently every time.
 *   <li>Stored text: Base64 of {@code version(1) | iv(12) | ciphertext+tag}. The version byte lets
 *       us rotate keys later.
 *   <li>Because of the random IV, encrypted values can't be searched. {@link #lookupHash} gives a
 *       fixed HMAC-SHA256 of the value for exact-match search ("find member by NIC").
 * </ul>
 *
 * Two separate keys are derived from the one configured key, one per job.
 */
@Component
@EnableConfigurationProperties(CryptoProperties.class)
public class FieldEncryption {

  private static final byte VERSION = 1;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec encryptionKey;
  private final SecretKeySpec hashKey;
  private final SecureRandom random = new SecureRandom();

  public FieldEncryption(CryptoProperties properties) {
    byte[] master = decodeKey(properties.fieldKey());
    this.encryptionKey = new SecretKeySpec(derive(master, "bookhive-field-encryption"), "AES");
    this.hashKey = new SecretKeySpec(derive(master, "bookhive-lookup-hash"), "HmacSHA256");
  }

  public String encrypt(String plain) {
    if (plain == null) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      ByteBuffer out = ByteBuffer.allocate(1 + IV_BYTES + encrypted.length);
      out.put(VERSION).put(iv).put(encrypted);
      return Base64.getEncoder().encodeToString(out.array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  /** Fails loudly on a wrong key or a changed value: tampering is never silently accepted. */
  public String decrypt(String stored) {
    if (stored == null) {
      return null;
    }
    try {
      ByteBuffer in = ByteBuffer.wrap(Base64.getDecoder().decode(stored));
      if (in.get() != VERSION) {
        throw new IllegalStateException("Unknown encryption version");
      }
      byte[] iv = new byte[IV_BYTES];
      in.get(iv);
      byte[] encrypted = new byte[in.remaining()];
      in.get(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Could not decrypt a stored value", e);
    }
  }

  /** Same input, same output (64 hex chars): for exact-match search on encrypted columns. */
  public String lookupHash(String plain) {
    if (plain == null) {
      return null;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(hashKey);
      return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Hashing failed", e);
    }
  }

  private static byte[] decodeKey(String base64) {
    if (base64 == null || base64.isBlank()) {
      throw new IllegalStateException("bookhive.crypto.field-key is not set");
    }
    byte[] key = Base64.getDecoder().decode(base64.strip());
    if (key.length != 32) {
      throw new IllegalStateException("bookhive.crypto.field-key must be 32 bytes (Base64)");
    }
    return key;
  }

  /** HMAC-SHA256(master, purpose): a separate 32-byte key per purpose. */
  private static byte[] derive(byte[] master, String purpose) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(master, "HmacSHA256"));
      return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Key derivation failed", e);
    }
  }
}
