package lk.bookhive.common.storage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import lk.bookhive.common.exception.FieldValidationException;

/**
 * Checks an uploaded document before it's stored: size, and the real file type read from the file's
 * first bytes ("magic numbers"). The browser's Content-Type and the file name are ignored, because
 * anyone can rename virus.exe to nic.jpg.
 */
public final class UploadCheck {

  /** Same as spring.servlet.multipart.max-file-size; also checked here in case that changes. */
  public static final long MAX_DOCUMENT_BYTES = 5L * 1024 * 1024;

  private static final byte WILDCARD = '?';

  /** File types accepted for documents (photos from a phone, or a scanned PDF). */
  public enum DocumentType {
    JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}),
    // "RIFF" + 4 size bytes + "WEBP"; the size bytes are skipped in matches()
    WEBP("image/webp", "RIFF????WEBP".getBytes()),
    PDF("application/pdf", "%PDF-".getBytes());

    private final String contentType;
    private final byte[] signature;

    DocumentType(String contentType, byte[] signature) {
      this.contentType = contentType;
      this.signature = signature;
    }

    public String contentType() {
      return contentType;
    }

    private boolean matches(byte[] head) {
      if (head.length < signature.length) {
        return false;
      }
      for (int i = 0; i < signature.length; i++) {
        if (signature[i] != WILDCARD && head[i] != signature[i]) {
          return false;
        }
      }
      return true;
    }
  }

  /** A checked upload: its real type, and a stream that still starts at the first byte. */
  public record CheckedUpload(DocumentType type, InputStream content) {}

  private UploadCheck() {}

  /**
   * @param field the request field, for the error shown under the right input
   * @throws FieldValidationException FILE_TOO_LARGE, FILE_EMPTY or FILE_TYPE_NOT_ALLOWED
   */
  public static CheckedUpload document(String field, InputStream content, long sizeBytes)
      throws IOException {
    if (sizeBytes <= 0) {
      throw new FieldValidationException(field, "FILE_EMPTY", "The file is empty.");
    }
    if (sizeBytes > MAX_DOCUMENT_BYTES) {
      throw new FieldValidationException(
          field, "FILE_TOO_LARGE", "The file is too large. The limit is 5 MB.");
    }
    BufferedInputStream buffered = new BufferedInputStream(content);
    buffered.mark(16);
    byte[] head = buffered.readNBytes(12);
    buffered.reset();
    DocumentType type =
        detect(head)
            .orElseThrow(
                () ->
                    new FieldValidationException(
                        field,
                        "FILE_TYPE_NOT_ALLOWED",
                        "Upload a photo (JPG, PNG, WebP) or a PDF."));
    return new CheckedUpload(type, buffered);
  }

  /**
   * A picture (book covers): JPEG or PNG only, because those are what Java can read to make the
   * thumbnails. Same size limit as documents.
   */
  public static CheckedUpload image(String field, InputStream content, long sizeBytes)
      throws IOException {
    CheckedUpload checked = document(field, content, sizeBytes);
    if (checked.type() != DocumentType.JPEG && checked.type() != DocumentType.PNG) {
      throw new FieldValidationException(
          field, "FILE_TYPE_NOT_ALLOWED", "Upload a JPG or PNG picture.");
    }
    return checked;
  }

  static Optional<DocumentType> detect(byte[] head) {
    return Arrays.stream(DocumentType.values()).filter(type -> type.matches(head)).findFirst();
  }
}
